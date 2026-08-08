# Vigila plugins/ItemsAdder/output/generated.zip y corre sync_resourcepack.ps1
# automaticamente cada vez que cambia (por ej. despues de /iazip en el server).
# Dejar esta ventana abierta -- mientras corra, nunca mas hace falta acordarse
# de re-empaquetar el resourcepack a mano.

$ErrorActionPreference = "Stop"

$IaZip       = "C:\Users\User\AppData\Roaming\.minecraft\PokeWorld\plugins\ItemsAdder\output\generated.zip"
$SyncScript  = "C:\Users\User\AppData\Roaming\.minecraft\POKEWORLD-LAUNCHER\sync_resourcepack.ps1"
$HashMarker  = "C:\Users\User\AppData\Roaming\.minecraft\POKEWORLD-LAUNCHER\_last_synced_resourcepack.sha1"
$PollSeconds = 20

function Get-CurrentHash {
    if (Test-Path $IaZip) { (Get-FileHash -Path $IaZip -Algorithm SHA1).Hash } else { $null }
}

Write-Host "Vigilando $IaZip cada $PollSeconds segundos -- Ctrl+C para detener."

$lastHash = if (Test-Path $HashMarker) { (Get-Content $HashMarker -Raw).Trim() } else { Get-CurrentHash }
Write-Host "Hash inicial: $lastHash"

while ($true) {
    Start-Sleep -Seconds $PollSeconds

    $current = Get-CurrentHash
    if (-not $current) { continue }
    if ($current -eq $lastHash) { continue }

    Write-Host "$(Get-Date -Format 'HH:mm:ss') Cambio detectado en generated.zip -- esperando a que termine de escribirse..."

    # Esperar a que el tamano del archivo se estabilice (ItemsAdder puede tardar
    # unos segundos en terminar de escribir el zip).
    $prevSize = -1
    while ($true) {
        Start-Sleep -Seconds 4
        if (-not (Test-Path $IaZip)) { continue }
        $size = (Get-Item $IaZip).Length
        if ($size -eq $prevSize -and $size -gt 0) { break }
        $prevSize = $size
    }

    Write-Host "$(Get-Date -Format 'HH:mm:ss') Sincronizando..."
    try {
        & powershell -NoProfile -ExecutionPolicy Bypass -File $SyncScript
    } catch {
        Write-Host "ERROR corriendo sync_resourcepack.ps1: $($_.Exception.Message)"
    }

    $lastHash = Get-CurrentHash
    Set-Content -Path $HashMarker -Value $lastHash -NoNewline
}
