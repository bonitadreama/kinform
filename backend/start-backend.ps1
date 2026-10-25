$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try { & node src/server.mjs } finally { Pop-Location }
