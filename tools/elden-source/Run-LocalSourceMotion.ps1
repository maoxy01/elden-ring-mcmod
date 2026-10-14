$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$profile = Join-Path $workspace 'build/elden-source/retargeted/profile.json'
if (-not (Test-Path -LiteralPath $profile -PathType Leaf)) {
    throw 'Generate and validate the local motion profile before starting Minecraft.'
}
Set-Location -LiteralPath $workspace
& (Join-Path $workspace 'gradlew.bat') runClient -PmanualSkills -PsourceMotion --offline --no-daemon '-Dorg.gradle.java.home=D:/JDK21'
exit $LASTEXITCODE
