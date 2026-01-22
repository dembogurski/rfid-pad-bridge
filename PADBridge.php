<?php
/**
 * PADBridge.php
 * Bridge PHP → Java RFID
 *
 * Autor: Ing. Doglas A. Dembogurski Feix
 * Fecha: 21-01-2026
 */

header("Content-Type: application/json; charset=utf-8");

// -------------------------------------------------
// 1. Entrada
// -------------------------------------------------
$accion = $_REQUEST['action'] ?? null;

if (!$accion) {
    error("Parametro 'accion' requerido");
}

// -------------------------------------------------
// 2. Acciones permitidas
// -------------------------------------------------
$acciones = [
    "inventory"     => [],
    "read-epc"      => [],
    "write-epc"     => ["epc"],
    "clear"         => ["palabras"]
];

if (!isset($acciones[$accion])) {
    error("Accion no permitida: $accion");
}

// -------------------------------------------------
// 3. Construir comando Java
// -------------------------------------------------
$baseDir   = realpath(__DIR__);
$isWindows = strtoupper(substr(PHP_OS, 0, 3)) === 'WIN';

$params = [];
foreach ($acciones[$accion] as $p) {
    if (!isset($_REQUEST[$p])) {
        error("Falta parametro '$p'");
    }
    $params[] = escapeshellarg($_REQUEST[$p]);
}

$javaCmd = "java --enable-native-access=ALL-UNNAMED";

if ($isWindows) {
    $javaCmd .= ' -Djava.library.path=natives\\windows -cp "out;libs\\*"';
} else {
    $javaCmd .= ' -Djava.library.path=natives/linux -cp "out:libs/*"';
}

$javaCmd .= ' com.marijoa.rfid.Bridge ' . $accion;

if (!empty($params)) {
    $javaCmd .= ' ' . implode(" ", $params);
}

// -------------------------------------------------
// 4. Ejecutar proceso Java
// -------------------------------------------------
$descriptorspec = [
    1 => ["pipe", "w"], // STDOUT
    2 => ["pipe", "w"], // STDERR
];

$process = proc_open($javaCmd, $descriptorspec, $pipes, $baseDir);

if (!is_resource($process)) {
    error("No se pudo ejecutar Java");
}

$rawStdout = stream_get_contents($pipes[1]);
$rawStderr = stream_get_contents($pipes[2]);

fclose($pipes[1]);
fclose($pipes[2]);

$exitCode = proc_close($process);

// -------------------------------------------------
// 5. Filtrar salida (eliminar ruido nativo)
// -------------------------------------------------
$lines = preg_split("/\r\n|\n|\r/", $rawStdout);

$clean = [];
foreach ($lines as $line) {
    $line = trim($line);

    if ($line === '') continue;

    if (
        str_starts_with($line, 'DETECTED=') ||
        str_starts_with($line, 'WRITTEN=')  ||
        str_starts_with($line, 'ERROR=')    ||
        $line === 'OK' ||
        $line === 'NO_TAG'
    ) {
        $clean[] = $line;
    }
}

$stdout = implode("\n", $clean);

// -------------------------------------------------
// 6. Respuesta JSON
// -------------------------------------------------
echo json_encode([
    "ok"        => ($exitCode === 0),
    "accion"    => $accion,
    "resultado" => $stdout,
    "exit_code" => $exitCode
], JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE);

// -------------------------------------------------
function error(string $msg): void
{
    echo json_encode([
        "ok"    => false,
        "error" => $msg
    ], JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE);
    exit;
}
