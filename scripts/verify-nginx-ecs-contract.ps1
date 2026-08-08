$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$configurationPath = Join-Path $repositoryRoot 'apps/web/nginx.conf'
$configuration = Get-Content -LiteralPath $configurationPath -Raw

$requiredLocations = @('/api/', '/actuator/')
$missing = @()

foreach ($location in $requiredLocations) {
    $escapedLocation = [regex]::Escape($location)
    $blockPattern = "(?s)location\s+$escapedLocation\s*\{(?:(?!location\s+).)*proxy_pass\s+http://127\.0\.0\.1:8080\s*;(?:(?!location\s+).)*\}"
    if ($configuration -notmatch $blockPattern) {
        $missing += $location
    }
}

if ($missing.Count -gt 0) {
    Write-Error "Missing ECS same-task proxy contract for: $($missing -join ', ')"
}

Write-Output 'NGINX_ECS_PROXY_CONTRACT=PASS'
