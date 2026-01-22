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
    echo json_encode([
        "ok" => false,
        "error" => "Parametro 'accion' requerido"
    ]);
    exit;
}

// -------------------------------------------------
// 2. Acciones permitidas
// -------------------------------------------------
$acciones = [
    "inventory"     => [],
    "scan"          => [],
    "read-epc"      => [],
    "write-epc"     => ["epc"],
    "clear"         => ["palabras"],
    "clear-filter"  => [],
    "set-power"     => ["nivel"],
    "get-freq"      => [],
    "set-freq"      => ["modo"]
];

if (!isset($acciones[$accion])) {
    echo json_encode([
        "ok" => false,
        "error" => "Accion no permitida",
        "accion" => $accion
    ]);
    exit;
}

// -------------------------------------------------
// 3. Construir comando Java
// -------------------------------------------------
$baseDir = realpath(__DIR__);
$isWindows = strtoupper(substr(PHP_OS, 0, 3)) === 'WIN';

$params = [];
foreach ($acciones[$accion] as $p) {
    if (isset($_REQUEST[$p])) {
        $params[] = escapeshellarg($_REQUEST[$p]);
    }
}

// Validaciones mínimas
if ($accion === "write-epc" && empty($params)) {
    error("write-epc requiere parametro 'epc'");
}
if ($accion === "set-power" && empty($params)) {
    error("set-power requiere parametro 'nivel'");
}
if ($accion === "set-freq" && empty($params)) {
    error("set-freq requiere parametro 'modo'");
}

$javaCmd = "java --enable-native-access=ALL-UNNAMED";  // Esto es para que no tire warnings

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
// 4. Ejecutar
// -------------------------------------------------
$descriptorspec = [
    1 => ["pipe", "w"], // stdout
    2 => ["pipe", "w"], // stderr
];

$process = proc_open($javaCmd, $descriptorspec, $pipes, $baseDir);

if (!is_resource($process)) {
    error("No se pudo ejecutar Java");
}

$stdout = stream_get_contents($pipes[1]);
$stderr = stream_get_contents($pipes[2]);

fclose($pipes[1]);
fclose($pipes[2]);

$exitCode = proc_close($process);

// -------------------------------------------------
// 5. Respuesta
// -------------------------------------------------
echo json_encode([
    "ok" => $exitCode === 0,
    "accion" => $accion,
    "comando" => $javaCmd,
    "stdout" => trim($stdout),
    "stderr" => trim($stderr),
    "exit_code" => $exitCode
], JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE);

// -------------------------------------------------
function error($msg)
{
    echo json_encode([
        "ok" => false,
        "error" => $msg
    ]);
    exit;
}
