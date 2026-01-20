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