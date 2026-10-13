$ErrorActionPreference = 'Stop'

$url = 'https://github.com/jgraph/drawio-desktop/releases/download/v31.4.5/draw.io-31.4.5.msi'
$headersPath = 'C:\Users\Lenovo\Desktop\bs\test-range.headers'
$dataPath = 'C:\Users\Lenovo\Desktop\bs\test-range.bin'
Remove-Item -LiteralPath $headersPath, $dataPath -Force -ErrorAction SilentlyContinue

& curl.exe -L --fail --show-error --retry 4 --retry-delay 1 --retry-all-errors `
    --connect-timeout 20 --max-time 90 --http1.1 `
    -H 'Range: bytes=8388608-8392703' `
    -H 'Accept-Encoding: identity' `
    -D $headersPath -o $dataPath $url

[pscustomobject]@{
    ExitCode = $LASTEXITCODE
    Headers = if (Test-Path -LiteralPath $headersPath) { Get-Content -LiteralPath $headersPath }
    Size = if (Test-Path -LiteralPath $dataPath) { (Get-Item -LiteralPath $dataPath).Length } else { 0 }
}
