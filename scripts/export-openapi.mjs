/**
 * 接口文档导出：从运行中的后端拉取 OpenAPI 描述，生成 docs/openapi.json 与 docs/接口文档.md。
 *
 * 用法：node scripts/export-openapi.mjs [--api http://127.0.0.1:8080]
 * 前置条件：后端已启动（任意 profile 均可）。
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const docsDir = path.join(projectRoot, "docs");

function readArg(name, fallback) {
  const index = process.argv.indexOf(`--${name}`);
  return index >= 0 && process.argv[index + 1] ? process.argv[index + 1] : fallback;
}

const apiBase = readArg("api", "http://127.0.0.1:8080").replace(/\/$/, "");

/** springdoc 默认用控制器类名做 tag，这里映射为中文分组名。 */
const TAG_LABELS = {
  "auth-controller": "认证与登录",
  "user-controller": "个人中心与账号",
  "city-controller": "城市",
  "station-controller": "友好驿站",
  "station-service-controller": "驿站服务项目",
  "appointment-controller": "服务预约",
  "appeal-controller": "诉求反馈与处理",
  "activity-controller": "活动与报名",
  "activity-admin-controller": "活动管理（后台）",
  "news-controller": "政策资讯",
  "notice-controller": "通知公告",
  "evaluation-controller": "服务评价",
  "admin-controller": "平台管理（账号与角色）",
  "dashboard-controller": "统计分析与报表"
};

const METHOD_ORDER = ["get", "post", "put", "delete", "patch"];

/** 少数接口需要单独说明，其余按路径后缀推断。 */
const SUMMARY_OVERRIDES = {
  "post /api/auth/login": "登录并获取 JWT 凭证",
  "post /api/auth/register": "注册普通用户账号",
  "post /api/auth/refresh": "刷新登录凭证",
  "get /api/user/info": "查询当前登录账号资料",
  "put /api/user/info": "修改当前登录账号资料",
  "get /api/user/page": "查询账号列表（简化版，供账号管理使用）",
  "put /api/user/{id}/status": "启用或停用账号",
  "get /api/admin/overview": "查询管理端工作流概览",
  "get /api/admin/users": "分页查询账号（支持角色与关键字过滤）",
  "get /api/admin/roles": "查询内置角色与账号数量",
  "put /api/admin/users/{id}/roles": "设置账号角色",
  "get /api/stat/overview": "查询平台统计概览（实时聚合）",
  "get /api/stat/appeal/month": "查询近 6 个自然月业务趋势",
  "get /api/stat/service/ranking": "查询服务项目预约排行",
  "get /api/stat/export/{type}": "导出统计报表（CSV）",
  "get /api/station/page": "分页查询友好驿站（前台）",
  "get /api/station/nearby": "查询附近友好驿站（与分页查询同源）",
  "get /api/station/admin/page": "分页查询友好驿站（后台，含停用与已删除）",
  "get /api/station/areas": "查询区域分布统计（前台筛选下拉，可按城市）",
  "get /api/city/list": "查询平台覆盖的城市列表（含各城市驿站数）",
  "get /api/city/nearest": "按经纬度定位到距离最近的城市",
  "get /api/city/locate": "按经纬度定位用户所在城市（高德逆地理编码，含是否已覆盖）",
  "get /api/station/{id}": "查询驿站详情",
  "post /api/station": "新增驿站",
  "put /api/station/{id}": "修改驿站",
  "put /api/station/{id}/status": "修改驿站营业状态",
  "delete /api/station/{id}": "逻辑删除驿站",
  "get /api/station/service/page": "分页查询驿站服务项目",
  "get /api/station/service/list": "查询指定驿站的服务项目列表",
  "post /api/station/service": "新增服务项目",
  "put /api/station/service/{id}": "修改服务项目",
  "put /api/station/service/{id}/status": "启用或停用服务项目",
  "delete /api/station/service/{id}": "删除服务项目",
  "post /api/appointment": "提交服务预约",
  "get /api/appointment/my": "查询本人预约记录",
  "put /api/appointment/{id}/cancel": "取消本人预约",
  "get /api/appointment/admin/page": "分页查询预约（后台）",
  "put /api/appointment/audit": "审核预约（确认或拒绝）",
  "post /api/appeal": "提交诉求",
  "get /api/appeal/my": "查询本人诉求及处理记录",
  "get /api/appeal/admin/page": "分页查询诉求（后台，匿名诉求隐藏提交人）",
  "get /api/appeal/{id}": "查询诉求详情与处理记录",
  "get /api/appeal/{id}/records": "查询诉求处理记录",
  "put /api/appeal/assign": "分派诉求处理人",
  "put /api/appeal/handle": "处理诉求（处理中、办结或关闭）",
  "post /api/appeal/evaluate": "评价已办结诉求",
  "get /api/activity/page": "分页查询已发布活动",
  "get /api/activity/my": "查询本人活动报名",
  "post /api/activity/signup": "报名活动（校验名额与重复报名）",
  "get /api/activity/admin/signups": "查询活动报名记录（后台）",
  "get /api/activity/admin/page": "分页查询活动（后台）",
  "post /api/activity/admin": "新增活动",
  "put /api/activity/admin/{id}": "修改活动",
  "delete /api/activity/admin/{id}": "删除活动",
  "get /api/news/page": "分页查询已发布资讯",
  "get /api/news/admin/page": "分页查询资讯（后台）",
  "get /api/news/{id}": "查询资讯详情",
  "post /api/news": "发布资讯",
  "put /api/news/{id}": "修改资讯",
  "delete /api/news/{id}": "删除资讯",
  "get /api/notice/page": "分页查询已发布公告",
  "get /api/notice/admin/page": "分页查询公告（后台）",
  "get /api/notice/{id}": "查询公告详情",
  "post /api/notice": "发布公告",
  "put /api/notice/{id}": "修改公告",
  "delete /api/notice/{id}": "删除公告",
  "post /api/evaluation/station": "提交驿站服务评价",
  "get /api/evaluation/station/page": "分页查询驿站评价",
  "get /api/evaluation/station/summary": "查询驿站评价汇总（各维度平均分）",
  "get /api/evaluation/my": "查询本人提交的评价"
};

/** 写接口使用 Map 接收参数，springdoc 无法展开，这里显式列出字段含义。 */
const BODY_FIELDS = {
  "post /api/auth/login": [["username", "是", "登录账号"], ["password", "是", "登录密码"]],
  "post /api/auth/register": [["username", "是", "登录账号"], ["phone", "是", "手机号"], ["password", "是", "登录密码"]],
  "post /api/auth/refresh": [["token", "是", "待刷新的 token"]],
  "put /api/user/info": [["realName", "否", "姓名"], ["phone", "否", "手机号"], ["jobType", "否", "职业类型"], ["area", "否", "所属区域"]],
  "put /api/user/{id}/status": [["status", "是", "正常 或 停用"]],
  "put /api/admin/users/{id}": [["realName", "否", "姓名"], ["phone", "否", "手机号"], ["jobType", "否", "职业类型"], ["area", "否", "所属区域"]],
  "put /api/admin/users/{id}/status": [["status", "是", "正常 或 停用"]],
  "put /api/admin/users/{id}/roles": [["roleCodes", "是", "角色编码数组，至少一个，如 [\"STATION_ADMIN\"]"]],
  "post /api/appointment": [["stationId", "是", "驿站编号"], ["serviceId", "否", "服务项目编号"], ["date", "是", "预约日期 yyyy-MM-dd"], ["slot", "否", "预约时段，如 09:00-11:00"]],
  "put /api/appointment/audit": [["id", "是", "预约编号"], ["status", "是", "CONFIRMED 或 REJECTED"], ["reason", "否", "审核说明"]],
  "post /api/appeal": [["title", "是", "诉求标题"], ["type", "否", "驿站服务/权益保障/设施建议/其他问题"], ["area", "否", "所属区域"], ["content", "否", "具体内容"], ["anonymous", "否", "是否匿名，默认 false"]],
  "put /api/appeal/assign": [["id", "是", "诉求编号"], ["content", "否", "分派说明"]],
  "put /api/appeal/handle": [["id", "是", "诉求编号"], ["status", "是", "PROCESSING / RESOLVED / CLOSED"], ["content", "否", "处理说明，办结时建议必填"]],
  "post /api/appeal/evaluate": [["appealId", "是", "诉求编号"], ["score", "是", "评分 1-5"], ["content", "否", "评价内容"]],
  "post /api/activity/signup": [["activityId", "是", "活动编号"], ["phone", "否", "联系电话"]],
  "post /api/activity/admin": [["title", "是", "活动标题"], ["stationId", "否", "所属驿站编号"], ["activityTime", "否", "活动时间 yyyy-MM-dd HH:mm:ss"], ["address", "否", "活动地点"], ["maxCount", "否", "名额上限，默认 50 且必须大于 0"], ["content", "否", "活动说明"], ["status", "否", "PUBLISHED 或 DRAFT"]],
  "put /api/activity/admin/{id}": [["title", "否", "活动标题"], ["stationId", "否", "所属驿站编号"], ["activityTime", "否", "活动时间"], ["address", "否", "活动地点"], ["maxCount", "否", "名额上限"], ["content", "否", "活动说明"], ["status", "否", "PUBLISHED 或 DRAFT"]],
  "post /api/news": [["title", "是", "资讯标题"], ["category", "否", "分类，默认 政策"], ["content", "否", "正文"], ["status", "否", "PUBLISHED 或 DRAFT"]],
  "put /api/news/{id}": [["title", "否", "资讯标题"], ["category", "否", "分类"], ["content", "否", "正文"], ["status", "否", "PUBLISHED 或 DRAFT"]],
  "post /api/notice": [["title", "是", "公告标题"], ["noticeType", "否", "类型，默认 通知"], ["targetType", "否", "投放对象，默认 ALL"], ["content", "否", "正文"], ["status", "否", "PUBLISHED 或 DRAFT"]],
  "put /api/notice/{id}": [["title", "否", "公告标题"], ["noticeType", "否", "类型"], ["targetType", "否", "投放对象"], ["content", "否", "正文"], ["status", "否", "PUBLISHED 或 DRAFT"]],
  "post /api/evaluation/station": [["stationId", "是", "驿站编号"], ["score", "是", "综合评分 1-5"], ["serviceScore", "否", "服务评分，默认取综合评分"], ["environmentScore", "否", "环境评分"], ["facilityScore", "否", "设施评分"], ["content", "否", "评价内容"]]
};

function bodyFieldsTable(method, endpoint) {
  const fields = BODY_FIELDS[`${method} ${endpoint}`];
  if (!fields) return "";
  const lines = ["| 字段 | 必填 | 说明 |", "| --- | --- | --- |"];
  for (const [name, required, description] of fields) {
    lines.push(`| ${name} | ${required} | ${description} |`);
  }
  return lines.join("\n");
}


const SUFFIX_RULES = [
  [/\/admin\/page$/, "分页查询（后台）"],
  [/\/page$/, "分页查询"],
  [/\/my$/, "查询本人记录"],
  [/\/status$/, "修改状态"],
  [/\/records$/, "查询处理记录"],
  [/\{id\}$/, null]
];

function summarize(method, endpoint) {
  const key = `${method} ${endpoint}`;
  if (SUMMARY_OVERRIDES[key]) return SUMMARY_OVERRIDES[key];
  for (const [pattern, label] of SUFFIX_RULES) {
    if (label && pattern.test(endpoint)) return label;
  }
  if (/\{id\}$/.test(endpoint)) {
    if (method === "get") return "查询详情";
    if (method === "put") return "修改记录";
    if (method === "delete") return "删除记录";
  }
  if (method === "post") return "新增记录";
  if (method === "put") return "修改记录";
  if (method === "delete") return "删除记录";
  return "查询";
}

function tagLabel(tag) {
  return TAG_LABELS[tag] || tag;
}

/** 把 springdoc 生成的 ApiResponseXxx 泛型名翻译成可读的业务数据类型。 */
function describeData(schemaName) {
  if (!schemaName) return "业务数据";
  const raw = schemaName.replace(/^ApiResponse/, "");
  if (!raw) return "业务数据";
  if (raw === "Void") return "无（null）";
  if (raw.startsWith("PageResult")) return `分页结果，\`records\` 为 ${raw.slice("PageResult".length)} 列表`;
  if (raw.startsWith("List")) return `${raw.slice(4) || "对象"} 列表`;
  if (raw === "MapStringObject") return "键值对象（字段名见本接口说明）";
  return raw;
}

function resolveRef(spec, schema, depth = 0) {
  if (!schema || depth > 6) return schema;
  if (schema.$ref) {
    const name = schema.$ref.split("/").pop();
    return { ...(spec.components?.schemas?.[name] || {}), __name: name };
  }
  return schema;
}

function typeOf(spec, schema) {
  const resolved = resolveRef(spec, schema);
  if (!resolved) return "—";
  if (resolved.__name) return resolved.__name;
  if (resolved.type === "array") return `${typeOf(spec, resolved.items)}[]`;
  if (resolved.type) return resolved.type;
  if (resolved.oneOf) return resolved.oneOf.map((item) => typeOf(spec, item)).join(" | ");
  return "object";
}

function schemaTable(spec, schema) {
  const resolved = resolveRef(spec, schema);
  if (!resolved || !resolved.properties) return "";
  const required = new Set(resolved.required || []);
  const lines = ["| 字段 | 类型 | 必填 | 说明 |", "| --- | --- | --- | --- |"];
  for (const [name, value] of Object.entries(resolved.properties)) {
    const description = (value.description || "").replace(/\|/g, "\\|").replace(/\n/g, " ");
    lines.push(`| ${name} | ${typeOf(spec, value)} | ${required.has(name) ? "是" : "否"} | ${description || "—"} |`);
  }
  return lines.join("\n");
}

function parametersTable(parameters) {
  if (!parameters || parameters.length === 0) return "";
  const lines = ["| 参数 | 位置 | 类型 | 必填 | 说明 |", "| --- | --- | --- | --- | --- |"];
  for (const parameter of parameters) {
    const description = (parameter.description || "").replace(/\|/g, "\\|").replace(/\n/g, " ");
    const type = parameter.schema?.type || (parameter.schema?.$ref ? parameter.schema.$ref.split("/").pop() : "string");
    lines.push(
      `| ${parameter.name} | ${parameter.in} | ${type} | ${parameter.required ? "是" : "否"} | ${description || "—"} |`
    );
  }
  return lines.join("\n");
}

async function main() {
  const url = `${apiBase}/v3/api-docs`;
  let spec;
  try {
    const response = await fetch(url);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    spec = await response.json();
  } catch (error) {
    console.error(`无法获取 OpenAPI 描述：${url}\n${error.message}\n请确认后端已启动。`);
    process.exit(1);
  }

  fs.mkdirSync(docsDir, { recursive: true });
  fs.writeFileSync(path.join(docsDir, "openapi.json"), `${JSON.stringify(spec, null, 2)}\n`, "utf8");

  const grouped = new Map();
  for (const [endpoint, operations] of Object.entries(spec.paths || {})) {
    for (const [method, operation] of Object.entries(operations)) {
      if (!METHOD_ORDER.includes(method)) continue;
      const tag = (operation.tags && operation.tags[0]) || "其他";
      if (!grouped.has(tag)) grouped.set(tag, []);
      grouped.get(tag).push({ endpoint, method, operation });
    }
  }

  const lines = [];
  lines.push("# 接口文档");
  lines.push("");
  lines.push(`本文档由 \`scripts/export-openapi.mjs\` 从运行中的后端自动生成，源描述见 [openapi.json](openapi.json)。`);
  lines.push("");
  lines.push(`- 接口总数：**${[...grouped.values()].reduce((sum, list) => sum + list.length, 0)}**`);
  lines.push(`- 分组数量：**${grouped.size}**`);
  lines.push(`- 在线调试：后端启动后访问 \`${apiBase}/swagger-ui.html\``);
  lines.push(`- OpenAPI 描述：\`${apiBase}/v3/api-docs\``);
  lines.push("");
  lines.push("## 通用约定");
  lines.push("");
  lines.push("统一返回结构：");
  lines.push("");
  lines.push("```json");
  lines.push('{ "code": 200, "message": "success", "data": {} }');
  lines.push("```");
  lines.push("");
  lines.push("分页返回结构（`data` 部分）：");
  lines.push("");
  lines.push("```json");
  lines.push('{ "current": 1, "size": 10, "total": 0, "records": [] }');
  lines.push("```");
  lines.push("");
  lines.push("| 状态码 | 含义 |");
  lines.push("| --- | --- |");
  lines.push("| 200 | 业务成功（`code` 为 200） |");
  lines.push("| 400 | 参数校验失败或业务规则不满足，`message` 为中文提示 |");
  lines.push("| 401 | 未登录或凭证失效 |");
  lines.push("| 403 | 已登录但当前角色无权访问 |");
  lines.push("| 500 | 服务器内部错误 |");
  lines.push("");
  lines.push("鉴权：登录接口返回 `token`，后续请求携带 `Authorization: Bearer <token>`。");
  lines.push("");
  lines.push("角色：`USER`（普通用户）、`STATION_ADMIN`（驿站管理员）、`PLATFORM_ADMIN`（管理员，统一统筹并可创建子账号分发功能权限）。");
  lines.push("");

  lines.push("## 接口分组总览");
  lines.push("");
  lines.push("| 分组 | 接口数 |");
  lines.push("| --- | --- |");
  for (const [tag, list] of [...grouped.entries()].sort((a, b) => a[0].localeCompare(b[0]))) {
    lines.push(`| ${tagLabel(tag)} | ${list.length} |`);
  }
  lines.push("");

  for (const [tag, list] of [...grouped.entries()].sort((a, b) => a[0].localeCompare(b[0]))) {
    lines.push(`## ${tagLabel(tag)}`);
    lines.push("");
    for (const { endpoint, method, operation } of list.sort((a, b) => a.endpoint.localeCompare(b.endpoint))) {
      lines.push(`### \`${method.toUpperCase()} ${endpoint}\``);
      lines.push("");
      lines.push(summarize(method, endpoint));
      lines.push("");

      const parameters = operation.parameters || [];
      const table = parametersTable(parameters);
      if (table) {
        lines.push("**请求参数**");
        lines.push("");
        lines.push(table);
        lines.push("");
      }

      const bodySchema = operation.requestBody?.content?.["application/json"]?.schema;
      if (bodySchema) {
        lines.push("**请求体**（`application/json`）");
        lines.push("");
        const fields = bodyFieldsTable(method, endpoint);
        if (fields) {
          lines.push(fields);
        } else {
          const resolved = resolveRef(spec, bodySchema);
          const body = schemaTable(spec, bodySchema);
          lines.push(body || `\`${resolved?.__name || typeOf(spec, bodySchema)}\` 对象。`);
        }
        lines.push("");
      }

      const okResponse = operation.responses?.["200"] || operation.responses?.["201"];
      const okSchema = okResponse?.content?.["*/*"]?.schema
        || okResponse?.content?.["application/json"]?.schema;
      if (okSchema) {
        const resolved = resolveRef(spec, okSchema);
        const name = resolved?.__name || "";
        if (/^ApiResponse/.test(name)) {
          lines.push(`**成功响应**：统一返回结构（\`code\`/\`message\`/\`data\`），\`data\` 为${describeData(name)}。`);
        } else {
          lines.push("**成功响应**" + (name ? `（\`${name}\`）` : ""));
          lines.push("");
          const responseTable = schemaTable(spec, okSchema);
          lines.push(responseTable || `\`${typeOf(spec, okSchema)}\``);
        }
        lines.push("");
      }
    }
  }

  fs.writeFileSync(path.join(docsDir, "接口文档.md"), `${lines.join("\n")}\n`, "utf8");

  console.log(`OpenAPI 描述已保存：${path.join(docsDir, "openapi.json")}`);
  console.log(`接口文档已生成：${path.join(docsDir, "接口文档.md")}`);
  console.log(`接口总数：${[...grouped.values()].reduce((sum, list) => sum + list.length, 0)}，分组：${grouped.size}`);
}

main().catch((error) => {
  console.error("导出失败：", error);
  process.exit(1);
});
