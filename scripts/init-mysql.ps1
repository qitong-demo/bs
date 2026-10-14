param(
  [string]$User = $env:MYSQL_USER,
  [string]$Password = $env:MYSQL_PASSWORD,
  [string]$HostName = 'localhost',
  [int]$Port = 3306,
  [string]$MysqlExe = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
  [switch]$Reset
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($User) -or [string]::IsNullOrWhiteSpace($Password)) {
  throw '请提供 MYSQL_USER 和 MYSQL_PASSWORD，或通过参数 -User/-Password 传入。'
}
if (-not (Test-Path $MysqlExe)) { throw "未找到 mysql.exe：$MysqlExe" }

$projectRoot = Split-Path -Parent $PSScriptRoot
# 执行顺序：先建表，再导入真实驿站名录（stations.sql）与政策资讯（news.sql），
# 最后导入演示账号与活动（data.sql）。
# 活动记录引用驿站编号，因此 stations.sql 必须先于 data.sql 执行。
$sqlFiles = @(
  (Join-Path $projectRoot 'backend/src/main/resources/db/schema.sql'),
  (Join-Path $projectRoot 'backend/src/main/resources/db/stations.sql'),
  (Join-Path $projectRoot 'backend/src/main/resources/db/news.sql'),
  # data.sql 必须排在 activity.sql **之前**：它用 INSERT IGNORE 硬编码了活动 id 1、2，
  # 那是驿站自办活动、也是用户唯一能报名的两条。若 activity.sql 先跑，
  # 自增 id 已占到 1~38，这两条会因主键冲突被静默跳过，且不报任何错。
  (Join-Path $projectRoot 'backend/src/main/resources/db/data.sql'),
  # 采集来的真实活动（各级工会/人社局/社区办的）：organizer_type=EXTERNAL，
  # 站内只展示并提供跳转、不接受报名。
  (Join-Path $projectRoot 'backend/src/main/resources/db/activity.sql')
)
foreach ($file in $sqlFiles) {
  if (-not (Test-Path $file)) { throw "未找到 SQL 文件：$file" }
  if ($file -match '\s') { throw "SQL 路径包含空格，mysql 客户端的 source 命令无法处理：$file" }
}

# 必须显式指定 utf8mb4：Windows 控制台默认代码页为 936，
# 否则 mysql 客户端会按 GBK 解析 UTF-8 脚本，中文写入后会变成乱码。
$commonArgs = @(
  "--host=$HostName",
  "--port=$Port",
  "--user=$User",
  "--protocol=tcp",
  "--default-character-set=utf8mb4"
)

$env:MYSQL_PWD = $Password

if ($Reset) {
  & $MysqlExe @commonArgs --batch --execute='DROP DATABASE IF EXISTS friendly_station;'
  if ($LASTEXITCODE -ne 0) { throw "删除数据库失败，退出码：$LASTEXITCODE" }
  Write-Output '已删除旧数据库 friendly_station。'
}

foreach ($file in $sqlFiles) {
  $sourcePath = $file.Replace('\', '/')
  & $MysqlExe @commonArgs --batch --execute="source $sourcePath"
  if ($LASTEXITCODE -ne 0) { throw "执行失败：$sourcePath（退出码 $LASTEXITCODE）" }
  Write-Output "已执行：$sourcePath"
}

$charset = & $MysqlExe @commonArgs --batch --skip-column-names --execute='USE friendly_station; SELECT @@character_set_database;'
$stationCount = & $MysqlExe @commonArgs --batch --skip-column-names --execute='USE friendly_station; SELECT COUNT(*) FROM station;'
$serviceCount = & $MysqlExe @commonArgs --batch --skip-column-names --execute='USE friendly_station; SELECT COUNT(*) FROM station_service;'
$stationName = & $MysqlExe @commonArgs --batch --skip-column-names --execute='USE friendly_station; SELECT CONCAT(station_name, '' / '', area, '' / '', IFNULL(station_type, ''未分类'')) FROM station WHERE id = 1;'
$stationSource = & $MysqlExe @commonArgs --batch --skip-column-names --execute='USE friendly_station; SELECT CONCAT(IFNULL(source_name, ''无''), '' / 核对日期 '', IFNULL(verified_at, ''无'')) FROM station WHERE id = 1;'
$activityTitle = & $MysqlExe @commonArgs --batch --skip-column-names --execute='USE friendly_station; SELECT title FROM activity WHERE id = 1;'

Write-Output ''
Write-Output 'MySQL schema 与驿站名录、演示数据初始化完成。'
Write-Output "数据库字符集：$charset"
Write-Output "驿站数量：$stationCount 条，服务项目：$serviceCount 条"
Write-Output "校验 1 号驿站：$stationName"
Write-Output "校验数据来源：$stationSource"
Write-Output "校验 1 号活动标题：$activityTitle"
Write-Output '提示：上面几行若出现乱码，说明客户端字符集不匹配，请确认使用了 --default-character-set=utf8mb4。'
