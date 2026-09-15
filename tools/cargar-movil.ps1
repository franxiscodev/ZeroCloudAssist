# Copia al movil los ficheros que van fuera del APK y comprueba su SHA256 (plan 02, paso 3.13).
# Solo copia lo que falta o ha cambiado: compara el SHA256 del PC con el de `sha256sum` en el movil.
# Desde la raiz del repo, con la app instalada y abierta una vez.
# Con el movil por USB y por wifi a la vez, adb exige elegir: -Serial <id de `adb devices`>.
param([string]$Serial)

$ErrorActionPreference = 'Stop'
$app = '/sdcard/Android/data/com.ialogia.zerocloudassist/files'
$ficheros = @(
    @{ Local = 'models/qwen2.5-1.5b-instruct-q4_k_m.gguf'; Destino = "$app/models" },
    @{ Local = 'models/multilingual-e5-small-q8_0.gguf'; Destino = "$app/models" },
    @{ Local = 'models/acs355.sqlite'; Destino = "$app/manuales" }
)
$dispositivo = if ($Serial) { @('-s', $Serial) } else { @() }

function Sha-Movil([string]$ruta) {
    # Vacio si el fichero no existe en el movil.
    $salida = adb @dispositivo shell "sha256sum '$ruta' 2>/dev/null"
    if ($salida) { return ($salida -split '\s+')[0] } else { return '' }
}

foreach ($f in $ficheros) {
    $nombre = Split-Path $f.Local -Leaf
    $remoto = "$($f.Destino)/$nombre"
    $pc = (Get-FileHash $f.Local -Algorithm SHA256).Hash.ToLower()

    if ((Sha-Movil $remoto) -eq $pc) {
        Write-Host "OK $nombre $pc (ya estaba)"
        continue
    }
    adb @dispositivo shell mkdir -p $f.Destino
    adb @dispositivo push $f.Local "$($f.Destino)/"
    if ($LASTEXITCODE) { throw "fallo adb push de $nombre" }

    $movil = Sha-Movil $remoto
    if ($pc -ne $movil) { throw "SHA256 distinto en ${nombre}: PC $pc, movil $movil" }
    Write-Host "OK $nombre $pc"
}
