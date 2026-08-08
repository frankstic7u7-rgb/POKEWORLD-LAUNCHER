# Sincroniza plugins/ItemsAdder/output/generated.zip -> el resourcepack empaquetado
# en el launcher (Pro y Lite), cada vez que cambia (por ej. tras correr /iazip).
#
# Uso manual:    powershell -File sync_resourcepack.ps1
# Uso automatico: watch_resourcepack.ps1 llama a este script solo cuando detecta un cambio real.

$ErrorActionPreference = "Stop"

$LauncherRoot = "C:\Users\User\AppData\Roaming\.minecraft\POKEWORLD-LAUNCHER"
$IaZip        = "C:\Users\User\AppData\Roaming\.minecraft\PokeWorld\plugins\ItemsAdder\output\generated.zip"
$AssetsRepo   = "frankstic7u7-rgb/POKEWORLD-LAUNCHER"
$AssetsTag    = "assets-v1"
$LogFile      = Join-Path $LauncherRoot "_resourcepack_sync.log"

function Log($msg) {
    $line = "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') $msg"
    Write-Host $line
    Add-Content -Path $LogFile -Value $line
}

function Get-Sha1Hex($path) {
    (Get-FileHash -Path $path -Algorithm SHA1).Hash.ToLower()
}

function Ensure-AssetUploaded($filePath, $assetName) {
    $existing = gh release view $AssetsTag -R $AssetsRepo --json assets -q ".assets[].name" 2>$null
    if ($existing -contains $assetName) {
        Log "  ya existe en el release: $assetName (sin re-subir)"
        return
    }
    Log "  subiendo $assetName ($([math]::Round((Get-Item $filePath).Length / 1MB, 1)) MB)..."
    $tmpNamed = Join-Path $env:TEMP $assetName
    Copy-Item $filePath $tmpNamed -Force
    gh release upload $AssetsTag $tmpNamed -R $AssetsRepo --clobber | Out-Null
    Remove-Item $tmpNamed -Force
    Log "  subido: $assetName"
}

function Patch-And-Publish($packName, $outputDir, $manifestFile, $feedDir) {
    Log "--- Publicando $packName ---"
    $manifestPath = Join-Path $outputDir $manifestFile
    $manifest = Get-Content $manifestPath -Raw | ConvertFrom-Json

    # Archivos grandes conocidos (>5MB): se redirigen siempre a GitHub Releases,
    # subiendolos si hace falta (el nombre del asset es su propio hash sha1).
    foreach ($task in $manifest.tasks) {
        if ($task.size -gt 5MB) {
            $hash = Split-Path $task.location -Leaf
            $srcFile = Join-Path $outputDir "objects\$($task.location)"
            Ensure-AssetUploaded -filePath $srcFile -assetName $hash
            $task.location = "https://github.com/$AssetsRepo/releases/download/$AssetsTag/$hash"
        }
    }
    $manifest | ConvertTo-Json -Depth 20 | Set-Content -Path (Join-Path $feedDir $manifestFile) -Encoding utf8

    # Objetos chicos (todo lo que no se redirigio arriba)
    foreach ($task in $manifest.tasks) {
        if ($task.location -notlike "https://*") {
            $src = Join-Path $outputDir "objects\$($task.location)"
            $dst = Join-Path $feedDir "objects\$($task.location)"
            New-Item -ItemType Directory -Force -Path (Split-Path $dst) | Out-Null
            Copy-Item $src $dst -Force
        }
    }

    # Librerias
    $libSrc = Join-Path $outputDir "libraries"
    $libDst = Join-Path $feedDir "libraries"
    Copy-Item "$libSrc\*" $libDst -Recurse -Force

    Log "--- $packName publicado (v$($manifest.version)) ---"
}

try {
    if (-not (Test-Path $IaZip)) {
        Log "ERROR: no se encontro $IaZip -- corriste /iazip alguna vez?"
        exit 1
    }

    Log "=== Sincronizando resourcepack ==="

    # 1) Copiar el zip regenerado a los 2 proyectos fuente del launcher
    Copy-Item $IaZip "$LauncherRoot\build_project\src\resourcepacks\PokeWorld-ResourcePack.zip" -Force
    New-Item -ItemType Directory -Force -Path "$LauncherRoot\build_project_lite\src\resourcepacks" | Out-Null
    Copy-Item $IaZip "$LauncherRoot\build_project_lite\src\resourcepacks\PokeWorld-ResourcePack.zip" -Force
    Log "Copiado a build_project(_lite)/src/resourcepacks"

    # 2) Leer y subir versiones
    $pkgPath = "$LauncherRoot\feed\packages.json"
    $pkg = Get-Content $pkgPath -Raw | ConvertFrom-Json
    $proEntry  = $pkg.packages | Where-Object { $_.name -eq "pokeworld" }
    $liteEntry = $pkg.packages | Where-Object { $_.name -eq "pokeworld_lite" }
    $proVer  = [int]$proEntry.version + 1
    $liteVer = [int]$liteEntry.version + 1
    Log "Version nueva: Pro=$proVer Lite=$liteVer"

    # 3) Sacar watermedia de Lite (no va en el modpack) antes de regenerar
    $waterDir = "$LauncherRoot\_watermedia_tmp_auto"
    New-Item -ItemType Directory -Force -Path $waterDir | Out-Null
    Move-Item "$LauncherRoot\build_project_lite\src\mods\watermedia-3.0.0.21.jar" $waterDir -Force
    Move-Item "$LauncherRoot\build_project_lite\src\mods\watermedia_binaries-3.0.0.6.jar" $waterDir -Force

    $builderJar = "$LauncherRoot\launcher-builder\build\libs\launcher-builder-4.6-SNAPSHOT.jar"

    Log "Regenerando manifiesto Pro..."
    & java -jar $builderJar --input "$LauncherRoot\build_project" --output "$LauncherRoot\build_project_output" `
        --version $proVer --manifest-dest "$LauncherRoot\build_project_output\pokeworld.json" --pretty-print *>> $LogFile

    Log "Regenerando manifiesto Lite..."
    & java -jar $builderJar --input "$LauncherRoot\build_project_lite" --output "$LauncherRoot\build_project_lite_output" `
        --version $liteVer --manifest-dest "$LauncherRoot\build_project_lite_output\pokeworld_lite.json" --pretty-print *>> $LogFile

    # Restaurar watermedia
    Move-Item "$waterDir\watermedia-3.0.0.21.jar" "$LauncherRoot\build_project_lite\src\mods\" -Force
    Move-Item "$waterDir\watermedia_binaries-3.0.0.6.jar" "$LauncherRoot\build_project_lite\src\mods\" -Force
    Remove-Item $waterDir -Force -Recurse

    # 4) Parchear redirects + copiar objetos/librerias a feed/
    Patch-And-Publish "Pro"  "$LauncherRoot\build_project_output"      "pokeworld.json"      "$LauncherRoot\feed\pokeworld_pack"
    Patch-And-Publish "Lite" "$LauncherRoot\build_project_lite_output" "pokeworld_lite.json" "$LauncherRoot\feed\pokeworld_lite_pack"

    # 5) Bump packages.json
    $proEntry.version = "$proVer"
    $liteEntry.version = "$liteVer"
    $pkg | ConvertTo-Json -Depth 10 | Set-Content -Path $pkgPath -Encoding utf8
    Log "packages.json actualizado"

    # 6) git commit + push
    Push-Location $LauncherRoot
    git add feed/ | Out-Null
    git commit -m "Auto-sync resourcepack tras /iazip (Pro v$proVer, Lite v$liteVer)" | Out-Null
    git push origin master 2>&1 | ForEach-Object { Log $_ }
    Pop-Location

    Log "=== Listo: Pro v$proVer, Lite v$liteVer publicados ==="
}
catch {
    Log "ERROR: $($_.Exception.Message)"
    Log $_.ScriptStackTrace
    exit 1
}
