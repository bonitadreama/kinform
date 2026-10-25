$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
& "$root\gradlew.bat" -p $root :checks:LiveProtocolTest
if ($LASTEXITCODE -ne 0) { throw 'Gradle task failed' }
