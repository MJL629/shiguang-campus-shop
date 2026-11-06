# AI-assisted launcher. All generated data stays under the project directory.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
& python (Join-Path $PSScriptRoot 'local.py') start
if ($LASTEXITCODE -ne 0) { throw 'Startup failed. See the message above and .local logs.' }
