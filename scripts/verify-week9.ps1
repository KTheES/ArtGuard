[CmdletBinding()]
param(
    [switch]$SkipDocker
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$frontend = Join-Path $repoRoot 'frontend'
$image = 'artguard-frontend:week9-local'
$container = 'artguard-frontend-week9'

function Invoke-Step {
    param([string]$Name, [scriptblock]$Action)
    Write-Host "`n==> $Name" -ForegroundColor Cyan
    & $Action
    if ($LASTEXITCODE -ne 0) {
        throw "$Name failed with exit code $LASTEXITCODE"
    }
}

Push-Location $frontend
try {
    Invoke-Step 'Install locked npm dependencies' { npm ci }
    Invoke-Step 'Lint' { npm run lint }
    Invoke-Step 'Test' { npm test }
    Invoke-Step 'Build' { npm run build }
    Invoke-Step 'Package dry run' { npm pack --dry-run }
    Invoke-Step 'Dependency audit' { npm audit --audit-level=high }
}
finally {
    Pop-Location
}

if ($SkipDocker) {
    Write-Host "`nDocker verification skipped by request." -ForegroundColor Yellow
    exit 0
}

Invoke-Step 'Check Docker engine' { docker info }
Invoke-Step 'Build Docker image' { docker build --tag $image --file (Join-Path $frontend 'Dockerfile') $frontend }

try {
    docker rm --force $container 2>$null | Out-Null
    Invoke-Step 'Start Docker container' { docker run --detach --name $container --publish 127.0.0.1:5173:5173 $image }

    $healthy = $false
    for ($attempt = 1; $attempt -le 30; $attempt++) {
        $status = docker inspect --format '{{.State.Health.Status}}' $container
        if ($status -eq 'healthy') {
            $healthy = $true
            break
        }
        if ($status -eq 'unhealthy') {
            break
        }
        Start-Sleep -Seconds 2
    }

    if (-not $healthy) {
        docker logs $container
        throw 'Container did not become healthy.'
    }

    $response = Invoke-WebRequest -UseBasicParsing 'http://127.0.0.1:5173/'
    if ($response.StatusCode -ne 200 -or $response.Content -notmatch 'ArtworkGuard') {
        throw 'Container HTTP smoke test failed.'
    }
    Write-Host "`nWeek 9 verification passed." -ForegroundColor Green
}
finally {
    docker rm --force $container 2>$null | Out-Null
}
