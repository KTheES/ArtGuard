param([string]$Python = "python", [switch]$Dev)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot)
& $Python -m venv .venv
if ($LASTEXITCODE -ne 0) { throw 'Python 3.12+ virtual environment creation failed.' }
& ./.venv/Scripts/python.exe -m pip install -r requirements-cpu.txt
if ($LASTEXITCODE -ne 0) { throw 'CPU PyTorch installation failed.' }
$requirements = if ($Dev) { 'requirements-dev.txt' } else { 'requirements.txt' }
& ./.venv/Scripts/python.exe -m pip install -r $requirements
if ($LASTEXITCODE -ne 0) { throw 'Service dependency installation failed.' }
& ./.venv/Scripts/python.exe -m app.download_model
if ($LASTEXITCODE -ne 0) { throw 'Pinned model download failed.' }
