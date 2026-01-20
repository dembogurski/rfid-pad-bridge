package com.marijoa.rfid;


/*
    * Puente Java para controlar lector RFID UHF vía USB
    * Basado en la librería rscja-deviceapi
    * Ejecutar asi:  
    * 
    * En Windows:java --enable-native-access=ALL-UNNAMED -Djava.library.path=natives\windows -cp "out;libs\*" com.marijoa.rfid.Bridge inventory
    * 
    * En Linux:  java --enable-native-access=ALL-UNNAMED -Djava.library.path=natives/linux -cp "out:libs/*" com.marijoa.rfid.Bridge inventory
    * 
    * Requisitos: 
    * Usá Java 17 o 21 LTS

        Siempre ejecutar con:  --enable-native-access=ALL-UNNAMED

    * Autor: Ing. Doglas A. Dembogurski Feix
*/




import com.rscja.deviceapi.RFIDWithUHFUsb;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import java.util.*;

// Definición manual de bancos de memoria UHF
class Bank {
    public static final int RESERVED = 0;
    public static final int EPC      = 1;
    public static final int TID      = 2;
    public static final int USER     = 3;
}

public class Bridge {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("""
                Uso:
                  java Bridge inventory
                  java Bridge scan
                  java Bridge read-epc
                  java Bridge write-epc <numero>
                  java Bridge clear [palabras]
                  java Bridge clear-filter
                  java Bridge set-power <nivel>
                  java Bridge get-freq
                  java Bridge set-freq <modo>   (0=China, 1=US/FCC, 8=EU/ETSI)
            """);
            return;
        }

        String cmd = args[0].toLowerCase(Locale.ROOT);
        RFIDWithUHFUsb reader = RFIDWithUHFUsb.getInstance();

        // Rutas nativas según SO
        if (System.getProperty("os.name").toLowerCase().contains("win"))
            reader.setDllOrSOFilePath("natives/windows/UHFAPI.dll", "");
        else
            reader.setDllOrSOFilePath("", "natives/linux/libTagReader.so");

        if (!reader.init("")) {
            System.err.println("No se pudo abrir el lector (init falló).");
            return;
        }

        switch (cmd) {
            case "set-power" -> {
                int pwr = (args.length > 1) ? Integer.parseInt(args[1]) : 30;
                boolean ok = reader.setPower(pwr);
                System.out.println(ok ? "OK power=" + reader.getPower() : "Error al setear potencia");
            }

            case "inventory" -> {
                var epcs = inventoryOnce(reader, 800);
                if (epcs.isEmpty()) System.out.println("NO_TAG");
                else epcs.forEach(System.out::println);
            }

            case "read-epc" -> {
                UHFTAGInfo tag = reader.inventorySingleTag();
                if (tag == null) System.err.println("NO_TAG");
                else System.out.println("EPC=" + tag.getEPC());
            }

            case "write-epc" -> {
                if (args.length < 2) {
                    System.err.println("Uso: write-epc <numero>");
                    break;
                }
                String nuevoEpc = args[1];
                boolean ok = writeEpcSelected(reader, nuevoEpc);
                System.out.println(ok ? "✅ EPC escrito correctamente" : "❌ ERROR al escribir EPC");
            }

            case "scan" -> {
                System.out.println("Escaneando continuamente... (Ctrl+C para salir)");
                Set<String> vistos = new LinkedHashSet<>();
                reader.startInventoryTag();
                try {
                    while (true) {
                        UHFTAGInfo info = reader.readTagFromBuffer();
                        if (info != null && info.getEPC() != null && !info.getEPC().isEmpty()) {
                            String epc = info.getEPC();
                            if (!vistos.contains(epc)) {
                                vistos.add(epc);
                                System.out.println("Detectado EPC: " + epc);
                            }
                            System.out.println(">>> Detectado EPC: " + epc);
                        }
                        Thread.sleep(250);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    reader.stopInventory();
                }
            }

            case "set-freq" -> {
                int mode = (args.length > 1) ? Integer.parseInt(args[1]) : 1;
                boolean ok = reader.setFrequencyMode((byte) mode);
                System.out.println(ok ? "OK frecuencia modo=" + mode : "ERROR al cambiar frecuencia");
            }

            case "get-freq" -> {
                int freq = reader.getFrequencyMode();
                System.out.println("Frecuencia actual: modo=" + freq);
            }

            case "clear" -> {
                int words = (args.length > 1) ? Integer.parseInt(args[1]) : 32;
                var epcs = inventoryOnce(reader, 800);
                if (epcs.isEmpty()) {
                    System.err.println("NO_TAG");
                    break;
                }
                String epc = epcs.iterator().next();
                boolean ok = clearUserFiltered(reader, epc, words);
                System.out.println(ok ? "OK limpiado USER" : "ERROR al limpiar USER");
            }
            case "clear-filter" -> {
                boolean ok = reader.setFilter(1, 32, 0, "");
                System.out.println(ok ? "Filtro limpiado correctamente" : "Error al limpiar filtro");
            }

            default -> System.err.println("Comando desconocido: " + cmd);
        }

        reader.free();
    }

    /** Lee EPCs detectados una vez */
    private static Set<String> inventoryOnce(RFIDWithUHFUsb r, int timeoutMs) {
        Set<String> epcs = new LinkedHashSet<>();
        long t0 = System.currentTimeMillis();
        r.startInventoryTag();
        while (System.currentTimeMillis() - t0 < timeoutMs) {
            UHFTAGInfo info = r.readTagFromBuffer();
            if (info != null && info.getEPC() != null && !info.getEPC().isEmpty())
                epcs.add(info.getEPC());
        }
        r.stopInventory();
        return epcs;
    }

    /** Limpia el banco USER */
    private static boolean clearUserFiltered(RFIDWithUHFUsb r, String epcHex, int wordCnt) {
        if (wordCnt <= 0) wordCnt = 32;
        StringBuilder zeros = new StringBuilder(wordCnt * 4);
        for (int i = 0; i < wordCnt; i++) zeros.append("0000");
        return r.writeData("00000000", Bank.USER, 0, wordCnt, zeros.toString(),
                Bank.EPC, 32, epcHex.length() * 4, epcHex);
    }


    /** Nueva función: regraba el EPC del tag actualmente detectado */
    private static boolean writeEpcSelected(RFIDWithUHFUsb r, String newEpcDigits) {
        // 1️⃣ Detectar el tag activo
        UHFTAGInfo tag = r.inventorySingleTag();
        if (tag == null) {
            System.err.println("⚠️ No se encontró ningún tag.");
            return false;
        }
        String currentEpc = tag.getEPC();
        System.out.println("Tag detectado: " + currentEpc);

        // 2️⃣ Filtro activo por EPC actual
        boolean filterOk = r.setFilter(1, 32, currentEpc.length() * 4, currentEpc);
        if (!filterOk) {
            System.err.println("No se pudo aplicar filtro EPC actual.");
            return false;
        }

        // 3️⃣ Preparar nuevo EPC (BCD)
        String digits = newEpcDigits.replaceAll("\\D", "");
        if (digits.length() % 2 != 0) digits = "0" + digits;

        var bcd = new StringBuilder();
        for (int i = 0; i < digits.length(); i += 2) {
            int hi = Character.digit(digits.charAt(i), 10);
            int lo = Character.digit(digits.charAt(i + 1), 10);
            bcd.append(String.format("%02X", (hi << 4) | lo));
        }

        int epcLenBytes = 12;
        while (bcd.length() < epcLenBytes * 2)
            bcd.insert(0, "0");

        if (bcd.length() > epcLenBytes * 2)
        bcd.setLength(epcLenBytes * 2); // recortar si sobra
    

        int epcWords = (int) Math.ceil(epcLenBytes / 2.0);
        int pcVal = (epcWords << 11);
        String pcHex = String.format("%04X", pcVal);

        // 4️⃣ Escribir nuevo EPC (bank 1, desde word 2)
        boolean ok1 = r.writeData("00000000", Bank.EPC, 2, epcWords, bcd.toString());
        boolean ok2 = r.writeData("00000000", Bank.EPC, 1, 1, pcHex);

        r.setFilter(1, 32, 0, "");

        if (!ok1 || !ok2) {
            System.err.println("❌ Error al escribir EPC.");
            return false;
        }

        System.out.println("✅ EPC regrabado exitosamente: " + bcd);
        return true;
    }
}
