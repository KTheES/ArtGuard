[CmdletBinding()]
param(
 [Parameter(Mandatory=$true)][string]$OutputDirectory
)
$ErrorActionPreference='Stop'
# libpq connection and credentials come from PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSFILE.
# Destination must be on an encrypted volume with restricted operator access.
foreach($tool in @('pg_dump','pg_restore')){Get-Command $tool -ErrorAction Stop|Out-Null}
if([string]::IsNullOrWhiteSpace($env:PGDATABASE)){throw 'PGDATABASE is required'}
if($env:PGSSLMODE -ne 'verify-full'){throw 'PGSSLMODE=verify-full is required for backup'}
$destination=[IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $destination -Force|Out-Null
$stamp=[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')
$archive=Join-Path $destination ("artworkguard-"+$stamp+"-"+[Guid]::NewGuid().ToString('N')+".dump")
& pg_dump --format=custom --no-owner --no-acl --file=$archive
if($LASTEXITCODE -ne 0){throw 'pg_dump failed; incomplete archive retained for inspection'}
& pg_restore --list $archive | Out-Null
if($LASTEXITCODE -ne 0){throw 'Archive directory validation failed'}
$digest=(Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash
[pscustomobject]@{file=[IO.Path]::GetFileName($archive);sha256=$digest;createdAt=[DateTime]::UtcNow.ToString('o')}|
 ConvertTo-Json|Set-Content -LiteralPath ($archive+'.json') -Encoding utf8
Write-Output "Backup archive and SHA-256 manifest created: $archive"
