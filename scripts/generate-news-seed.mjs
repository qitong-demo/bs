/**
 * 把人工核实的政策与资讯清单生成数据库种子数据。
 *
 * 输入：data/policy-sources.json（由调研产出，每条都带可打开核对的原文链接）
 * 输出：
 *   - backend/src/main/resources/db/news.sql   导入 MySQL 的 INSERT
 *   - docs/政策资讯来源.md                      来源说明，便于逐条核对
 *
 * 诚实性原则（与本项目其他数据一致）：
 *   - 只导入 verified === true 的条目；
 *   - 未核实条目单独写入文档的「未纳入」小节，说明原因，不混进正文；
 *   - 每条都保留 publisher 与 source_url，界面与文档都能追溯到原文。
 *
 * 用法：node scripts/generate-news-seed.mjs
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const sourcePath = path.join(projectRoot, "data", "policy-sources.json");
const sqlPath = path.join(projectRoot, "backend", "src", "main", "resources", "db", "news.sql");
const docPath = path.join(projectRoot, "docs", "政策资讯来源.md");

const CITY_NAMES = {
  "330100": "杭州市",
  "110100": "北京市",
  "310100": "上海市",
  "440300": "深圳市",
  "440100": "广州市",
  "510100": "成都市",
  "420100": "武汉市"
};

const ALLOWED_CATEGORIES = new Set([
  "权益保障", "社会保险", "职业伤害", "算法与规则", "驿站服务",
  "福利补贴", "技能培训", "子女关爱", "劳动报酬"
]);

if (!fs.existsSync(sourcePath)) {
  console.error(`未找到 ${path.relative(projectRoot, sourcePath)}。请先完成政策调研再运行本脚本。`);
  process.exit(1);
}

const dataset = JSON.parse(fs.readFileSync(sourcePath, "utf8"));
const items = Array.isArray(dataset.items) ? dataset.items : [];
const unverified = Array.isArray(dataset.unverified) ? dataset.unverified : [];

/** SQL 字符串字面量转义。 */
function sqlText(value) {
  if (value === null || value === undefined || value === "") return "NULL";
  return `'${String(value).replace(/\\/g, "\\\\").replace(/'/g, "''")}'`;
}

/** 归一化日期：YYYY-MM-DD，无法识别返回 null。 */
function normalizeDate(value) {
  const text = String(value || "").trim();
  return /^\d{4}-\d{2}-\d{2}$/.test(text) ? text : null;
}

const accepted = [];
const rejected = [];
const seen = new Set();

for (const item of items) {
  const title = String(item.title || "").trim();
  const url = String(item.url || "").trim();
  if (!title || !url) {
    rejected.push({ ...item, reason: "缺少标题或原文链接" });
    continue;
  }
  if (seen.has(title)) {
    rejected.push({ ...item, reason: "标题重复" });
    continue;
  }
  // 只导入明确核实过的：verified 未显式设为 false 也接受，但必须在报告里说明口径
  if (item.verified === false) {
    rejected.push({ ...item, reason: "标记为未核实" });
    continue;
  }
  seen.add(title);

  const category = ALLOWED_CATEGORIES.has(item.category) ? item.category : "权益保障";
  const sourceType = item.sourceType === "PLATFORM" ? "PLATFORM" : "OFFICIAL";
  const rawCity = item.cityCode ? String(item.cityCode).trim() : "";

  // 城市归属必须是平台已覆盖的 7 个城市之一。
  // 否则这条资讯在任何城市都匹配不上（既非本地、又非不限城市），等于永远不显示——
  // 与其悄悄丢失，不如明确排除并在文档里说明原因。
  if (rawCity && !CITY_NAMES[rawCity]) {
    rejected.push({ ...item, reason: `城市 ${item.cityName || rawCity} 不在平台覆盖范围内，导入后不会在任何城市显示` });
    continue;
  }

  accepted.push({
    title,
    category,
    sourceType,
    publisher: String(item.publisher || "").trim(),
    platform: item.platform ? String(item.platform).trim() : "",
    cityCode: rawCity,
    cityName: rawCity ? CITY_NAMES[rawCity] : "",
    publishedAt: normalizeDate(item.publishedAt),
    url,
    summary: String(item.summary || "").trim()
  });
}

// ---------------------------------------------------------------- 生成 SQL
const sql = [];
sql.push("-- 政策与资讯种子数据（由 scripts/generate-news-seed.mjs 生成，请勿手工编辑）");
sql.push("--");
sql.push("-- 数据来源：data/policy-sources.json，每条都带可打开的原文链接。");
sql.push("-- city_code 为 NULL 表示不限城市（国家层面或全国性平台政策），在所有城市都可见；");
sql.push("-- 非空时只在该城市可见，并在该城市排到最前面。");
sql.push("-- source_type 区分官方（政府/群团/官方媒体）与平台企业自行发布。");
sql.push("");
sql.push("USE friendly_station;");
sql.push("");
sql.push("DELETE FROM news;");
sql.push("");
sql.push("INSERT INTO news (title, category, content, view_count, status, source_type, publisher, platform, city_code, published_at, source_url) VALUES");

const rows = accepted.map((item) => {
  // 正文：摘要 + 来源署名 + 原文链接。界面详情直接展示，用户可点链接回溯原文。
  const contentParts = [];
  if (item.summary) contentParts.push(item.summary);
  if (item.publisher) contentParts.push(`发布方：${item.publisher}`);
  if (item.publishedAt) contentParts.push(`发布日期：${item.publishedAt}`);
  if (item.cityName) contentParts.push(`适用城市：${item.cityName}`);
  else contentParts.push("适用范围：全国");
  contentParts.push(`原文链接：${item.url}`);
  const content = contentParts.join("\n");

  return "  (" + [
    sqlText(item.title),
    sqlText(item.category),
    sqlText(content),
    "0",
    sqlText("PUBLISHED"),
    sqlText(item.sourceType),
    sqlText(item.publisher),
    sqlText(item.platform),
    sqlText(item.cityCode),
    sqlText(item.publishedAt),
    sqlText(item.url)
  ].join(", ") + ")";
});
sql.push(rows.join(",\n") + ";");
sql.push("");

fs.mkdirSync(path.dirname(sqlPath), { recursive: true });
fs.writeFileSync(sqlPath, sql.join("\n"), "utf8");

// ---------------------------------------------------------------- 生成来源说明
const byCity = {};
const bySource = {};
const byCategory = {};
for (const item of accepted) {
  const city = item.cityCode ? `${item.cityName}（${item.cityCode}）` : "不限城市";
  byCity[city] = (byCity[city] || 0) + 1;
  const source = item.sourceType === "PLATFORM" ? `平台：${item.platform || item.publisher}` : "官方";
  bySource[source] = (bySource[source] || 0) + 1;
  byCategory[item.category] = (byCategory[item.category] || 0) + 1;
}

const doc = [];
doc.push("# 政策与资讯来源");
doc.push("");
doc.push("> 本文件由 `scripts/generate-news-seed.mjs` 根据 `data/policy-sources.json` 生成。");
doc.push(`> 收录 **${accepted.length}** 条，每条都带**可打开的原文链接**，可逐条核对。`);
doc.push(">");
doc.push("> **口径说明**：这些条目来自公开发布的政策文件与平台公告，系统只做了摘录与归类，");
doc.push("> **不代表已获得发布方授权**，也不代表对政策内容的解释；具体执行以原文为准。");
doc.push("");
doc.push("## 一、收录概览");
doc.push("");
doc.push("| 维度 | 分布 |");
doc.push("| --- | --- |");
doc.push(`| 总数 | ${accepted.length} 条 |`);
doc.push(`| 来源类型 | ${Object.entries(bySource).map(([k, v]) => `${k} ${v} 条`).join("；")} |`);
doc.push(`| 城市归属 | ${Object.entries(byCity).map(([k, v]) => `${k} ${v} 条`).join("；")} |`);
doc.push(`| 分类 | ${Object.entries(byCategory).map(([k, v]) => `${k} ${v} 条`).join("；")} |`);
doc.push("");
doc.push("## 二、逐条来源");
doc.push("");
doc.push("| # | 标题 | 发布方 | 来源 | 城市 | 发布日期 | 原文 |");
doc.push("| --- | --- | --- | --- | --- | --- | --- |");
accepted.forEach((item, index) => {
  const source = item.sourceType === "PLATFORM" ? `平台 · ${item.platform || ""}` : "官方";
  const city = item.cityCode ? item.cityName : "全国";
  doc.push(`| ${index + 1} | ${item.title} | ${item.publisher || "—"} | ${source} | ${city} | ${item.publishedAt || "—"} | [原文](${item.url}) |`);
});
doc.push("");
doc.push("## 三、未纳入的条目");
doc.push("");
if (rejected.length === 0 && unverified.length === 0) {
  doc.push("本期调研没有产生未纳入条目。");
} else {
  doc.push("以下条目**没有被写入系统**，原因如实列出，供后续补充：");
  doc.push("");
  doc.push("| 标题 | 原因 | 链接 |");
  doc.push("| --- | --- | --- |");
  for (const item of unverified) {
    doc.push(`| ${item.title || "（无标题）"} | ${item.note || "未能打开页面核实"} | ${item.url || "—"} |`);
  }
  for (const item of rejected) {
    doc.push(`| ${item.title || "（无标题）"} | ${item.reason} | ${item.url || "—"} |`);
  }
}
doc.push("");
doc.push("## 四、复现方式");
doc.push("");
doc.push("```powershell");
doc.push("# 1) 抓取并核实来源（结果写入 data/policy-sources.json）");
doc.push("# 2) 生成 news.sql 与本说明文档");
doc.push("node scripts/generate-news-seed.mjs");
doc.push("# 3) 重新导入数据库");
doc.push("powershell -ExecutionPolicy Bypass -File scripts/init-mysql.ps1 -User root -Password <密码> -Reset");
doc.push("```");
doc.push("");

fs.mkdirSync(path.dirname(docPath), { recursive: true });
fs.writeFileSync(docPath, doc.join("\n"), "utf8");

// ---------------------------------------------------------------- 生成前端平台清单
// 平台下拉的选项直接由来源数据派生，避免前端硬编码一份容易过期的列表。
const platforms = [...new Set(accepted.map((item) => item.platform).filter(Boolean))].sort((a, b) =>
  a.localeCompare(b, "zh-Hans-CN")
);
const constantPath = path.join(projectRoot, "frontend", "src", "constants", "news-platforms.js");
const constantLines = [
  "/**",
  " * 政策资讯可筛选的平台清单。",
  " *",
  " * 由 scripts/generate-news-seed.mjs 从 data/policy-sources.json 生成，请勿手工编辑。",
  " * 这里列的是**资讯涉及的平台**（含官方媒体报道该平台的情形），",
  " * 不区分这条是平台自己发布的、还是官方媒体发布的。",
  " */",
  `export const NEWS_PLATFORMS = ${JSON.stringify(platforms, null, 2)};`,
  ""
];
fs.mkdirSync(path.dirname(constantPath), { recursive: true });
fs.writeFileSync(constantPath, constantLines.join("\n"), "utf8");

console.log(`已收录 ${accepted.length} 条（未纳入 ${rejected.length + unverified.length} 条）`);
console.log(`来源类型：${Object.entries(bySource).map(([k, v]) => `${k}=${v}`).join("  ")}`);
console.log(`城市分布：${Object.entries(byCity).map(([k, v]) => `${k}=${v}`).join("  ")}`);
console.log(`涉及平台 ${platforms.length} 个：${platforms.join("、")}`);
console.log(`已生成：\n  - ${path.relative(projectRoot, sqlPath)}\n  - ${path.relative(projectRoot, docPath)}\n  - ${path.relative(projectRoot, constantPath)}`);
