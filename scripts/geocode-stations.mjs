/**
 * 用高德地图 Web 服务为驿站回填坐标与缺失区县（派生数据，非来源给出）。
 *
 * 输入：backend/src/main/resources/db/stations.json
 * 输出：data/station-geocode.json（**入仓的缓存**，保证结果可复现、重跑不必再调接口）
 *
 * 策略（实测：仅按名称搜 POI 的「有返回率」是 100%，但会把「大悦城小哥驿站」和
 * 「万达小哥驿站」匹配到同一个点，因此必须做置信度校验）：
 *   1. POI 搜索（offset=5），逐条校验区县一致性 + 名称相似度 + 地址相似度，取最可信的一条；
 *   2. 校验不通过则回退到地址地理编码；
 *   3. 两者都失败则跳过，保留空坐标（不从名称推断、不编造）。
 *
 * 依赖环境变量 AMAP_WEB_KEY（高德 Web 服务 Key）。该 Key 只在后端/脚本使用，绝不进前端。
 *
 * 用法：
 *   node scripts/geocode-stations.mjs                # 增量：只处理缓存里没有的
 *   node scripts/geocode-stations.mjs --refresh      # 全量重跑
 *   node scripts/geocode-stations.mjs --limit 50     # 只处理 50 条（调试用）
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const stationsPath = path.join(projectRoot, "backend", "src", "main", "resources", "db", "stations.json");
const cachePath = path.join(projectRoot, "data", "station-geocode.json");

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
const cityNameByCode = Object.fromEntries(dataset.cities.map((city) => [city.cityCode, city.cityName]));

const cache = !refresh && fs.existsSync(cachePath)
  ? JSON.parse(fs.readFileSync(cachePath, "utf8"))
  : { generatedAt: "", provider: "amap", note: "", stats: {}, stations: {} };
cache.stations = cache.stations || {};

/** 驿站名称里的通用词：比较「区别于其他驿站的专名部分」时去掉。 */
const GENERIC_WORDS = [
  "户外职工爱心接力站", "户外劳动者服务站点", "新就业群体基层服务站点", "户外工作者驿站",
  "工会驿站", "职工驿站", "暖蜂驿站", "暖心驿站", "暖新驿站", "小哥驿站", "骑手驿站",
  "城管驿站", "爱心驿站", "党群服务中心", "畅享驿站", "服务站点", "驿站", "服务站", "服务点"
];

function stripGeneric(text) {
  let out = String(text || "");
  for (const word of GENERIC_WORDS) {
    out = out.split(word).join("");
  }
  return out.replace(/[·・\-—()（）\s"“”]/g, "");
}

/** 字符重合度：交集大小 / 较短串长度。 */
function similarity(a, b) {
  const left = new Set(stripGeneric(a));
  const right = new Set(stripGeneric(b));
  if (left.size === 0 || right.size === 0) return 0;
  let common = 0;
  for (const char of left) {
    if (right.has(char)) common += 1;
  }
  return common / Math.min(left.size, right.size);
}

const NAME_OK = 0.6;
const ADDR_OK = 0.5;
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function fetchJson(url, retries = 3) {
  for (let attempt = 1; attempt <= retries; attempt += 1) {
    try {
      const response = await fetch(url);
      const json = await response.json();
      if (json.status === "1") return json;
      // 高德配额超限会返回 infocode 10003 等，直接抛出让调用方记录
      if (json.infocode && json.infocode !== "10000") {
        throw new Error(`${json.info}(${json.infocode})`);
      }
      return json;
    } catch (error) {
      if (attempt === retries) throw error;
      await sleep(500 * attempt);
    }
  }
  return null;
}

async function searchPoi(name, cityName) {
  const url = "https://restapi.amap.com/v3/place/text"
    + `?keywords=${encodeURIComponent(name)}&city=${encodeURIComponent(cityName)}`
    + `&citylimit=true&offset=5&key=${KEY}`;
  const json = await fetchJson(url);
  return Array.isArray(json?.pois) ? json.pois : [];
}

async function geocodeAddress(address, cityName) {
  const url = "https://restapi.amap.com/v3/geocode/geo"
    + `?address=${encodeURIComponent(address)}&city=${encodeURIComponent(cityName)}&key=${KEY}`;
  const json = await fetchJson(url);
  const hit = json?.geocodes?.[0];
  return hit ? { location: hit.location, district: hit.district, level: hit.level } : null;
}

function splitLocation(location) {
  const [lng, lat] = String(location || "").split(",").map(Number);
  return Number.isFinite(lng) && Number.isFinite(lat) ? { longitude: lng, latitude: lat } : null;
}

/** 判断 POI 的 adname 是否与驿站记录的 area 相符；area 不是区县时不做限制。 */
function districtConsistent(area, adname) {
  if (!area || !/[区县市]$/.test(area)) return true;
  if (!adname) return false;
  return adname === area || adname.includes(area) || area.includes(adname);
}

const today = new Date();
const geocodedAt = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;

const stats = { poiTrusted: 0, geocodeTrusted: 0, missed: 0, districtFilled: 0 };
const pending = dataset.stations.filter((station) => refresh || !cache.stations[station.id]);
const targets = pending.slice(0, limit);

console.log(`驿站总数 ${dataset.stations.length}，本次待处理 ${targets.length} 条（缓存已有 ${Object.keys(cache.stations).length} 条）\n`);

let processed = 0;
for (const station of targets) {
  const cityName = cityNameByCode[station.cityCode] || "";
  // 记录驿站名称：生成器按编号关联缓存，名称可用于校验编号是否错位，避免坐标准张冠李戴。
  const record = { stationName: station.stationName, source: "", district: "", matchedName: "", geocodedAt };

  try {
    const pois = await searchPoi(station.stationName, cityName);
    await sleep(110);

    let best = null;
    let bestName = 0;
    for (const poi of pois) {
      const nameScore = similarity(station.stationName, poi.name);
      const addrScore = similarity(station.address, poi.address);
      if (!districtConsistent(station.area, poi.adname)) continue;
      if (nameScore >= NAME_OK || (nameScore >= 0.4 && addrScore >= ADDR_OK)) {
        if (nameScore > bestName) {
          best = poi;
          bestName = nameScore;
        }
      }
    }

    if (best) {
      const point = splitLocation(best.location);
      if (point) {
        record.source = "AMAP_POI";
        record.longitude = point.longitude;
        record.latitude = point.latitude;
        record.district = best.adname || "";
        record.matchedName = best.name || "";
        stats.poiTrusted += 1;
      }
    }

    if (!record.source) {
      const geo = await geocodeAddress(`${cityName}${station.area}${station.address}`, cityName);
      await sleep(110);
      const point = geo ? splitLocation(geo.location) : null;
      if (point) {
        record.source = "AMAP_GEOCODE";
        record.longitude = point.longitude;
        record.latitude = point.latitude;
        record.district = geo.district || "";
        record.matchedName = station.address;
        stats.geocodeTrusted += 1;
      }
    }

    if (!record.source) {
      record.source = "AMAP_MISS";
      stats.missed += 1;
    }
  } catch (error) {
    record.source = "AMAP_ERROR";
    record.error = String(error.message || error);
    stats.missed += 1;
  }

  cache.stations[station.id] = record;
  processed += 1;

  const tail = record.source === "AMAP_POI" || record.source === "AMAP_GEOCODE"
    ? `${record.source} ${record.longitude},${record.latitude} ${record.district}`
    : record.source;
  console.log(`[${processed}/${targets.length}] #${station.id} ${station.stationName.slice(0, 24)} → ${tail}`);

  // 每 100 条落盘一次，长任务可中断续跑
  if (processed % 100 === 0) {
    writeCache();
  }
}

function writeCache() {
  cache.generatedAt = new Date().toISOString();
  cache.note = "坐标为高德地图地理编码派生结果，**非公开来源给出**；AMAP_POI 表示按驿站名称匹配到高德 POI，"
    + "AMAP_GEOCODE 表示按详细地址地理编码，AMAP_MISS 表示未能定位（坐标保持为空）。";
  const all = Object.values(cache.stations);
  cache.stats = {
    total: all.length,
    poiTrusted: all.filter((item) => item.source === "AMAP_POI").length,
    geocodeTrusted: all.filter((item) => item.source === "AMAP_GEOCODE").length,
    missed: all.filter((item) => item.source === "AMAP_MISS" || item.source === "AMAP_ERROR").length
  };
  fs.mkdirSync(path.dirname(cachePath), { recursive: true });
  fs.writeFileSync(cachePath, `${JSON.stringify(cache, null, 2)}\n`, "utf8");
}

writeCache();

console.log("\n=== 本次统计 ===");
console.log(`POI 可信命中    ${stats.poiTrusted}`);
console.log(`地理编码命中    ${stats.geocodeTrusted}`);
console.log(`未命中          ${stats.missed}`);
console.log("\n=== 缓存累计 ===");
console.log(JSON.stringify(cache.stats, null, 2));
console.log(`\n已写入 ${path.relative(projectRoot, cachePath)}`);
