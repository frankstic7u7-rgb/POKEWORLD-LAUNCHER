# Chequeo puntual (no queda corriendo): si plugins/ItemsAdder/output/generated.zip
# cambio desde la ultima vez, sincroniza y publica. Pensado para llamarse cada
# pocos minutos desde el Programador de tareas de Windows -- sin ventana, sin bat.

$ErrorActionPreference = "Stop"

$IaZip      = "C:\Users\User\AppData\Roaming\.minecraft\PokeWorld\plugins\ItemsAdder\output\generated.zip"
$SyncScript = "C:\Users\User\AppData\Roaming\.minecraft\POKEWORLD-LAUNCHER\sync_resourcepack.ps1"
$HashMarker = "C:\Users\User\AppData\Roaming\.minecraft\POKEWORLD-LAUNCHER\_last_synced_resourcepack.sha1"

if (-not (Test-Path $IaZip)) { exit }

$current = (Get-FileHash -Path $IaZip -Algorithm SHA1).Hash
$last = if (Test-Path $HashMarker) { (Get-Content $HashMarker -Raw).Trim() } else { $null }

if ($current -eq $last) { exit }

# Esperar a que el zip termine de escribirse (tamano estable) antes de tocarlo.
$prevSize = -1
while ($true) {
    Start-Sleep -Seconds 3
    if (-not (Test-Path $IaZip)) { exit }
    $size = (Get-Item $IaZip).Length
    if ($size -eq $prevSize -and $size -gt 0) { break }
    $prevSize = $size
}

& powershell -NoProfile -ExecutionPolicy Bypass -File $SyncScript

$newHash = (Get-FileHash -Path $IaZip -Algorithm SHA1).Hash
Set-Content -Path $HashMarker -Value $newHash -NoNewline
