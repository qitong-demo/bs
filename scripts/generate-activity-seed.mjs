/**
 * 由 data/activity-sources.json 生成 activity.sql 种子。
 *
 * 与 generate-news-seed.mjs 同一套做法：数据文件里只有采集到的真实活动，
 * 每条都带可核对的原文链接；这里只做格式转换，不做任何内容加工。
 *
 * 采集来的活动一律写成 organizer_type = 'EXTERNAL'：
 * 驿站/平台之外的机构办的活动，站内只展示信息并提供跳转，**不接受站内报名**
 * ——报名与审核由主办方负责，我们既不掌握名单也无法联系报名人。
 *
 * 用法：node scripts/generate-activity-seed.mjs
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "..");

const SOURCE = path.join(root, "data", "activity-sources.json");
const TARGET = path.join(root, "backend", "src", "main", "resources", "db", "activity.sql");

const items = JSON.parse(fs.readFileSync(SOURCE, "utf8"));
if (!Array.isArray(items) || items.length === 0) {
  console.error("数据文件为空或格式不对");
  process.exit(1);
}

/** SQL 字符串转义：单引号加倍，反斜杠转义。 */
function q(value) {
  if (value === null || value === undefined) return "NULL";
  return "'" + String(value).replace(/\\/g, "\\\\").replace(/'/g, "''") + "'";
}

/**
 * 日期归一化。
 *
 * 采集到的日期精度不一：多数到日，少数只到月。activity_time 是 DATETIME NOT NULL，
 * 所以月份精度统一取当月 1 日——**这是精度妥协，不是编造**，
 * 页面展示时用 activityDate 原值（含"2025-07"这种粒度）说明真实精度。
 */
function toDateTime(raw) {
  const text = String(raw || "").trim();
  if (/^\d{4}-\d{2}-\d{2}$/.test(text)) return text + " 00:00:00";
  if (/^\d{4}-\d{2}$/.test(text)) return text + "-01 00:00:00";
  if (/^\d{4}$/.test(text)) return text + "-01-01 00:00:00";
  throw new Error("无法解析的日期：" + raw);
}

const accepted = [];
const rejected = [];

for (const item of items) {
  // 没有可核对原文链接的一律不收——这是本模块的底线
  if (!item.url || !/^https?:\/\//.test(item.url)) {
    rejected.push({ title: item.title, reason: "缺少可核对的原文链接" });
    continue;
  }
  if (!item.title || !item.organizer) {
    rejected.push({ title: item.title, reason: "缺少标题或主办方" });
    continue;
  }
  accepted.push(item);
}

const lines = [];
lines.push("-- 由 scripts/generate-activity-seed.mjs 生成，请勿手工编辑。");
lines.push("-- 数据源：data/activity-sources.json（采集自公开来源，每条均可核对原文）。");
lines.push("-- 这些活动的 organizer_type 一律为 EXTERNAL：站内只展示并提供跳转，不接受站内报名。");
lines.push("-- 主办方多为各级工会、人社局与街道社区；来源等级 GOV=政府群团 / MEDIA=官方媒体。");
lines.push("");
lines.push("-- 幂等：先清掉上一次生成的采集活动（station_id 为空表示非驿站自办），");
lines.push("-- 再重新插入。驿站自办活动不受影响。");
lines.push("USE friendly_station;");
lines.push("");
lines.push("DELETE FROM activity WHERE organizer_type = 'EXTERNAL' AND station_id IS NULL;");
lines.push("");

for (const item of accepted) {
  const fields = [
    "title",
    "station_id",
    "activity_time",
    "address",
    "max_count",
    "content",
    "status",
    "organizer_type",
    "external_url",
    "city_code",
    "publisher",
    "category",
    "summary",
    "source_level"
  ].join(", ");

  const values = [
    q(item.title),
    "NULL",
    q(toDateTime(item.activityDate)),
    q(item.address || ""),
    // 外部活动不收站内报名，名额无意义，写 0 以区别于驿站活动的默认 50
    "0",
    q(item.summary || ""),
    q("PUBLISHED"),
    q("EXTERNAL"),
    q(item.url),
    q(item.cityCode),
    q(item.organizer),
    q(item.category || ""),
    q(item.summary || ""),
    q(item.sourceLevel || "GOV")
  ].join(", ");

  lines.push(`INSERT INTO activity (${fields}) VALUES (${values});`);
}

lines.push("");
lines.push("-- 汇总");

const byCity = new Map();
const byCategory = new Map();
const byLevel = new Map();
for (const item of accepted) {
  const city = item.cityName || "全国";
  byCity.set(city, (byCity.get(city) || 0) + 1);
  byCategory.set(item.category || "未分类", (byCategory.get(item.category || "未分类") || 0) + 1);
  byLevel.set(item.sourceLevel || "GOV", (byLevel.get(item.sourceLevel || "GOV") || 0) + 1);
}
lines.push(`-- 共 ${accepted.length} 条`);
lines.push(`-- 城市：${[...byCity.entries()].map(([k, v]) => `${k}=${v}`).join("  ")}`);
lines.push(`-- 分类：${[...byCategory.entries()].map(([k, v]) => `${k}=${v}`).join("  ")}`);
lines.push(`-- 来源等级：${[...byLevel.entries()].map(([k, v]) => `${k}=${v}`).join("  ")}`);
lines.push("");

fs.writeFileSync(TARGET, lines.join("\n"), "utf8");

console.log(`已收录 ${accepted.length} 条真实活动${rejected.length ? `，拒绝 ${rejected.length} 条` : ""}`);
console.log(`城市分布：${[...byCity.entries()].map(([k, v]) => `${k}=${v}`).join("  ")}`);
console.log(`分类分布：${[...byCategory.entries()].map(([k, v]) => `${k}=${v}`).join("  ")}`);
console.log(`来源等级：${[...byLevel.entries()].map(([k, v]) => `${k}=${v}`).join("  ")}`);
if (rejected.length) {
  rejected.forEach((r) => console.log(`  排除：${r.title}（${r.reason}）`));
}
console.log(`已生成：${path.relative(root, TARGET)}`);
