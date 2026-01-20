Short, focused instructions to help AI coding agents work productively on this repository.

Repository summary
- Small Java command-line bridge to a UHF RFID reader (single class: `src/com/marijoa/rfid/Bridge.java`).
- Uses vendor ReaderAPI Jar (`libs/ReaderAPI20240822.jar`) plus JNA (`libs/jna-*.jar`) and a native DLL for Windows (`natives/windows/UHFAPI.dll`).
- No build system files (pom.xml/gradle); project is run directly with `javac`/`java` and requires native library availability.

Big-picture architecture & intent
- Purpose: provide simple CLI commands to inventory tags, read/write EPC and clear USER memory using the vendor SDK.
- Single-entrypoint CLI: `Bridge.main(String[] args)` parses commands (`inventory`, `read-epc`, `write-epc`, `clear`, `set-power`).
- Hardware boundary: code talks to the reader via `com.rscja.deviceapi.RFIDWithUHFUsb` from the vendor JAR. Native interop is required (the DLL). Treat the JAR + DLL as a black-box SDK.

Key files to inspect
- `src/com/marijoa/rfid/Bridge.java` — primary logic and examples of how to call the SDK.
- `libs/ReaderAPI20240822.jar` — vendor SDK (do not modify). Look up its API when behavior is unclear.
- `natives/windows/UHFAPI.dll` — required at runtime on Windows. On Linux a counterpart may be needed under `natives/linux`.

How to run locally (developer commands)
- Compile and run on Windows (PowerShell example). Ensure `libs/*.jar` are on the classpath and `natives/windows/UHFAPI.dll` is discoverable (working directory or PATH):

    # compile
    javac -d out -cp libs/* src/com/marijoa/rfid/Bridge.java

    # run (example: inventory)
    java -cp out;libs/* com.marijoa.rfid.Bridge inventory

- Notes:
  - On Windows the native DLL must be in the current working directory, in a directory listed in PATH, or loaded via System.loadLibrary. The simplest approach is to run from the repo root where `UHFAPI.dll` exists.
  - If you switch to Linux, you'll need the equivalent native library in `natives/linux` and adjust the classpath/run invocation accordingly.

Project-specific patterns & conventions
- Minimal CLI style: methods return booleans and print human-readable messages (OK / ERROR / NO_TAG). When changing behavior keep the same simple stdout/stderr contract — scripts likely parse these literals.
- Tag filtering: when writing/clearing memory the code uses EPC filtering parameters (filterBank=EPC, filterPtrBits=32, filterLenBits = epcHex.length*4). Preserve the bit/offset math when editing write/clear logic.
- EPC format: `write-epc` converts decimal strings into BCD and pads/truncates to 96 bits (12 bytes). If modifying EPC logic, ensure parity handling, BCD conversion and PC calculation (pcVal = (epcWords << 11)) are preserved or intentionally changed with tests.

Integration points and external dependencies
- Vendor SDK (Jar) and platform native (DLL) are required at compile + runtime.
- No network services are used. Hardware I/O is the main external dependency.

Testing and debugging tips
- You can unit-test pure helper code (BCD conversion, padding, PC calc) by extracting small methods and running standard JUnit tests. But hardware calls require either the actual device or mocking the `RFIDWithUHFUsb` API (create an interface wrapper around the vendor class to allow mocks).
- For quick manual testing run CLI commands and inspect stdout/stderr. Scripts rely on literal outputs like `NO_TAG`, `OK`, `ERROR`, and `EPC=<hex>` — keep them stable.

When modifying code, follow these safety checks
- Don't remove or reformat the SDK calls — keep parameter ordering when calling `writeData` and inventory methods.
- Preserve the `reader.init("")` / `reader.free()` lifecycle. Failure to call `free()` may leave the device in a bad state.

Examples (copy from the codebase)
- Inventory loop pattern (used in `inventoryOnce`) — read from buffer until timeout, then stop inventory.
- Filtered write pattern (used in `writeEpcFiltered`):
  - detect tag via inventory
  - compute filterLenBits from detected tag EPC length
  - call `writeData` twice: first data payload, then PC word

If you need more context
- Inspect the vendor JAR for method signatures if behavior is unclear: `jar tf libs/ReaderAPI20240822.jar` and open classes with `javap -classpath libs/ReaderAPI20240822.jar com.rscja.deviceapi.RFIDWithUHFUsb`.

What not to change
- Do not commit or alter `libs/*.jar` binaries or `natives/*` DLLs. Treat them as external vendor artifacts.
- Avoid changing printed literals parsed by external scripts (e.g., `NO_TAG`, `OK`, error lines).

If you edit this file
- Keep suggestions brief and concrete. If proposing behavior changes (EPC length, different padding), include a short migration note and tests for BCD/PC calculation.

Questions for the maintainer (things AI may ask if blocked)
- Are there automated tests or CI we should integrate with? (none present in the repo)
- Is there an equivalent native library for Linux you expect to commit under `natives/linux`?

End of instructions.
