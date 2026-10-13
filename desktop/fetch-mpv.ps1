# Скачивает видеодвижок mpv (сборка shinchiro, x86_64) и интерфейс uosc в resources/windows/mpv.
# Конфиги (mpv.conf, input.conf, script-opts/uosc.conf) лежат в git и не перезаписываются.
$ErrorActionPreference = "Stop"
$root = Join-Path $PSScriptRoot "resources\windows\mpv"
$tmp = Join-Path $env:TEMP "smartplayer-mpv"
New-Item -ItemType Directory -Force $tmp, $root | Out-Null

$release = Invoke-RestMethod "https://api.github.com/repos/shinchiro/mpv-winbuild-cmake/releases/latest"
$asset = $release.assets | Where-Object { $_.name -match '^mpv-x86_64-\d.*\.7z$' } | Select-Object -First 1
Write-Host "mpv: $($asset.name)"
Invoke-WebRequest $asset.browser_download_url -OutFile "$tmp\mpv.7z"
Invoke-WebRequest "https://www.7-zip.org/a/7zr.exe" -OutFile "$tmp\7zr.exe"
& "$tmp\7zr.exe" x -y "-o$tmp\mpv" "$tmp\mpv.7z" | Out-Null
Copy-Item "$tmp\mpv\mpv.exe", "$tmp\mpv\mpv.com", "$tmp\mpv\d3dcompiler_43.dll" $root -Force
Copy-Item "$tmp\mpv\doc" $root -Recurse -Force

$uosc = "5.13.0"
Invoke-WebRequest "https://github.com/tomasklaen/uosc/releases/download/$uosc/uosc.zip" -OutFile "$tmp\uosc.zip"
Expand-Archive "$tmp\uosc.zip" "$tmp\uosc" -Force
Copy-Item "$tmp\uosc\scripts", "$tmp\uosc\fonts" (Join-Path $root "portable_config") -Recurse -Force
Write-Host "Готово: $root"
