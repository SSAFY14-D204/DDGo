param(
    [Parameter(Mandatory = $true)]
    [string]$ExpectedModel,

    [Parameter(Mandatory = $true)]
    [string]$FriendlyName,

    [string]$PreferredEndpoint
)

$ErrorActionPreference = "Stop"

function Get-AdbPath {
    $sdkAdb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $sdkAdb) {
        return $sdkAdb
    }

    $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
    if ($adbCommand) {
        return $adbCommand.Source
    }

    throw "adb.exe not found. Install Android platform-tools first."
}

function Normalize-Model([string]$value) {
    return ($value -replace "[-_]", "").ToUpperInvariant()
}

function Get-MdnsEndpoints([string]$adbPath) {
    $output = & $adbPath mdns services
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to read adb mdns services."
    }

    $output |
        Where-Object { $_ -match "_adb-tls-connect\._tcp" } |
        ForEach-Object {
            $tokens = ($_ -split "\s+") | Where-Object { $_ }
            if ($tokens.Count -gt 0) {
                $tokens[-1]
            }
        } |
        Where-Object { $_ -match "^\d{1,3}(\.\d{1,3}){3}:\d+$" } |
        Select-Object -Unique
}

function Try-Connect([string]$adbPath, [string[]]$endpoints) {
    foreach ($endpoint in $endpoints) {
        Write-Host "Trying $endpoint ..."
        & $adbPath connect $endpoint | Out-Host
    }
}

function Find-MatchingDevice([string]$adbPath, [string]$expectedModel) {
    $devices = & $adbPath devices -l
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to read adb devices."
    }

    $serials = $devices |
        Where-Object { $_ -match "\bdevice\b" -and $_ -notmatch "^List of devices" } |
        ForEach-Object {
            $line = $_.Trim()
            if ($line -and $line -match "^(.*?)\s+device(?:\s|$)") {
                $matches[1].Trim()
            }
        }

    foreach ($serial in $serials) {
        $model = $null
        try {
            $model = (& $adbPath -s $serial shell getprop ro.product.model 2>$null | Out-String).Trim()
        } catch {
            continue
        }

        if ([string]::IsNullOrWhiteSpace($model)) {
            continue
        }

        if ((Normalize-Model $model) -eq (Normalize-Model $expectedModel)) {
            return [PSCustomObject]@{
                Serial = $serial
                Model = $model
            }
        }
    }

    return $null
}

$adbPath = Get-AdbPath
& $adbPath start-server | Out-Null

$candidateEndpoints = @()
if ($PreferredEndpoint) {
    $candidateEndpoints += $PreferredEndpoint
}
$candidateEndpoints += Get-MdnsEndpoints -adbPath $adbPath
$candidateEndpoints = $candidateEndpoints | Select-Object -Unique

if ($candidateEndpoints.Count -gt 0) {
    Try-Connect -adbPath $adbPath -endpoints $candidateEndpoints
    Start-Sleep -Seconds 1
}

$matched = Find-MatchingDevice -adbPath $adbPath -expectedModel $ExpectedModel
if ($matched) {
    Write-Host ""
    Write-Host "$FriendlyName connected."
    Write-Host "Model : $($matched.Model)"
    Write-Host "Serial: $($matched.Serial)"
    exit 0
}

Write-Host ""
Write-Host "Could not find $FriendlyName ($ExpectedModel)."
Write-Host "Current adb devices:"
& $adbPath devices -l | Out-Host
exit 1
