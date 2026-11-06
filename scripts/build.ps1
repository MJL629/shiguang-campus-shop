$ErrorActionPreference = 'Stop'
& python (Join-Path $PSScriptRoot 'local.py') build
if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
