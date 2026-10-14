$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $workspace
& (Join-Path $workspace 'gradlew.bat') runClient -PmanualSkills --offline --no-daemon '-Dorg.gradle.java.home=D:/JDK21'
exit $LASTEXITCODE
