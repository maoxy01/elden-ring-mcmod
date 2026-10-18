# 装备 2D 的实机人工观感（隔离目录，无夹具接管操作）
# 游戏目录 build/equipmentView/，日志 build/equipmentView/logs/latest.log
# 首次运行会把创造模式测试存档复制成 saves/equipment-view；之后不再覆盖。
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $workspace
& (Join-Path $workspace 'gradlew.bat') runClient -PequipmentView --offline --no-daemon '-Dorg.gradle.java.home=D:/JDK21'
exit $LASTEXITCODE
