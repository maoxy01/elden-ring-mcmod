[CmdletBinding()]
param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$java = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { (Get-Command java -ErrorAction Stop).Source }
$version = (& $java -version 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0 -or $version -notmatch 'version "21\.') { throw 'This server requires Java 21. Pass -JavaHome with the JDK 21 directory.' }
& (Join-Path $PSScriptRoot 'Assert-TestBudget.ps1') -Mode server
Push-Location $PSScriptRoot
try {
    & $java -Xms256m -Xmx1536m -XX:ActiveProcessorCount=4 '@libraries/net/neoforged/neoforge/21.1.219/win_args.txt' --nogui
    if ($LASTEXITCODE -ne 0) { throw "Server exited with code $LASTEXITCODE; inspect logs/latest.log" }
} finally { Pop-Location }
