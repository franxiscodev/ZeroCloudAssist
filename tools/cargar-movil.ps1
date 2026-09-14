# Copia al movil los ficheros del RAG que van fuera del APK y comprueba su SHA256.
# Forma minima del paso 3.13 del plan 02, adelantada a la etapa 2.
# Desde la raiz del repo, con la app instalada y abierta una vez.
# Con el movil por USB y por wifi a la vez, adb exige elegir: -Serial <id de `adb devices`>.
param([string]$Serial)

$ErrorActionPreference = 'Stop'
$app = '/sdcard/Android/data/com.ialogia.zerocloudassist/files'
$ficheros = @(
    @{ Local = 'models/acs355.sqlite'; Destino = "$app/manuales" },
    @{ Local = 'models/multilingual-e5-small-q8_0.gguf'; Destino = "$app/models" },
    # Vectores del PC para comparar con los del movil (paso 2.3, solo en depuracion).
    @{ Local = 'models/bateria-vectores.json'; Destino = "$app/debug" }
)
$dispositivo = if ($Serial) { @('-s', $Serial) } else { @() }

foreach ($f in $ficheros) {
    $nombre = Split-Path $f.Local -Leaf
    adb @dispositivo shell mkdir -p $f.Destino
    adb @dispositivo push $f.Local "$($f.Destino)/"
    if ($LASTEXITCODE) { throw "fallo adb push de $nombre" }

    $pc = (Get-FileHash $f.Local -Algorithm SHA256).Hash.ToLower()
    $movil = (adb @dispositivo shell sha256sum "$($f.Destino)/$nombre").Split(' ')[0]
    if ($pc -ne $movil) { throw "SHA256 distinto en ${nombre}: PC $pc, movil $movil" }
    Write-Host "OK $nombre $pc"
}
