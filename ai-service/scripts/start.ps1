$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot)
if (-not (Test-Path ./.venv/Scripts/python.exe)) { throw 'Run scripts/setup.ps1 first.' }
if (-not $env:AI_API_KEY -or $env:AI_API_KEY.Length -lt 32) { throw 'Set a shared AI_API_KEY with at least 32 characters for the backend and AI service.' }
& ./.venv/Scripts/python.exe -m uvicorn app.main:create_app --factory --host 127.0.0.1 --port 8001 --workers 1 --no-access-log
