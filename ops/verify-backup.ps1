[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$Archive)
$ErrorActionPreference='Stop'
Get-Command pg_restore -ErrorAction Stop|Out-Null
$resolved=(Resolve-Path -LiteralPath $Archive).Path
$manifest=Get-Content -LiteralPath ($resolved+'.json') -Raw|ConvertFrom-Json
if($manifest.file -ne [IO.Path]::GetFileName($resolved)){throw 'Manifest filename mismatch'}
if((Get-FileHash -LiteralPath $resolved -Algorithm SHA256).Hash -ne $manifest.sha256){throw 'Backup hash mismatch'}
& pg_restore --list $resolved | Out-Null
if($LASTEXITCODE -ne 0){throw 'Invalid backup archive'}
Write-Output 'Hash and archive directory verified. A restore drill is still required.'
