/**
 * 从高德同步驿站的可验证营业信息（营业时间 open_time、联系电话 tel）。
 *
 * 输入：backend/src/main/resources/db/stations.json
 * 输出：data/station-business.json（**入仓的缓存**，重跑生成器不必再调接口）
 *
 * 为什么只同步这两项：
 *   高德 POI 有 biz_ext.open_time 与 tel，但**没有**「服务设施」这类结构化字段
 *   （type/tag 只是「生活服务/政府机构」这种粗分类），所以饮水/充电/热饭等服务项目
 *   仍然只能来自公开来源文本，不能从高德补。
 *
 * 为什么必须做可信度校验：
 *   实测随机 40 条中，7 条 POI 匹配落到了**别的驿站**上，其中 3 条还带营业时间——
 *   例如「易家街道夏日清凉驿站」被匹配到「菜鸟驿站(双柏后街店)」。
 *   无条件同步会把错误数据写进库，因此这里复用与坐标回填相同的三重校验。
 *
 * 用法：
 *   node scripts/sync-station-business.mjs            # 增量：只处理缓存里没有的
 *   node scripts/sync-station-business.mjs --refresh  # 全量重跑
 *   node scripts/sync-station-business.mjs --limit 50 # 调试
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const stationsPath = path.join(projectRoot, "backend", "src", "main", "resources", "db", "stations.json");
const cachePath = path.join(projectRoot, "data", "station-business.json");

const KEY = process.env.AMAP_WEB_KEY;
if (!KEY) {
  console.error("缺少环境变量 AMAP_WEB_KEY（高德 Web 服务 Key）。该 Key 只在后端与脚本使用，不要写入前端或提交到公开仓库。");
  process.exit(1);
}

const args = process.argv.slice(2);
const refresh = args.includes("--refresh");
const limitIndex = args.indexOf("--limit");
const limit = limitIndex >= 0 ? Number(args[limitIndex + 1]) : Infinity;

const dataset = JSON.parse(fs.readFileSync(stationsPath, "utf8"));
const cityNameByCode = Object.fromEntries(dataset.cities.map((c) => [c.cityCode, c.cityName]));

const cache = !refresh && fs.existsSync(cachePath)
  ? JSON.parse(fs.readFileSync(cachePath, "utf8"))
  : { generatedAt: "", provider: "amap", note: "", stats: {}, stations: {} };
cache.stations = cache.stations || {};

const GENERIC_WORDS = [
  "户外职工爱心接力站", "户外劳动者服务站点", "新就业群体基层服务站点", "户外工作者驿站",
  "工会驿站", "职工驿站", "暖蜂驿站", "暖心驿站", "暖新驿站", "小哥驿站", "骑手驿站",
  "城管驿站", "爱心驿站", "党群服务中心", "畅享驿站", "服务站点", "驿站", "服务站", "服务点"
];

function stripGeneric(value) {
  let out = String(value || "");
  for (const word of GENERIC_WORDS) out = out.split(word).join("");
  return out.replace(/[·・\-—()（）\s"“”]/g, "");
}

function similarity(a, b) {
  const left = new Set(stripGeneric(a));
  const right = new Set(stripGeneric(b));
  if (!left.size || !right.size) return 0;
  let common = 0;
  for (const ch of left) if (right.has(ch)) common += 1;
  return common / Math.min(left.size, right.size);
}

/** 高德空字段会返回空数组，需要归一化成字符串，否则 [] 会被当成有效值。 */
function text(value) {
  if (value === null || value === undefined) return "";
  if (Array.isArray(value)) return value.length === 0 ? "" : value.join(",");
  return String(value).trim();
}

const NAME_OK = 0.6;
const ADDR_OK = 0.5;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function fetchJson(url, retries = 3) {
  for (let attempt = 1; attempt <= retries; attempt += 1) {
    try {
      const response = await fetch(url);
      const json = await response.json();
      if (json.status === "1") return json;
      if (json.infocode && json.infocode !== "10000") throw new Error(`${json.info}(${json.infocode})`);
      return json;
    } catch (error) {
      if (attempt === retries) throw error;
      await sleep(400 * attempt);
    }
  }
  return null;
}

async function searchPoi(name, cityName) {
  const url = "https://restapi.amap.com/v3/place/text"
    + `?keywords=${encodeURIComponent(name)}&city=${encodeURIComponent(cityName)}`
    + `&citylimit=true&offset=5&extensions=all&key=${KEY}`;
  const json = await fetchJson(url);
  return Array.isArray(json?.pois) ? json.pois : [];
}

function districtConsistent(area, adname) {
  if (!area || !/[区县市]$/.test(area)) return true;
  if (!adname) return false;
  return adname === area || adname.includes(area) || area.includes(adname);
}

/** 剔除明显不是「驿站营业时间」的值（例如菜鸟驿站的营业时间被误配回来）。 */
function plausibleOpenTime(value) {
  const openTime = text(value);
  if (!openTime) return "";
  if (openTime.length > 40) return "";
  return /^\s*(\d{1,2}[:：]\d{2}|24\s*小时|全天)/.test(openTime) ? openTime : "";
}

const today = new Date();
const syncedAt = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;

const pending = dataset.stations.filter((station) => refresh || !cache.stations[station.id]);
const targets = pending.slice(0, limit);

console.log(`驿站总数 ${dataset.stations.length}，本次待处理 ${targets.length} 条（缓存已有 ${Object.keys(cache.stations).length} 条）\n`);

let processed = 0;
for (const station of targets) {
  const cityName = cityNameByCode[station.cityCode] || "";
  const record = { stationName: station.stationName, matchedName: "", matchScore: 0, openTime: "", tel: "", syncedAt };

  try {
    const pois = await searchPoi(station.stationName, cityName);
    await sleep(110);

    let best = null;
    let bestScore = 0;
    for (const poi of pois) {
      const nameScore = similarity(station.stationName, poi.name);
      const addrScore = similarity(station.address, poi.address);
      if (!districtConsistent(station.area, poi.adname)) continue;
      if (nameScore >= NAME_OK || (nameScore >= 0.4 && addrScore >= ADDR_OK)) {
        if (nameScore > bestScore) { best = poi; bestScore = nameScore; }
      }
    }

    if (best) {
      record.matchedName = text(best.name);
      record.matchScore = Number(bestScore.toFixed(2));
      record.openTime = plausibleOpenTime(best.biz_ext?.open_time || best.biz_ext?.opentime);
      record.tel = text(best.tel).split(";")[0];
    }
  } catch (error) {
    record.error = String(error.message || error);
  }

  cache.stations[station.id] = record;
  processed += 1;
  const tail = [record.openTime ? `营业时间=${record.openTime}` : "", record.tel ? `电话=${record.tel}` : ""]
    .filter(Boolean).join("  ") || "无可同步字段";
  console.log(`[${processed}/${targets.length}] #${station.id} ${station.stationName.slice(0, 22)} → ${tail}`);

  if (processed % 100 === 0) writeCache();
}

function writeCache() {
  cache.generatedAt = new Date().toISOString();
  cache.note = "营业时间与联系电话来自高德地图 POI（biz_ext.open_time / tel），属于第三方数据，非驿站官方提供；"
    + "仅保留名称相似度校验通过的可信匹配。高德不提供「服务设施」结构化字段，服务项目仍来自公开来源文本。";
  const all = Object.values(cache.stations);
  cache.stats = {
    total: all.length,
    matched: all.filter((item) => item.matchScore >= NAME_OK).length,
    withOpenTime: all.filter((item) => item.openTime).length,
    withTel: all.filter((item) => item.tel).length
  };
  fs.mkdirSync(path.dirname(cachePath), { recursive: true });
  fs.writeFileSync(cachePath, `${JSON.stringify(cache, null, 2)}\n`, "utf8");
}

writeCache();

console.log("\n=== 缓存累计 ===");
console.log(JSON.stringify(cache.stats, null, 2));
const t = cache.stats.total || 1;
console.log(`营业时间覆盖 ${cache.stats.withOpenTime}/${t} (${Math.round(cache.stats.withOpenTime / t * 100)}%)，`
  + `电话覆盖 ${cache.stats.withTel}/${t} (${Math.round(cache.stats.withTel / t * 100)}%)`);
console.log(`已写入 ${path.relative(projectRoot, cachePath)}`);
