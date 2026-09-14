# Guardia de estres (plan 02, pasos 3.16 y 5.3): encadena preguntas en la app sin reiniciarla y
# comprueba en logcat que el reinicio de la cache, el tope de tokens y las fuentes aguantan.
# Es la guardia de la leccion 1 del plan 01: el protocolo en verde no ejercitaba los fallos del JNI.
#
# Desde la raiz del repo, con la app abierta y lista. Sin tildes: `adb input text` no las admite.
# La pantalla solo se lee (uiautomator) con la app parada; mientras genera se espera por logcat,
# porque uiautomator roba CPU y falsea las medidas.
# Con el movil por USB y por wifi a la vez: -Serial <id de `adb devices`>.
param([int]$Preguntas = 20, [int]$TimeoutPorPregunta = 120, [string]$Serial)

$ErrorActionPreference = 'Stop'
$paquete = 'com.ialogia.zerocloudassist'
$dispositivo = if ($Serial) { @('-s', $Serial) } else { @() }

# Codigos, sintomas, seguridad, parametros y preguntas fuera del manual.
$lista = @(
    'Que significa el fallo F0009 y que reviso',
    'El variador muestra la alarma A2001 que hago',
    'El armario esta muy caliente y el variador se para',
    'Como mido la tension del bus de continua',
    'Para que sirve el parametro 9905',
    'Como cambio el ventilador del variador',
    'El motor gira al reves que compruebo',
    'Que es la funcion STO',
    'Me sale el error 0016 al arrancar',
    'Como ajusto la rampa de aceleracion',
    'Que hago si el variador no arranca y no muestra ningun fallo',
    'Voy a desmontar el cable del motor que precauciones tomo',
    'Como configuro la entrada analogica AI1',
    'Que significa el fallo F0001',
    'Como reseteo un fallo desde el panel',
    'Para que sirve el parametro 1202',
    'El variador tiene conexion Bluetooth',
    'Cuanto cuesta el variador ACS355',
    'Que mantenimiento necesita el variador',
    'Que hago si aparece sobretension en el bus'
)

function Get-Center([string]$xpath) {
    adb @dispositivo shell uiautomator dump /sdcard/zca-ui.xml | Out-Null
    $node = ([xml](adb @dispositivo shell cat /sdcard/zca-ui.xml)).SelectNodes($xpath) | Select-Object -First 1
    if (-not $node -or $node.bounds -notmatch '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') { throw "No encuentro $xpath" }
    @((([int]$matches[1] + [int]$matches[3]) / 2), (([int]$matches[2] + [int]$matches[4]) / 2))
}
function Get-Lines([string]$pattern) {
    @(adb @dispositivo logcat -d -T $desde -s ZCA ZCA_METRICS ZCA_RESPUESTA InferenceEngineImpl |
        Select-String -Pattern $pattern)
}
function Show-Memory([string]$titulo) {
    "--- memoria $titulo ---"
    adb @dispositivo shell dumpsys meminfo $paquete | Select-String 'Native Heap|TOTAL PSS|TOTAL SWAP'
    adb @dispositivo shell cat /proc/meminfo | Select-String 'MemAvailable'
}

# Solo cuenta lo que pase desde ahora (sin borrar el logcat de la bateria anterior). En segundos
# desde 1970, sin espacios: `adb shell` junta los argumentos y la shell del movil los volveria a partir.
$desde = "$((adb @dispositivo shell date +%s).Trim()).000"
Show-Memory 'antes'
$fin = 'ZCA_RESPUESTA|Error al generar|Failed'
$conMetricas = 0

for ($n = 0; $n -lt [Math]::Min($Preguntas, $lista.Count); $n++) {
    $antes = (Get-Lines $fin).Count
    $metricasAntes = (Get-Lines 'ZCA_METRICS').Count

    # Vaciar el campo (puede quedar texto de un intento anterior) y escribir la pregunta.
    $campo = Get-Center '//node[@class="android.widget.EditText"]'
    adb @dispositivo shell input tap $campo[0] $campo[1]
    adb @dispositivo shell input keycombination 113 29   # Ctrl+A
    adb @dispositivo shell input keyevent 67              # borrar
    adb @dispositivo shell input text ($lista[$n] -replace ' ', '%s')
    Start-Sleep -Milliseconds 800
    # Con el teclado abierto el boton cambia de sitio: se busca despues de escribir.
    $boton = Get-Center '//node[@text="Preguntar"]/..'
    adb @dispositivo shell input tap $boton[0] $boton[1]

    $inicio = Get-Date
    $limite = $inicio.AddSeconds($TimeoutPorPregunta)
    while ((Get-Date) -lt $limite -and (Get-Lines $fin).Count -le $antes) { Start-Sleep -Seconds 3 }
    Start-Sleep -Seconds 1   # la linea de metricas va justo detras de la respuesta
    $segundos = [int]((Get-Date) - $inicio).TotalSeconds
    $metricas = Get-Lines 'ZCA_METRICS' | Select-Object -Last 1
    if ((Get-Lines 'ZCA_METRICS').Count -gt $metricasAntes) {
        $conMetricas++
        "P$($n + 1) ($segundos s): $($metricas.Line -replace '.*ZCA_METRICS:\s*', '')"
    } else {
        "P$($n + 1) ($segundos s): SIN METRICAS · $((Get-Lines $fin | Select-Object -Last 1).Line)"
    }
    Start-Sleep -Seconds 2
}

"--- $conMetricas/$([Math]::Min($Preguntas, $lista.Count)) con metricas ---"
"--- resumen logcat ---"
# Firmas concretas y distinguiendo mayusculas: ai-chat tambien escribe en el log el texto de las
# preguntas, los fragmentos y las respuestas, que dicen "error" o "fallo" sin que haya fallo.
adb @dispositivo logcat -d -T $desde -s ai-chat ZCA InferenceEngineImpl AndroidRuntime DEBUG libc |
    Select-String -CaseSensitive -Pattern 'FATAL EXCEPTION|llama_decode.*fail|Error al generar|Failed to process|STOP: hitting' |
    ForEach-Object { $_.Line }
Show-Memory 'despues'
adb @dispositivo shell rm -f /sdcard/zca-ui.xml
