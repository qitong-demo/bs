# 新就业群体友好驿站服务与诉求响应平台

基于 Spring Boot 3 + Vue 3 的毕业设计项目：面向外卖骑手、快递员、网约车司机等新就业群体，提供
友好驿站查询、服务预约、诉求闭环处理、政策资讯、活动报名、服务评价与后台统计分析能力。

平台是**面向全国的多城市平台**（不是杭州专属）：驿站名录按城市归属，顶栏可切换城市，
首次进入时用浏览器定位判断用户所在城市；不同城市对同类驿站的叫法不同（如杭州「小哥驿站 / 城管驿站」、
北京「工会驿站 / 暖新驿站」、上海「户外职工爱心接力站」），系统按来源原文记录名称与类型。

> 数据说明：**驿站名录已接入多城市公开来源**，共 **1344 条、覆盖 7 个城市**
> （杭州 267、北京 550、上海 36、深圳 117、广州 28、成都 172、武汉 174）。
> 来源、发布日期与逐条核对记录见 [docs/驿站数据来源.md](docs/驿站数据来源.md)；
> 每条记录携带 `data_status` 说明可核对程度（`VERIFIED` / `PARTIAL` / `MANUAL`）。
> **7 个城市的公开来源都没有提供点位坐标**，坐标是用**高德地图地理编码派生**的（1247/1344，93%），
> 以 `geocode_source` / `geocode_at` 单独记录，**不参与 `data_status` 判定**，`data_note` 中会写明「非来源给出」；
> 坐标为派生值，**误差可达数百米，不能当作精确位置**。
> **营业时间以高德数据为准**（高德提供 386 条，其中 227 条覆盖了来源原值；高德没有的 760 条回退来源文本，
> 合并后**共 1146 条有营业时间**）；**联系电话为来源优先、高德补空**（共 408 条，公开来源完全不提供电话）。
> 来源原值与覆盖情况以 `business_source` / `business_synced_at` 记录，并在 `data_note` 中留痕。
> **预约时段必须落在驿站营业时间内**，超出会返回 400 与中文提示（营业时间缺失时不拦截）。
> 界面上的**营业状态按当前时间实时判断**，分「营业中 / 已打烊 / 营业时间待核验」——
> **没有营业时间时显示「营业时间待核验」，绝不显示「营业中」**。
> 高德**不提供**「服务设施」与「实时营业状态」两类字段，因此服务项目仍只能来自公开来源文本。
> 演示账号、演示活动以及预约、诉求、报名、评价等运行时数据仍为演示内容，
> 统计指标是"对系统内已记录数据"的实时聚合，**不代表真实业务成效**。
> 详见 [AUDIT_AND_EVIDENCE.md](AUDIT_AND_EVIDENCE.md) 与 [docs/系统使用说明.md](docs/系统使用说明.md)。

## 目录结构

```text
backend/                 Spring Boot 后端（Java 17 / Maven）
  src/main/java/...      控制器、业务服务（内存实现 + MySQL 实现双 Profile）、实体与 Mapper
  src/main/resources/db  schema.sql 建表；stations.sql 多城市驿站名录（生成文件，含 city 表）；
                         stations.json 内存模式共用的同一份数据；data.sql 演示账号与活动
  src/test/java/...      单元测试与端到端接口集成测试
frontend/                Vue 3 + Vite + Element Plus 前端
  src/api/               按业务域拆分的接口封装（含 city.js）
  src/components/        公共组件（StationMap.vue：高德地图 JS API）
  src/stores/            Pinia 状态（user.js：用户城市与定位判断）
  src/views/             用户端模块、管理端工作台（src/views/admin/）
  src/**/*.spec.js       Vitest 测试
scripts/                 初始化、采集与生成、地理编码、验收与导出脚本
data/                    高德地理编码缓存（入仓，重跑生成器不会再次调用接口）
docs/                    接口文档、测试用例、使用说明、驿站数据来源、性能报告
outputs/screenshots/     浏览器走查自动生成的页面截图
```

## 技术栈

| 层次 | 选型 |
| --- | --- |
| 后端 | Spring Boot 3.3.5、Java 17、MyBatis-Plus 3.5.7、JJWT 0.12.6、springdoc-openapi 2.6.0 |
| 数据库 | MySQL 8.0（另提供不依赖数据库的内存演示模式） |
| 前端 | Vue 3.5、Vite 6、Element Plus 2.11、Pinia、Vue Router、ECharts 5、高德地图 JS API 2.0（GCJ-02 坐标，与地理编码结果一致） |
| 外部服务 | 高德地图 Web 服务（地理编码 / 逆地理编码 / POI 搜索），仅在后端与脚本调用 |
| 测试 | JUnit 5 + MockMvc、Vitest + @vue/test-utils、Playwright（浏览器走查）、PowerShell 接口回归脚本 |

## 高德地图配置（可跳过）

地图与坐标回填依赖高德 Key。**未配置时系统仍可正常运行**：定位接口返回降级结果、前端回退平台默认城市、地图显示降级提示，其余功能不受影响。

| Key | 用途 | 放置位置 |
| --- | --- | --- |
| Web **服务** Key | 地理编码 / 逆地理编码 / POI 搜索（坐标回填、营业信息同步、城市定位） | 后端环境变量 `AMAP_WEB_KEY`，**只在后端与脚本使用，绝不进前端** |
| Web **端**（JS API）Key | 浏览器端地图渲染 | `frontend/.env.local` 的 `VITE_AMAP_JS_KEY`（已 gitignore） |
| 安全密钥 | JSAPI 2.0 必需 | `frontend/.env.local` 的 `VITE_AMAP_JS_SECRET`（已 gitignore） |

```powershell
# 后端：注入 Web 服务 Key（定位接口与地理编码脚本用）
$env:AMAP_WEB_KEY = "你的高德 Web 服务 Key"
java -jar target/platform-0.0.1-SNAPSHOT.jar --spring.profiles.active=mysql

# 前端：复制示例文件后填入 JS API Key 与安全密钥（.env.local 不会入库）
copy frontend\.env.example frontend\.env.local

# 回填坐标（增量；--refresh 为全量重跑；结果写入 data/station-geocode.json 并已入仓）
node scripts/geocode-stations.mjs

# 同步营业时间与电话（营业时间以高德为准、高德没有时回退来源文本；电话来源优先、高德补空）
node scripts/sync-station-business.mjs
```

## 快速开始

### 一、默认演示模式（内存，无需 MySQL）

```powershell
cd backend
mvn -q -DskipTests package
java -jar target/platform-0.0.1-SNAPSHOT.jar          # http://127.0.0.1:8080

cd ..\frontend
npm install
npm run dev                                            # http://127.0.0.1:5174
```

内存模式下所有业务数据保存在服务端内存，重启后清空，适合快速演示与前端联调。

### 二、MySQL 模式

```powershell
# 1. 初始化数据库（-Reset 会先删除同名库，脚本已显式使用 utf8mb4，避免中文乱码）
powershell -ExecutionPolicy Bypass -File .\scripts\init-mysql.ps1 -User root -Password 你的密码 -Reset

# 2. 启动后端（mysql profile）
cd backend
$env:MYSQL_USER = "root"; $env:MYSQL_PASSWORD = "你的密码"
mvn -q -DskipTests package
java -jar target/platform-0.0.1-SNAPSHOT.jar --spring.profiles.active=mysql

# 3. 前端开发模式（Vite 已配置 /api 代理到 8080）
cd ..\frontend
npm run dev
```

### 三、生产形态（构建产物 + 静态服务 + 生产配置）

```powershell
cd frontend
npm ci
npm run build                                          # 产物在 frontend/dist

cd ..\backend
mvn -q -DskipTests package

# 生产配置与 mysql 配置叠加使用；JWT_SECRET 与数据库口令必须显式提供
$env:DB_HOST = "localhost"; $env:MYSQL_USER = "root"; $env:MYSQL_PASSWORD = "你的密码"
$env:JWT_SECRET = "请替换为不少于 32 字节的随机密钥"
java -jar target/platform-0.0.1-SNAPSHOT.jar --spring.profiles.active=mysql,prod

# 静态站点 + /api 反向代理（也可用 Nginx 等替代）
node scripts\serve-dist.mjs --port 5174 --api http://127.0.0.1:8080
```

也可以使用容器编排：

```powershell
$env:JWT_SECRET = "请替换为不少于 32 字节的随机密钥"
$env:MYSQL_ROOT_PASSWORD = "请替换为强密码"
docker compose up -d --build
```

`docker-compose.yml` 不包含任何演示默认密码，缺少必填环境变量时会直接拒绝启动。

### 演示账号

| 账号 | 密码 | 角色 | 可用范围 |
| --- | --- | --- | --- |
| `rider_xiaowang` | `password` | 普通用户 | 驿站查询、预约、诉求、活动、评价、个人中心 |
| `station_admin_01` | `password` | 驿站管理员 | 预约审核、诉求处理、活动管理、报表导出 |
| `platform_admin` | `password` | 平台管理员 | 全部管理端能力，含驿站、服务项目、资讯公告、账号与角色 |

生产配置下不会自动创建这些演示账号，需由 `data.sql` 或自行注册产生。

## 功能范围

**用户端（11 个模块）**：首页工作台、驿站查询（区域/服务/关键字筛选、收藏、详情）、服务预约
（按驿站加载服务项目、提交、查询记录、取消）、诉求反馈（提交、匿名）、我的诉求（状态筛选、
处理记录时间线、办结后评价）、活动报名（剩余名额校验、重复报名拦截）、政策资讯（资讯与公告
分页）、服务评价（四个维度评分、历史评价与平均分）、消息中心、数据统计、个人中心。

**管理端（11 个标签页）**：运营概览、预约审核、诉求处理（分派/处理中/办结/关闭 + 处理记录）、
驿站管理、服务项目管理、资讯公告管理、活动管理、报名记录、账号管理、角色权限、报表导出。
标签页按角色显示，后端接口另有 `@RequireRole` 校验。

## 接口

- 接口总数 **73** 个，分为 14 个业务分组，完整清单见 [docs/接口文档.md](docs/接口文档.md)
- 在线调试（需在后端开启）：`http://127.0.0.1:8080/swagger-ui.html`
- OpenAPI 描述：`http://127.0.0.1:8080/v3/api-docs`，导出文件见 [docs/openapi.json](docs/openapi.json)

统一返回结构 `{ "code": 200, "message": "success", "data": ... }`，分页结构
`{ "current": 1, "size": 10, "total": 0, "records": [] }`；鉴权使用
`Authorization: Bearer <token>`，未登录返回 401，越权返回 403。

## 验证与测试

| 验证项 | 命令 | 规模 |
| --- | --- | --- |
| 后端单元与集成测试 | `cd backend; mvn test` | 95 项（单元 69 + MockMvc 端到端集成 26，12 个测试类） |
| 前端自动化测试 | `cd frontend; npm test` | 53 项（接口契约、HTTP 封装、组件、营业状态解析、管理端工具） |
| 接口回归 | `powershell -ExecutionPolicy Bypass -File .\scripts\api-test.ps1` | 118 项断言，覆盖 14 组场景 |
| 浏览器走查与截图 | `node scripts\browser-check.mjs` | 38 项页面检查（含地图点位），输出 24 张截图到 `outputs/screenshots/` |
| 性能与并发 | `node scripts\perf-test.mjs` | 6 个接口并发压测 + 4 项并发正确性校验 |
| 接口文档导出 | `node scripts\export-openapi.mjs` | 生成 docs/openapi.json 与 docs/接口文档.md |
| 驿站数据采集与生成 | `node scripts\parse-station-sources.mjs` + `node scripts\generate-station-data.mjs` | 7 个城市 1344 条公开来源名录 → stations.json / stations.sql / 数据来源说明 |
| 坐标回填（高德） | `$env:AMAP_WEB_KEY="..."; node scripts\geocode-stations.mjs` | 1247/1344 条拿到可信坐标 → data/station-geocode.json |
| 营业信息同步（高德） | `$env:AMAP_WEB_KEY="..."; node scripts\sync-station-business.mjs` | 营业时间 386 条（高德为准）、电话 408 条 → data/station-business.json |

浏览器走查需要 Chrome 以调试端口启动，例如：

```powershell
& "C:\Program Files\Google\Chrome\Application\chrome.exe" --headless=new --no-sandbox `
  --remote-debugging-port=9222 --user-data-dir="$PWD\tmp\chrome-cdp" --window-size=1440,1000 about:blank
node scripts\browser-check.mjs
```

## 文档索引

- [docs/接口文档.md](docs/接口文档.md)　73 个接口的分组、参数、请求体与返回说明
- [docs/驿站数据来源.md](docs/驿站数据来源.md)　7 个城市 1344 条驿站名录的来源、区域分布、坐标口径与复现方式
- [docs/测试用例文档.md](docs/测试用例文档.md)　功能测试用例、自动化测试清单与缺陷回归记录
- [docs/系统使用说明.md](docs/系统使用说明.md)　角色权限、启动步骤、分角色操作指引与常见问题
- [docs/性能与并发测试报告.md](docs/性能与并发测试报告.md)　并发压测结果与并发正确性结论
- [docs/答辩演示提纲.md](docs/答辩演示提纲.md)　演示流程、技术要点与预期提问
- [DEVELOPMENT_PROGRESS.md](DEVELOPMENT_PROGRESS.md)　十阶段开发进度与完成度记录
- [AUDIT_AND_EVIDENCE.md](AUDIT_AND_EVIDENCE.md)　数据来源、可复核边界与尚不能作出的结论

## 数据说明与边界

- 驿站名录来自 **7 个城市**公开发布的权威来源，共 **1344 条**，逐条记录来源名称、链接、发布日期与核对日期；
  `data_status` 区分「可逐条核对」与「部分字段为来源的批次级描述」。
  **这不等于已实地核实**，也不等于这些城市驿站的完整清单。
- **来源都没有提供点位坐标**：7 个城市的公开来源均无经纬度，坐标是用**高德地图地理编码派生**的
  （1247/1344 条，93%）。派生的坐标以 `geocode_source` / `geocode_at` 单独记录、**不参与 `data_status` 判定**，
  并在 `data_note` 中写明「非来源给出」。**坐标为派生值，误差可达数百米，不能当作精确位置**；
  需要精确定位时请用高德地图搜索驿站名称或走官方渠道。
- **坐标可信度复核**：仅按名称搜 POI 的「有返回率」是 100%，但会把不同驿站匹配到同一个点；
  系统加入「区县一致性 + 名称相似度 + 地址相似度」三重校验，并对结果做「行政区冲突」与
  「距城市中心 > 120 公里」复核，**不合格的坐标宁可丢弃**。实测丢弃 95 条（84 条行政区冲突、11 条距离过远）、
  2 条未定位；去重后 1170 个坐标点，其中 51 个点被多条驿站共用（有些本就同址，也可能是泛化名称匹配到同一 POI）。
- **567 条记录因来源只给出名称、没有地址而未导入**（多为「最美驿站推荐名单」类公示）。
- 部分城市的名单原文是官方发布的**图片表格**（深圳 105 条、武汉 132 条经逐行读取还原），
  属于「从来源图片提取」，可信度略低于文本来源。
- 服务项目做了规范化（按顿号/逗号拆分、去掉括号补充与「等」字、超过 15 字的描述性片段不计入），
  因为服务项是预约时可选的最小单位；原始文本完整保留在 `docs/station-sources.json`。
- **44 条缺失的所属行政区**由地理编码结果补齐，并在 `data_note` 注明「由高德地图补齐，非来源给出」。
- **营业信息（营业时间 / 电话）**：来源大多不提供，因此从高德同步。
  **营业时间以高德为准**（高德 386 条，其中 227 条覆盖了来源原值；高德没有的 760 条回退来源文本，
  合并后共 **1146 条**有营业时间）；**电话为来源优先、高德补空**（408 条，公开来源完全不提供电话）。
  以 `business_source` / `business_synced_at` 记录来源，被高德覆盖的来源原值会在 `data_note` 里留痕
  （例如 `大悦城小哥驿站` 现在是高德的「10:00-22:00」，来源原文「每日 8:30-22:00」仅作记录）。
- **营业状态按当前时间实时判断**：界面分「营业中 / 已打烊 / 营业时间待核验」三态，每分钟自动更新。
  **没有营业时间时显示「营业时间待核验」，绝不显示「营业中」**。这是纯展示逻辑，
  预约时段的**校验仍以后端 `BusinessHours` 为准**。
- **预约时段必须落在驿站营业时间内**：超出会返回 400 与中文提示（如「所选时段 22:00-23:00 不在该驿站的营业时间内（10:00-22:00）」）。
  营业时间**缺失或无法解析时不拦截**（判断不了就不挡），避免因数据缺失误拒正常预约。
- **驿站查询的「只显示已启用」按服务端 `status` 字段过滤**，不是按当前时刻过滤；
  「此刻是否营业」由每行的实时状态标签表达（两者语义不同，已在界面上用 tooltip 说明）。
- **高德不提供「服务设施」与「实时营业状态」**：前者没有结构化字段（`type`/`tag` 只是「生活服务」这类粗分类），
  后者根本没有该字段；因此饮水/充电/热饭等服务项目**仍只能来自公开来源文本**，页面也已明确标注这一点。
- 点位 `status` 反映各来源发布时的开放状态，**使用前应重新核实**。
- **统计指标仍是全平台口径**，目前只有驿站查询与地图按城市过滤；首页已明确标注这一点。
- 用户定位不在已覆盖城市时，系统用高德逆地理编码识别真实城市并给出提示
  （顶栏显示「未覆盖」标签，悬停可见「您所在的城市（苏州市）暂未覆盖，已为您展示最近的上海市」），
  同时降级展示最近的已覆盖城市。
- 演示账号、演示活动以及预约、诉求、报名、评价等运行时数据为演示内容；统计口径为「系统内已记录数据」，
  不构成真实业务成效结论。
- 未做生产级安全渗透测试、未做多实例分布式并发验证，业务数据量也远小于真实规模。
- `.ps1` 脚本均带 UTF-8 BOM，以便在 Windows PowerShell 5.1 下正确读取中文。
- 前端 `dev / build / preview / test` 统一通过 `frontend/scripts/with-local-temp.mjs` 启动，
  该包装脚本会把 `TEMP/TMP/TMPDIR` 指向 `frontend/.tmp-run`。原因：部分 Windows 环境下 esbuild 在
  系统临时目录创建的中间文件会在构建过程中被占用，报 `remove ...: Access is denied` 而中断构建；
  这些文件在构建结束后仍可正常删除，属于构建瞬间的文件占用而非权限配置问题，改到项目内目录即可稳定规避。
- 高德 Key 不入仓：Web 服务 Key 通过环境变量 `AMAP_WEB_KEY` 注入，Web 端 Key 与安全密钥放在
  `frontend/.env.local`（已 gitignore）；未配置时地图、定位与预约时段校验降级但不影响其他功能。
- 营业时间与电话是**第三方数据**（来自商户认领与用户上报），可能与实际不符或过期，使用前宜再核实。
- **消息已读状态保存在本机浏览器**（localStorage），换设备或清理缓存后会重新变为未读；系统没有独立的消息表，
  消息由预约、诉求、报名记录实时派生。顶栏未读角标与消息中心读同一份数据（`stores/message.js`），两者始终一致。
- 数据库结构调整后需要重建：`schema.sql` 使用 `CREATE TABLE IF NOT EXISTS`，不会为旧库补充新增列，
  请用 `scripts/init-mysql.ps1 -Reset` 重新导入。
