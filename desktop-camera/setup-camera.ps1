param([string]$Python = 'python')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$environmentDirectory = Join-Path $projectRoot '.camera-venv'
& $Python -c 'import sys; assert (3, 10) <= sys.version_info[:2] <= (3, 12), "Use Python 3.10, 3.11 or 3.12"'
if ($LASTEXITCODE -ne 0) { throw 'Python 3.10–3.12 is required.' }
& $Python -m venv $environmentDirectory
if ($LASTEXITCODE -ne 0) { throw 'Could not create the camera environment.' }
$cameraPython = Join-Path $environmentDirectory 'Scripts/python.exe'
& $cameraPython -m pip install -r (Join-Path $PSScriptRoot 'requirements.txt')
if ($LASTEXITCODE -ne 0) { throw 'Camera dependency installation failed.' }
Write-Output 'Camera environment is ready. Run the desktop application from the project root.'
