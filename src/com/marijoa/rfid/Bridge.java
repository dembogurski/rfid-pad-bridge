package com.marijoa.rfid;

import com.rscja.deviceapi.RFIDWithUHFUsb;
import com.rscja.deviceapi.entity.UHFTAGInfo;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/*
 * Bridge Java RFID UHF → uso como proceso intermedio
 *
 * Convención de salida:
 *   STDOUT → datos estructurados (parseables)
 *   STDERR → logs / debug
 *
 * Formato de salida:
 *   DETECTED=<EPC>
 *   WRITTEN=<EPC>
 *   OK
 *   NO_TAG
 *   ERROR=<CODE>
 *
 *   Compilar con:   javac -encoding UTF-8 -cp "libs\*" -d out src\com\marijoa\rfid\Bridge.java

 * Autor: Ing. Doglas A. Dembogurski Feix
 */

class Bank {
    public static final int RESERVED = 0;
    public static final int EPC      = 1;
    public static final int TID      = 2;
    public static final int USER     = 3;
}

public class Bridge {

    // Cambiar a false si querés ver logs por STDERR
    private static final boolean QUIET = true;

    /* =========================
       Helpers de salida
       ========================= */

    private static void out(String msg) {
        System.out.println(msg);
    }

    private static void log(String msg) {
        if (!QUIET) {
            System.err.println(msg);
        }
    }

    static {
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            System.loadLibrary("TagReader");
        }
    }

    /* =========================
       MAIN
       ========================= */

    public static void main(String[] args) {

        if (args.length == 0) {
            out("ERROR=NO_ACTION");
            return;
        }

        String cmd = args[0].toLowerCase(Locale.ROOT);
        RFIDWithUHFUsb reader = RFIDWithUHFUsb.getInstance();

        // Librería nativa según SO
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            reader.setDllOrSOFilePath("natives/windows/UHFAPI.dll", "");
        }  

        if (!reader.init("")) {
            out("ERROR=INIT_FAILED");
            return;
        }
        

        try {

            switch (cmd) {

                /* -------------------------
                INVENTORY
                ------------------------- */
                case "inventory" -> {
                    Set<UHFTAGInfo> tags = inventoryOnce(reader, 800);
                    if (tags.isEmpty()) {
                        out("NO_TAG");
                    } else {
                        for (UHFTAGInfo info : tags) {
                            String epcNorm = normalizeEpc(info);
                            if (epcNorm != null && !epcNorm.isEmpty()) {
                                out("DETECTED=" + epcNorm);
                            }
                        }
                    }
                }

                /* -------------------------
                READ EPC
                ------------------------- */
                case "read-epc" -> {
                    UHFTAGInfo tag = reader.inventorySingleTag();
                    if (tag == null || tag.getEPC() == null || tag.getEPC().isEmpty()) {
                        out("NO_TAG");
                    } else {
                        out("DETECTED=" + normalizeEpc(tag));
                    }
                }

                /* -------------------------
                WRITE EPC
                ------------------------- */
                case "write-epc" -> {
                    if (args.length < 2) {
                        out("ERROR=MISSING_EPC");
                        break;
                    }

                    boolean ok = writeEpcSelected(reader, args[1]);
                    if (ok) {
                        out("OK");
                    } else {
                        out("ERROR=WRITE_FAILED");
                    }
                }

                /* -------------------------
                CLEAR USER MEMORY
                ------------------------- */
                case "clear" -> {

                    UHFTAGInfo tag = reader.inventorySingleTag();
                    if (tag == null || tag.getEPC() == null) {
                        out("NO_TAG");
                        break;
                    }

                    String currentEpc = normalizeEpc(tag);
                    out("DETECTED=" + currentEpc);

                    // Filtro por EPC NORMALIZADO
                    boolean filterOk = reader.setFilter(
                            Bank.EPC,
                            32,
                            currentEpc.length() * 4,
                            currentEpc
                    );

                    if (!filterOk) {
                        out("ERROR=FILTER_FAILED");
                        break;
                    }

                    // EPC limpio (96 bits)
                    String emptyEpc = "000000000000000000000000";

                    int epcWords = 6; // 96 bits / 16 = 6 words
                    int pcVal = (epcWords << 11);
                    String pcHex = String.format("%04X", pcVal);

                    boolean ok1 = reader.writeData(
                            "00000000",
                            Bank.EPC,
                            2,
                            epcWords,
                            emptyEpc
                    );

                    boolean ok2 = reader.writeData(
                            "00000000",
                            Bank.EPC,
                            1,
                            1,
                            pcHex
                    );

                    reader.setFilter(1, 32, 0, "");

                    if (ok1 && ok2) {
                        out("WRITTEN=" + emptyEpc);
                        out("OK");
                    } else {
                        out("ERROR=CLEAR_FAILED");
                    }
                }

                default -> out("ERROR=UNKNOWN_ACTION");
            }


        } catch (Exception e) {
            log("EXCEPTION: " + e.getMessage());
            out("ERROR=EXCEPTION");
        } finally {
            reader.free();
        }
    }

    private static String normalizeEpc(UHFTAGInfo tag) {
        if (tag == null || tag.getEPC() == null) return null;

        String epc = tag.getEPC();

        // El PC viene como HEX string (ej: "3000")
        String pcHex = tag.getPc();
        if (pcHex == null || pcHex.length() < 4) {
            // fallback defensivo
            return epc.replaceAll("0+$", "");
        }

        // Parsear PC
        int pc = Integer.parseInt(pcHex.substring(0, 4), 16);

        // Bits 11–15 → cantidad de words EPC
        int epcWords = (pc >> 11) & 0x1F;

        // 1 word = 16 bits = 4 hex chars
        int epcHexLen = epcWords * 4;

        if (epc.length() >= epcHexLen && epcHexLen > 0) {
            return epc.substring(0, epcHexLen);
        }

        return epc;
    }



    /* =========================
       INVENTORY ONCE
       ========================= */

    private static Set<UHFTAGInfo> inventoryOnce(RFIDWithUHFUsb r, int timeoutMs) {
        Set<UHFTAGInfo> tags = new LinkedHashSet<>();
        long t0 = System.currentTimeMillis();

        r.startInventoryTag();
        while (System.currentTimeMillis() - t0 < timeoutMs) {
            UHFTAGInfo info = r.readTagFromBuffer();
            if (info != null && info.getEPC() != null && !info.getEPC().isEmpty()) {
                tags.add(info);
            }
        }
        r.stopInventory();

        return tags;
    }


    /* =========================
       CLEAR USER (FILTERED)
       ========================= */

    private static boolean clearUserFiltered(RFIDWithUHFUsb r, String epcHex, int wordCnt) {
        if (wordCnt <= 0) wordCnt = 32;

        StringBuilder zeros = new StringBuilder(wordCnt * 4);
        for (int i = 0; i < wordCnt; i++) zeros.append("0000");

        return r.writeData(
                "00000000",
                Bank.USER,
                0,
                wordCnt,
                zeros.toString(),
                Bank.EPC,
                32,
                epcHex.length() * 4,
                epcHex
        );
    }

    /* =========================
       WRITE EPC (NUMERIC / BCD)
       ========================= */

    private static boolean writeEpcSelected(RFIDWithUHFUsb r, String newEpcDigits) {

        UHFTAGInfo tag = r.inventorySingleTag();
        if (tag == null || tag.getEPC() == null) {
            out("NO_TAG");
            return false;
        }

        String currentEpc = tag.getEPC();
        out("DETECTED=" + currentEpc);

        boolean filterOk = r.setFilter(1, 32, currentEpc.length() * 4, currentEpc);
        if (!filterOk) {
            return false;
        }

        // Solo dígitos (BCD)
        String digits = newEpcDigits.replaceAll("\\D", "");
        if (digits.length() % 2 != 0) digits = "0" + digits;

        StringBuilder bcd = new StringBuilder();
        for (int i = 0; i < digits.length(); i += 2) {
            int hi = Character.digit(digits.charAt(i), 10);
            int lo = Character.digit(digits.charAt(i + 1), 10);
            bcd.append(String.format("%02X", (hi << 4) | lo));
        }

        int epcLenBytes = 12; // 96 bits
        while (bcd.length() < epcLenBytes * 2) {
            bcd.insert(0, "0");
        }
        if (bcd.length() > epcLenBytes * 2) {
            bcd.setLength(epcLenBytes * 2);
        }

        int epcWords = (int) Math.ceil(epcLenBytes / 2.0);
        int pcVal = (epcWords << 11);
        String pcHex = String.format("%04X", pcVal);

        boolean ok1 = r.writeData("00000000", Bank.EPC, 2, epcWords, bcd.toString());
        boolean ok2 = r.writeData("00000000", Bank.EPC, 1, 1, pcHex);

        r.setFilter(1, 32, 0, "");

        if (!ok1 || !ok2) {
            return false;
        }

        out("WRITTEN=" + bcd);
        return true;
    }
}
