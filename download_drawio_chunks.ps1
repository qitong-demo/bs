$ErrorActionPreference = 'Stop'

$workspace = 'C:\Users\Lenovo\Desktop\bs'
$url = 'https://github.com/jgraph/drawio-desktop/releases/download/v31.4.5/draw.io-31.4.5.msi'
$output = Join-Path $workspace 'draw.io-31.4.5.msi'
$assembled = Join-Path $workspace 'draw.io-31.4.5.msi.complete'
$partsDir = Join-Path $workspace 'drawio-msi-parts'
$totalSize = [int64]151318528
$chunkSize = [int64]8388608
$throttle = 8

New-Item -ItemType Directory -Path $partsDir -Force | Out-Null

$ranges = [System.Collections.Generic.List[object]]::new()
$index = 0
for ($start = [int64]0; $start -lt $totalSize; $start += $chunkSize) {
    $end = [Math]::Min($start + $chunkSize - 1, $totalSize - 1)
    $expected = $end - $start + 1
    $ranges.Add([pscustomobject]@{
        Index = $index
        Start = $start
        End = $end
        Expected = $expected
        Part = (Join-Path $partsDir ('part-{0:D3}.bin' -f $index))
    })
    $index++
}

$ranges | ForEach-Object -Parallel {
    $r = $_
    $existing = if (Test-Path -LiteralPath $r.Part) {
        (Get-Item -LiteralPath $r.Part).Length
    } else {
        [int64]0
    }

    if ($existing -gt $r.Expected) {
        Remove-Item -LiteralPath $r.Part -Force
        $existing = [int64]0
    }

    $pending = "$($r.Part).download"
    if (Test-Path -LiteralPath $pending) {
        $pendingLength = (Get-Item -LiteralPath $pending).Length
        if ($pendingLength -le ($r.Expected - $existing)) {
            $append = [System.IO.File]::Open($r.Part, [System.IO.FileMode]::Append, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
            try {
                $pendingStream = [System.IO.File]::OpenRead($pending)
                try {
                    $pendingStream.CopyTo($append)
                }
                finally {
                    $pendingStream.Dispose()
                }
            }
            finally {
                $append.Dispose()
            }
            $existing += $pendingLength
        }
        Remove-Item -LiteralPath $pending -Force -ErrorAction SilentlyContinue
    }

    $attempt = 0
    while ($existing -lt $r.Expected) {
        $attempt++
        if ($attempt -gt 100) {
            throw "part $($r.Index) exceeded retry limit with $existing/$($r.Expected) bytes"
        }
        $resumeStart = $r.Start + $existing
        $rangeHeader = 'bytes={0}-{1}' -f $resumeStart, $r.End
        $remaining = $r.Expected - $existing
        $temp = "$($r.Part).download"
        $responseHeaders = "$($r.Part).headers"
        Remove-Item -LiteralPath $temp -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $responseHeaders -Force -ErrorAction SilentlyContinue

        & curl.exe -L --fail --silent --show-error --retry 0 `
            --connect-timeout 20 --max-time 90 --http1.1 `
            -H "Range: $rangeHeader" -H 'Accept-Encoding: identity' `
            -D $responseHeaders -o $temp $using:url
        $curlCode = $LASTEXITCODE
        $received = if (Test-Path -LiteralPath $temp) {
            (Get-Item -LiteralPath $temp).Length
        } else {
            [int64]0
        }
        $contentRange = if (Test-Path -LiteralPath $responseHeaders) {
            (Get-Content -LiteralPath $responseHeaders | Select-String -Pattern 'Content-Range:\s*bytes\s+(\d+)-(\d+)/(\d+)' | Select-Object -Last 1).Matches.Groups
        } else {
            $null
        }

        if ($received -gt 0 -and $received -le $remaining -and $contentRange -and [int64]$contentRange[1].Value -eq $resumeStart) {
            $out = [System.IO.File]::Open($r.Part, [System.IO.FileMode]::Append, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
            try {
                $in = [System.IO.File]::OpenRead($temp)
                try {
                    $in.CopyTo($out)
                }
                finally {
                    $in.Dispose()
                }
            }
            finally {
                $out.Dispose()
            }
            $existing += $received
        }

        Remove-Item -LiteralPath $temp, $responseHeaders -Force -ErrorAction SilentlyContinue
        if ($received -le 0 -or $received -gt $remaining -or -not $contentRange -or [int64]$contentRange[1].Value -ne $resumeStart) {
            Start-Sleep -Milliseconds (Get-Random -Minimum 200 -Maximum 1200)
            continue
        }
        if ($curlCode -ne 0 -and $received -eq 0) {
            Start-Sleep -Milliseconds (Get-Random -Minimum 500 -Maximum 1600)
        }
    }

    Write-Output ("part {0} complete ({1} bytes)" -f $r.Index, $existing)
} -ThrottleLimit $throttle

if (Test-Path -LiteralPath $assembled) {
    Remove-Item -LiteralPath $assembled -Force
}

$outStream = [System.IO.File]::Open($assembled, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
try {
    foreach ($r in $ranges) {
        $inStream = [System.IO.File]::OpenRead($r.Part)
        try {
            $inStream.CopyTo($outStream)
        }
        finally {
            $inStream.Dispose()
        }
    }
}
finally {
    $outStream.Dispose()
}

$assembledLength = (Get-Item -LiteralPath $assembled).Length
if ($assembledLength -ne $totalSize) {
    throw "assembled file has $assembledLength bytes; expected $totalSize"
}

$hash = (Get-FileHash -LiteralPath $assembled -Algorithm SHA256).Hash
Move-Item -LiteralPath $assembled -Destination $output -Force

$partsResolved = [System.IO.Path]::GetFullPath($partsDir)
$workspaceResolved = [System.IO.Path]::GetFullPath($workspace)
if ($partsResolved.StartsWith($workspaceResolved, [System.StringComparison]::OrdinalIgnoreCase)) {
    Remove-Item -LiteralPath $partsDir -Recurse -Force
}

[pscustomobject]@{
    File = $output
    Length = $totalSize
    SHA256 = $hash
}
