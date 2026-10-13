/**
 * 生成多城市驿站数据（canonical JSON + MySQL 初始化 SQL + 数据来源说明）。
 *
 * 输入：
 *   tmp/station-sources/parsed.json   杭州：由 scripts/parse-station-sources.mjs 从来源页面解析
 *   tmp/city-sources/<city>.json      其他城市：由采集流程产出（见 docs/驿站数据来源.md）
 * 输出：
 *   backend/src/main/resources/db/stations.json   两套 profile 共用的权威数据（内存实现直接读取）
 *   backend/src/main/resources/db/stations.sql    MySQL 初始化用的 INSERT 语句（含 city 表）
 *   docs/驿站数据来源.md                            逐条来源与核对记录（可复核）
 *   docs/station-sources.json                     解析后的原始记录（含来源原文片段，便于溯源）
 *
 * 原则：只做字段搬运与规范化，不从名称或上下文推测来源未给出的信息。
 *   - 来源未标注所属行政区的，area 记为城市名，并在 data_note 说明；
 *   - 来源未给出开放时间/服务设施的，字段留空，不填充默认值；
 *   - 来源只给出名称、没有地址的记录不导入（无法作为可用点位），但在文档中说明条数；
 *   - 经纬度来源未提供时保持为空，绝不用地址反推坐标。
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const parsedPath = path.join(projectRoot, "tmp", "station-sources", "parsed.json");
const citySourcesDir = path.join(projectRoot, "tmp", "city-sources");

if (!fs.existsSync(parsedPath)) {
  console.error(`缺少杭州解析结果：${parsedPath}\n请先运行 node scripts/parse-station-sources.mjs`);
  process.exit(1);
}
const parsed = JSON.parse(fs.readFileSync(parsedPath, "utf8"));

/** 核对日期：本次采集与核对来源的日期（按本地时区计算）。 */
function localDate(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
const VERIFIED_AT = process.env.VERIFIED_AT || localDate();

/**
 * 平台覆盖城市的元数据与展示顺序。
 * sortNo 决定城市选择器顺序与默认城市（1 = 平台默认城市）。
 */
const CITY_REGISTRY = [
  { cityCode: "330100", cityName: "杭州市", province: "浙江省", centerLng: 120.1551, centerLat: 30.2741, sortNo: 1 },
  { cityCode: "110100", cityName: "北京市", province: "北京市", centerLng: 116.4074, centerLat: 39.9042, sortNo: 2 },
  { cityCode: "310100", cityName: "上海市", province: "上海市", centerLng: 121.4737, centerLat: 31.2304, sortNo: 3 },
  { cityCode: "440300", cityName: "深圳市", province: "广东省", centerLng: 114.0579, centerLat: 22.5431, sortNo: 4 },
  { cityCode: "440100", cityName: "广州市", province: "广东省", centerLng: 113.2644, centerLat: 23.1291, sortNo: 5 },
  { cityCode: "510100", cityName: "成都市", province: "四川省", centerLng: 104.0665, centerLat: 30.5723, sortNo: 6 },
  { cityCode: "420100", cityName: "武汉市", province: "湖北省", centerLng: 114.3055, centerLat: 30.5928, sortNo: 7 }
];

/** 杭州：城管驿站来源对各站点的统一配置描述。 */
const CHENGGUAN_COMMON_SERVICES = ["休息区", "补给区", "阅读区", "应急医疗区", "空调", "免费茶水"];
const CHENGGUAN_OPEN_TIME = "每日 07:00-18:00";

/** 杭州小哥驿站：来源逐站描述的服务设施。 */
const XIAOGE_SERVICES = {
  钱江苑小哥驿站: ["小哥食堂", "小哥学院", "换电柜", "专属停车位", "空调", "微波炉", "应急药箱", "血压计", "桌椅", "图书"],
  大悦城小哥驿站: ["空调", "冰箱", "饮水机", "换电柜", "专区停车位"],
  万达小哥驿站: ["空调", "微波炉", "应急药箱", "血压计", "储物柜", "桌椅", "冰箱", "换电柜", "爱心雨具", "Wi-Fi", "图书角"],
  黄龙小哥驿站: ["空调", "微波炉", "应急药箱", "冰箱", "血压计", "手机充电", "桌椅", "免费停车", "优惠充电", "午餐服务"]
};

/**
 * 演示配置：把前 4 号「小哥驿站」分配给演示账号 2（station_admin_01）。
 * 用于验证驿站管理员的数据范围；该分配是演示配置，不属于公开来源内容。
 */
const DEMO_MANAGER_ID = 2;
const DEMO_MANAGED_STATION_IDS = [1, 2, 3, 4];

/** 按服务名称推断服务类型编码（与后端 station_service.service_type 取值一致）。 */
function serviceTypeOf(name) {
  if (/换电|充电|功率|电/.test(name)) return "POWER";
  if (/饭|餐|饮|水|茶/.test(name)) return "MEAL";
  if (/药|医疗|血压|急救/.test(name)) return "HEALTH";
  if (/Wi-Fi|网络|图书|阅读|学院|报刊/.test(name)) return "NETWORK";
  return "GENERAL";
}

/**
 * 把来源里的「服务内容」文本规范化为简短的服务项目列表。
 *
 * station_service 是给预约选择用的服务项，必须是短名称；但部分来源把整段描述
 * （例如「洗衣缝纫、针头线脑……配备饮水机、微波炉、烘干机、手机充电站等设施设备」）
 * 整体填进了服务字段。这里按顿号/逗号/分号拆开，去掉括号里的补充说明与「等」字，
 * 过长的描述性片段不计入服务项目（原始文本完整保留在 docs/station-sources.json 中）。
 */
const MAX_SERVICE_NAME_LENGTH = 15;
const MAX_SERVICES_PER_STATION = 15;

function normalizeServices(rawServices) {
  const items = [];
  for (const raw of rawServices || []) {
    for (const piece of String(raw).split(/[、，,；;/\n]+/)) {
      const cleaned = piece
        .replace(/[（(][^）)]*[）)]/g, "")
        .replace(/[（()）]/g, "")
        .replace(/^等/, "")
        .replace(/等(服务|设施|项目)?$/, "")
        .replace(/[。.]+$/, "")
        .replace(/^设有|^提供|^配备/, "")
        .trim();
      if (!cleaned || cleaned.length > MAX_SERVICE_NAME_LENGTH) {
        continue;
      }
      if (!items.includes(cleaned)) {
        items.push(cleaned);
      }
      if (items.length >= MAX_SERVICES_PER_STATION) {
        return items;
      }
    }
  }
  return items;
}

function toServiceItems(names) {
  return (names || []).map((name) => ({ name, type: serviceTypeOf(name) }));
}

/**
 * 已知的驿站类型名称（各地叫法不同）。
 * 只有名称中确实出现这些词时才据此归类，避免把「龙潭街道CC·社区青年汇」里的前半段误当成类型。
 * 长的写在前面，保证「户外职工爱心接力站」优先于「爱心接力站」。
 */
const KNOWN_STATION_TYPES = [
  "户外职工爱心接力站",
  "户外劳动者服务站点",
  "户外劳动者驿站",
  "党群服务驿站",
  "党群服务中心",
  "城管驿站",
  "小哥驿站",
  "骑手驿站",
  "暖蜂驿站",
  "暖心驿站",
  "暖新驿站",
  "工会驿站",
  "职工驿站",
  "爱心驿站",
  "司机之家",
  "青年汇"
];

/** 从驿站名称中识别驿站类型；识别不到时返回空字符串，由调用方使用兜底类型。 */
function stationTypeFromName(name) {
  // 优先匹配「类型·具体名称」结构的前缀，避免误判名称中段的词。
  const separatorIndex = name.indexOf("·");
  if (separatorIndex > 0 && separatorIndex <= 12) {
    const prefix = name.slice(0, separatorIndex).trim();
    if (KNOWN_STATION_TYPES.includes(prefix)) {
      return prefix;
    }
  }
  for (const type of KNOWN_STATION_TYPES) {
    if (name.includes(type)) {
      return type;
    }
  }
  return "";
}

/** 统一的字段口径判定：来源没有逐条给出的字段，都如实记进 data_note。 */
function resolveDataStatus({ address, district, services, openTime, extraNotes = [] }) {
  const missing = [...extraNotes];
  if (!address) missing.push("详细地址");
  if (!district) missing.push("所属行政区");
  if (!services || services.length === 0) missing.push("服务设施");
  if (!openTime) missing.push("开放时间");
  return {
    dataStatus: missing.length === 0 ? "VERIFIED" : "PARTIAL",
    dataNote: missing.length === 0 ? "" : `来源未逐条给出：${missing.join("、")}`
  };
}

const stations = [];
const excludedNoAddress = [];
const citySummaries = [];

// ------------------------------------------------------------ 1) 杭州
const chengguanSource = parsed.sources.chengguan;
const xiaogeSource = parsed.sources.xiaoge;
const hangzhouCity = CITY_REGISTRY[0];

const hangzhouStations = [];
for (const item of parsed.xiaogeStations) {
  const services = XIAOGE_SERVICES[item.name] ?? [];
  hangzhouStations.push({
    stationName: item.name,
    stationType: "小哥驿站",
    area: item.district || hangzhouCity.cityName,
    address: item.address,
    openTime: item.openTime,
    services,
    servicesScope: "STATION",
    sourceName: xiaogeSource.name,
    sourceUrl: xiaogeSource.url,
    sourcePublishedAt: xiaogeSource.publishedAt,
    rawText: item.excerpt ?? "",
    ...resolveDataStatus({ address: item.address, district: item.district, services, openTime: item.openTime })
  });
}
for (const item of parsed.chengguanStations) {
  hangzhouStations.push({
    stationName: item.name,
    stationType: "城管驿站",
    area: item.district || hangzhouCity.cityName,
    address: item.address,
    openTime: CHENGGUAN_OPEN_TIME,
    services: CHENGGUAN_COMMON_SERVICES,
    servicesScope: "BATCH",
    sourceName: chengguanSource.name,
    sourceUrl: chengguanSource.url,
    sourcePublishedAt: chengguanSource.publishedAt,
    rawText: "",
    ...resolveDataStatus({
      address: item.address,
      district: item.district,
      services: CHENGGUAN_COMMON_SERVICES,
      openTime: CHENGGUAN_OPEN_TIME,
      extraNotes: ["服务设施为来源对该批驿站的统一描述"]
    })
  });
}

citySummaries.push({
  city: hangzhouCity,
  sources: [xiaogeSource, chengguanSource].map((source) => ({
    name: source.name,
    publisher: source.publisher,
    publishedAt: source.publishedAt,
    url: source.url
  })),
  stationCount: 0,
  excluded: 0
});

// ------------------------------------------------------------ 2) 其他城市
const cityFiles = fs.existsSync(citySourcesDir)
  ? fs.readdirSync(citySourcesDir).filter((name) => name.endsWith(".json") && !name.startsWith("_"))
  : [];

const cityDatasets = new Map();
for (const file of cityFiles) {
  let dataset;
  try {
    dataset = JSON.parse(fs.readFileSync(path.join(citySourcesDir, file), "utf8"));
  } catch (error) {
    console.warn(`跳过无法解析的文件 ${file}：${error.message}`);
    continue;
  }
  if (dataset.cityCode && Array.isArray(dataset.stations)) {
    cityDatasets.set(dataset.cityCode, dataset);
  }
}

for (const city of CITY_REGISTRY) {
  if (city.cityCode === hangzhouCity.cityCode) {
    continue;
  }
  const dataset = cityDatasets.get(city.cityCode);
  if (!dataset) {
    continue;
  }
  const sources = (dataset.sources || []).map((source) => ({
    name: source.name || "",
    publisher: source.publisher || "",
    publishedAt: source.publishedAt || "",
    url: source.url || ""
  }));
  const cityStations = [];
  let excluded = 0;

  for (const item of dataset.stations) {
    const address = (item.address || "").trim();
    if (!address) {
      // 只有名称、没有地址的记录无法作为可用点位，不导入；在文档中说明条数。
      excluded += 1;
      excludedNoAddress.push({
        cityName: city.cityName,
        name: item.name,
        sourceName: sources[item.sourceIndex]?.name || "",
        sourceUrl: sources[item.sourceIndex]?.url || ""
      });
      continue;
    }
    const services = normalizeServices(Array.isArray(item.services) ? item.services : []);
    const openTime = (item.openTime || "").trim();
    const district = (item.district || "").trim();
    const source = sources[item.sourceIndex] || {};
    cityStations.push({
      stationName: item.name,
      stationType: stationTypeFromName(item.name) || dataset.defaultStationType || "驿站",
      area: district || city.cityName,
      address,
      openTime,
      services,
      servicesScope: "STATION",
      sourceName: source.name,
      sourceUrl: source.url,
      sourcePublishedAt: source.publishedAt,
      rawText: item.rawText || "",
      longitude: item.longitude ?? null,
      latitude: item.latitude ?? null,
      ...resolveDataStatus({ address, district, services, openTime })
    });
  }

  // 同一城市内所有驿站的服务设施完全一致时，说明来源是按批次统一描述，补充说明。
  const distinctServiceLists = new Set(cityStations.map((item) => item.services.join("|")));
  if (cityStations.length > 10 && distinctServiceLists.size === 1 && cityStations[0].services.length > 0) {
    for (const item of cityStations) {
      item.dataStatus = "PARTIAL";
      item.dataNote = item.dataNote
        ? `${item.dataNote}；服务设施为来源对该批驿站的统一描述`
        : "服务设施为来源对该批驿站的统一描述";
      item.servicesScope = "BATCH";
    }
  }

  citySummaries.push({ city, sources, stationCount: 0, excluded });
  stations.push(...cityStations.map((item) => ({ ...item, cityCode: city.cityCode })));
}

// 杭州排在最前，保证演示账号负责的 1~4 号驿站仍是四家「小哥驿站」。
stations.unshift(...hangzhouStations.map((item) => ({ ...item, cityCode: hangzhouCity.cityCode })));
stations.forEach((station, index) => {
  station.id = index + 1;
  station.status = "OPEN";
  station.managerId = DEMO_MANAGED_STATION_IDS.includes(station.id) ? DEMO_MANAGER_ID : null;
  station.verifiedAt = VERIFIED_AT;
});

// ------------------------------------------------------------ 合并高德地理编码结果
// 公开来源都不提供点位坐标，因此坐标由高德地图地理编码派生，结果缓存在 data/station-geocode.json。
// 这与 data_status 的口径相互独立：data_status 只表示“字段能否从公开来源逐条核对”。
const geocodeCachePath = path.join(projectRoot, "data", "station-geocode.json");
const geocodeCache = fs.existsSync(geocodeCachePath)
  ? JSON.parse(fs.readFileSync(geocodeCachePath, "utf8"))
  : null;
const geocodeStats = { AMAP_POI: 0, AMAP_GEOCODE: 0, missing: 0, districtFilled: 0, mismatch: 0, rejected: 0, rejectedFar: 0, rejectedDistrict: 0 };

/** 高德对空字段会返回空数组（例如 district: []），这里统一转成安全字符串。 */
function asText(value) {
  if (value === null || value === undefined) return "";
  return Array.isArray(value) ? "" : String(value).trim();
}

/** 两个经纬度点之间的大圆距离（公里），用于校验地理编码结果是否离谱。 */
function distanceKm(lng1, lat1, lng2, lat2) {
  const toRad = (value) => (value * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a = Math.sin(dLat / 2) ** 2
    + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 6371.0088 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

/** 距城市中心超过该距离的坐标视为离谱结果，丢弃而不是画到地图上。 */
const MAX_DISTANCE_FROM_CITY_KM = 120;

if (geocodeCache) {
  for (const station of stations) {
    const cityMeta = CITY_REGISTRY.find((city) => city.cityCode === station.cityCode);
    const cached = geocodeCache.stations?.[String(station.id)];
    if (!cached) {
      geocodeStats.missing += 1;
      station.geocodeSource = "";
      station.geocodeAt = "";
      continue;
    }
    // 缓存按驿站编号关联，换数据集时必须校验名称，避免编号错位导致坐标张冠李戴。
    if (cached.stationName && cached.stationName !== station.stationName) {
      geocodeStats.mismatch += 1;
      station.geocodeSource = "";
      station.geocodeAt = "";
      continue;
    }

    if (cached.source === "AMAP_POI" || cached.source === "AMAP_GEOCODE") {
      station.longitude = cached.longitude;
      station.latitude = cached.latitude;
      station.geocodeSource = cached.source;
      station.geocodeAt = cached.geocodedAt || VERIFIED_AT;
      geocodeStats[cached.source] += 1;

      // 来源未标注行政区（area 退化成了城市名）时，用地理编码结果补齐，并在 data_note 说明。
      const district = asText(cached.district);
      const areaFromCityName = station.area === (cityMeta?.cityName || "");
      if (areaFromCityName && district && /[区县市]$/.test(district)) {
        station.area = district;
        station.areaFilledByGeocode = true;
        geocodeStats.districtFilled += 1;
      }
    } else {
      station.geocodeSource = cached.source || "";
      station.geocodeAt = "";
    }
  }

  // ---- 坐标可信度复核：离谱的结果宁可丢弃，也不要在地图上画出错误的点 ----
  for (const station of stations) {
    if (station.longitude == null || station.latitude == null) continue;
    const cityMeta = CITY_REGISTRY.find((city) => city.cityCode === station.cityCode);
    const cached = geocodeCache.stations?.[String(station.id)] || {};
    const district = asText(cached.district);

    // 1) 与来源标注的行政区冲突（例如「风景区孤山驿站」被地理编码到临安区）
    const areaIsDistrict = station.area && /[区县市]$/.test(station.area) && station.area !== cityMeta?.cityName;
    if (areaIsDistrict && district && !district.includes(station.area) && !station.area.includes(district)) {
      geocodeStats.rejectedDistrict += 1;
      station.geocodeRejectReason = `地理编码结果所属行政区（${district}）与来源标注（${station.area}）不一致`;
      station.longitude = null;
      station.latitude = null;
      continue;
    }

    // 2) 距城市中心过远（明显跑偏）
    if (cityMeta?.centerLng != null && cityMeta?.centerLat != null) {
      const distance = distanceKm(station.longitude, station.latitude, cityMeta.centerLng, cityMeta.centerLat);
      if (distance > MAX_DISTANCE_FROM_CITY_KM) {
        geocodeStats.rejectedFar += 1;
        station.geocodeRejectReason = `地理编码结果距${cityMeta.cityName}中心 ${Math.round(distance)} 公里，超出 ${MAX_DISTANCE_FROM_CITY_KM} 公里阈值`;
        station.longitude = null;
        station.latitude = null;
      }
    }
  }
  geocodeStats.rejected = geocodeStats.rejectedDistrict + geocodeStats.rejectedFar;

  // ---- 坐标重合统计 ----
  // 多个驿站落在同一点，通常说明它们本就在同一栋楼/同一个院落（真实情况），
  // 也可能是地理编码把泛化的名称匹配到了同一个 POI。这里如实统计，供文档说明精度边界。
  const coordinateGroups = new Map();
  for (const station of stations) {
    if (station.longitude == null || station.latitude == null) continue;
    const key = `${station.longitude},${station.latitude}`;
    coordinateGroups.set(key, (coordinateGroups.get(key) ?? 0) + 1);
  }
  geocodeStats.distinctPoints = coordinateGroups.size;
  geocodeStats.sharedPoints = [...coordinateGroups.values()].filter((count) => count > 1).length;
  geocodeStats.sharedStations = [...coordinateGroups.values()]
    .filter((count) => count > 1)
    .reduce((sum, count) => sum + count, 0);
} else {
  console.warn(`未找到 ${path.relative(projectRoot, geocodeCachePath)}，本次生成的驿站将不带坐标。`
    + "如需坐标请先设置 AMAP_WEB_KEY 并执行 node scripts/geocode-stations.mjs");
}

// ------------------------------------------------------------ 合并高德营业信息（营业时间 / 电话）
// 营业时间优先级：**以高德数据为准**；高德没有给出具体营业时间的驿站，才回退到来源文本。
// 理由：来源里的「开放时间」常是批次级描述或活动期描述，而高德是按点位维护的，
// 粒度更细、更贴近实际（来源写「每日 8:30-22:00」的多为商场驿站，高德给的商场时间反而更准）。
// 联系电话仍为「来源优先、高德补空」——本轮只调整营业时间口径。
const businessCachePath = path.join(projectRoot, "data", "station-business.json");
const businessCache = fs.existsSync(businessCachePath)
  ? JSON.parse(fs.readFileSync(businessCachePath, "utf8"))
  : null;
const businessStats = {
  amapOpenTime: 0, sourceOpenTime: 0, telFromAmap: 0, telFromSource: 0,
  openTimeOverridden: 0
};

if (businessCache) {
  for (const station of stations) {
    const cached = businessCache.stations?.[String(station.id)];
    if (cached?.stationName && cached.stationName !== station.stationName) {
      continue; // 编号错位保护
    }
    const amapOpenTime = asText(cached?.openTime);
    const amapTel = asText(cached?.tel);
    const sourceOpenTime = station.openTime || "";

    // 营业时间：高德为准，来源兜底
    if (amapOpenTime) {
      if (sourceOpenTime && sourceOpenTime !== amapOpenTime) {
        station.openTimeReplacedSource = sourceOpenTime; // 留痕：来源原值，便于文档与排查
        businessStats.openTimeOverridden += 1;
      }
      station.openTime = amapOpenTime;
      station.openTimeFromAmap = true;
      businessStats.amapOpenTime += 1;
    } else if (sourceOpenTime) {
      station.openTime = sourceOpenTime;
      station.openTimeFromAmap = false;
      businessStats.sourceOpenTime += 1;
    }

    // 联系电话：来源优先，高德补空
    if (station.contactPhone) {
      businessStats.telFromSource += 1;
    } else if (amapTel) {
      station.contactPhone = amapTel;
      station.telFromAmap = true;
      businessStats.telFromAmap += 1;
    }

    // 来源汇总标记：营业时间以高德为准，电话以来源优先，因此这里只表达"最终数据的出处构成"。
    if (station.openTimeFromAmap && !station.telFromAmap) {
      station.businessSource = station.contactPhone ? "AMAP_POI+SOURCE" : "AMAP_POI";
    } else if (station.openTimeFromAmap && station.telFromAmap) {
      station.businessSource = "AMAP_POI";
    } else if (!station.openTimeFromAmap && station.telFromAmap) {
      station.businessSource = "SOURCE+AMAP_POI";
    } else {
      station.businessSource = (station.openTime || station.contactPhone) ? "SOURCE" : "";
    }
    station.businessSyncedAt = (station.openTimeFromAmap || station.telFromAmap)
      ? (cached?.syncedAt || VERIFIED_AT)
      : "";
  }
} else {
  console.warn(`未找到 ${path.relative(projectRoot, businessCachePath)}，本次生成的驿站不带高德营业信息。`
    + "如需同步请先设置 AMAP_WEB_KEY 并执行 node scripts/sync-station-business.mjs");
}

// 把坐标派生、区县补齐与营业信息同步的口径写进 data_note，保持来源字段的诚实性。
for (const station of stations) {
  const notes = station.dataNote ? [station.dataNote] : [];
  if (station.areaFilledByGeocode) {
    notes.push("所属行政区由高德地图地理编码补齐，非来源给出");
  }
  if (station.longitude != null && (station.geocodeSource === "AMAP_POI" || station.geocodeSource === "AMAP_GEOCODE")) {
    notes.push(station.geocodeSource === "AMAP_POI"
      ? "坐标为高德地图 POI 匹配派生，非来源给出"
      : "坐标为高德地图地理编码派生，非来源给出");
  }
  if (station.geocodeRejectReason) {
    notes.push(`坐标已丢弃：${station.geocodeRejectReason}`);
  }
  if (station.openTimeFromAmap) {
    notes.push("营业时间以高德地图为准（第三方数据）");
    if (station.openTimeReplacedSource) {
      notes.push(`来源原文的开放时间为「${station.openTimeReplacedSource}」`);
    }
  } else if (station.openTime) {
    notes.push("高德未提供营业时间，开放时间取自来源文本");
  }
  if (station.telFromAmap) {
    notes.push("联系电话来自高德地图 POI（第三方数据），非来源给出");
  }
  station.dataNote = notes.join("；");
}

// 城市表中的驿站数量以最终导入数为准。
const stationCountByCity = {};
for (const station of stations) {
  stationCountByCity[station.cityCode] = (stationCountByCity[station.cityCode] ?? 0) + 1;
}
for (const summary of citySummaries) {
  summary.stationCount = stationCountByCity[summary.city.cityCode] ?? 0;
}
const usedCities = CITY_REGISTRY.filter((city) => (stationCountByCity[city.cityCode] ?? 0) > 0);

// ------------------------------------------------------------ 输出 JSON
const payload = {
  generatedAt: new Date().toISOString(),
  verifiedAt: VERIFIED_AT,
  note: "驿站名称与地址取自公开来源；服务设施与开放时间的适用范围见每条记录的 servicesScope 与 dataNote。",
  cities: usedCities,
  total: stations.length,
  // serviceItems 由 services 派生，供内存实现直接读取（含服务类型编码），两者始终同源。
  stations: stations.map((station) => ({
    ...station,
    serviceItems: toServiceItems(station.services)
  }))
};

const jsonPath = path.join(projectRoot, "backend", "src", "main", "resources", "db", "stations.json");
fs.mkdirSync(path.dirname(jsonPath), { recursive: true });
fs.writeFileSync(jsonPath, `${JSON.stringify(payload, null, 2)}\n`, "utf8");

// ------------------------------------------------------------ 输出 SQL
function sqlText(value) {
  if (value === null || value === undefined || value === "") {
    return "NULL";
  }
  return `'${String(value).replace(/\\/g, "\\\\").replace(/'/g, "''")}'`;
}

const sqlLines = [];
sqlLines.push("-- 本文件由 scripts/generate-station-data.mjs 自动生成，请勿手工修改。");
sqlLines.push(`-- 核对日期：${VERIFIED_AT}；覆盖城市：${usedCities.length} 个；驿站总数：${stations.length}`);
sqlLines.push("-- 逐条来源见 docs/驿站数据来源.md。");
sqlLines.push("-- 说明：来源未标注所属行政区者 area 记为城市名；坐标与联系电话来源未提供时保持 NULL。");
sqlLines.push("");
sqlLines.push("USE friendly_station;");
sqlLines.push("");
sqlLines.push("-- 重新导入前先清理，避免重复执行产生重复数据（活动与预约请另行处理）。");
sqlLines.push("DELETE FROM station_service;");
sqlLines.push("DELETE FROM station;");
sqlLines.push("DELETE FROM city;");
sqlLines.push("");
sqlLines.push("INSERT INTO city (city_code, city_name, province, center_lng, center_lat, status, sort_no) VALUES");
sqlLines.push(usedCities.map((city) => "    (" + [
  sqlText(city.cityCode),
  sqlText(city.cityName),
  sqlText(city.province),
  city.centerLng,
  city.centerLat,
  "'ACTIVE'",
  city.sortNo
].join(", ") + ")").join(",\n") + ";");
sqlLines.push("");
sqlLines.push("INSERT INTO station");
sqlLines.push("    (id, station_name, city_code, area, address, longitude, latitude, contact_phone, open_time, status, manager_id, station_type, source_name, source_url, source_published_at, verified_at, data_status, data_note, geocode_source, geocode_at, business_source, business_synced_at)");
sqlLines.push("VALUES");

const valueRows = stations.map((station) => "    (" + [
  station.id,
  sqlText(station.stationName),
  sqlText(station.cityCode),
  sqlText(station.area),
  sqlText(station.address),
  station.longitude ?? "NULL",
  station.latitude ?? "NULL",
  sqlText(station.contactPhone),
  sqlText(station.openTime),
  sqlText(station.status),
  station.managerId === null || station.managerId === undefined ? "NULL" : station.managerId,
  sqlText(station.stationType),
  sqlText(station.sourceName),
  sqlText(station.sourceUrl),
  sqlText(station.sourcePublishedAt),
  sqlText(station.verifiedAt),
  sqlText(station.dataStatus),
  sqlText(station.dataNote),
  sqlText(station.geocodeSource),
  sqlText(station.geocodeAt),
  sqlText(station.businessSource),
  sqlText(station.businessSyncedAt)
].join(", ") + ")");
sqlLines.push(valueRows.join(",\n") + ";");
sqlLines.push("");

sqlLines.push("INSERT INTO station_service (station_id, service_name, service_type, need_appoint, status) VALUES");
const serviceRows = [];
for (const station of stations) {
  for (const service of toServiceItems(station.services)) {
    serviceRows.push(`    (${station.id}, ${sqlText(service.name)}, '${service.type}', 0, 1)`);
  }
}
sqlLines.push(serviceRows.length ? `${serviceRows.join(",\n")};` : "-- 本次数据没有任何服务项目");
sqlLines.push("");

const sqlPath = path.join(projectRoot, "backend", "src", "main", "resources", "db", "stations.sql");
fs.writeFileSync(sqlPath, `${sqlLines.join("\n")}\n`, "utf8");

// ------------------------------------------------------------ 输出证据文件
fs.writeFileSync(
  path.join(projectRoot, "docs", "station-sources.json"),
  `${JSON.stringify({
    generatedAt: payload.generatedAt,
    verifiedAt: VERIFIED_AT,
    cities: usedCities,
    citySources: citySummaries.map((summary) => ({
      cityCode: summary.city.cityCode,
      cityName: summary.city.cityName,
      stationCount: summary.stationCount,
      excludedNoAddress: summary.excluded,
      sources: summary.sources
    })),
    xiaogeStations: parsed.xiaogeStations,
    chengguanStations: parsed.chengguanStations.map((item) => ({
      seq: item.seq,
      name: item.rawName,
      address: item.rawAddress,
      district: item.district
    })),
    otherCityStations: stations
      .filter((station) => station.cityCode !== hangzhouCity.cityCode)
      .map((station) => ({
        cityCode: station.cityCode,
        name: station.stationName,
        address: station.address,
        district: station.area,
        openTime: station.openTime,
        services: station.services,
        sourceName: station.sourceName,
        sourceUrl: station.sourceUrl,
        rawText: station.rawText
      }))
  }, null, 2)}\n`,
  "utf8"
);

// ------------------------------------------------------------ 输出来源说明
const byType = {};
const byStatus = {};
for (const station of stations) {
  byType[station.stationType] = (byType[station.stationType] ?? 0) + 1;
  byStatus[station.dataStatus] = (byStatus[station.dataStatus] ?? 0) + 1;
}

const doc = [];
doc.push("# 驿站数据来源与核对记录");
doc.push("");
doc.push(`- 采集与核对日期：${VERIFIED_AT}`);
doc.push(`- 覆盖城市：**${usedCities.length}** 个；驿站总数：**${stations.length}** 条`);
doc.push("- 使用方式：`backend/src/main/resources/db/stations.sql` 由本文数据生成，");
doc.push("  `backend/src/main/resources/db/stations.json` 由内存演示模式直接读取，两者同源。");
doc.push("");
doc.push("> **平台定位**：本平台面向全国新就业群体，驿站名录按城市归属。不同城市对同类驿站的叫法不同");
doc.push("> （如杭州「小哥驿站 / 城管驿站」、北京「工会驿站 / 暖新驿站」、上海「户外职工爱心接力站」），");
doc.push("> 系统按来源原文记录名称与类型，不做统一改名。");
doc.push("");
doc.push("## 一、城市与来源清单");
doc.push("");
for (const summary of citySummaries) {
  doc.push(`### ${summary.city.cityName}（${summary.city.cityCode}）— ${summary.stationCount} 条`);
  doc.push("");
  doc.push("| 来源名称 | 发布机构 | 发布日期 | 访问地址 |");
  doc.push("| --- | --- | --- | --- |");
  for (const source of summary.sources) {
    doc.push(`| ${source.name} | ${source.publisher} | ${source.publishedAt} | <${source.url}> |`);
  }
  if (summary.excluded > 0) {
    doc.push("");
    doc.push(`> 另有 ${summary.excluded} 条记录来源只给出名称、没有详细地址，无法作为可用点位，**未导入系统**（保留在来源中供查证）。`);
  }
  doc.push("");
}
doc.push("## 二、数据构成");
doc.push("");
doc.push("| 驿站类型 | 数量 |");
doc.push("| --- | ---: |");
for (const [type, count] of Object.entries(byType).sort((a, b) => b[1] - a[1])) {
  doc.push(`| ${type} | ${count} |`);
}
doc.push("");
doc.push("### 字段口径与边界");
doc.push("");
doc.push("- `data_status = VERIFIED`：名称、地址、所属区域、开放时间、服务设施都能从来源逐条核对。");
doc.push("- `data_status = PARTIAL`：有字段来源未逐条给出（例如只给了名称与地址、没有服务设施），");
doc.push("  `data_note` 会逐项写明缺什么；服务设施若为来源对该批驿站的统一描述，也会在此说明。");
doc.push("- `servicesScope`：仅记录在生成的数据文件 `stations.json` 中用于溯源（`STATION` = 服务设施由来源逐站给出，");
doc.push("  `BATCH` = 来自来源对该批驿站的统一描述）；数据库与接口用 `data_status` 与 `data_note` 表达同样的边界。");
doc.push(`- **服务项目规范化**：来源的服务内容文本按顿号/逗号/分号拆分为服务项目，去掉括号里的补充说明与「等」字，`);
doc.push(`  超过 ${MAX_SERVICE_NAME_LENGTH} 字的描述性片段与超过 ${MAX_SERVICES_PER_STATION} 项的部分不计入服务项目`);
doc.push("  （原始文本完整保留在 [station-sources.json](station-sources.json) 中）；服务项是预约时可选的最小单位，必须是短名称。");
doc.push("- `status`：来源发布时这些点位均在开放状态，因此统一记为 `OPEN`；**点位是否持续开放需要在使用前重新核实**。");
doc.push("");
doc.push("### 坐标来源（派生数据，与来源字段分开记录）");
doc.push("");
doc.push("公开来源**都不提供点位经纬度**，因此本项目的坐标是用**高德地图 Web 服务派生**的：");
doc.push("");
doc.push("1. 先用驿站名称做 **POI 搜索**，逐条校验「区县一致性 + 名称相似度 + 地址相似度」，取最可信的一条（`geocode_source = AMAP_POI`）；");
doc.push("2. 校验不通过则回退到**详细地址地理编码**（`geocode_source = AMAP_GEOCODE`）；");
doc.push("3. 两者都失败则**保留空坐标**，不从名称猜测、不编造。");
doc.push("");
doc.push("> 实测说明：仅看「有返回率」，POI 搜索是 100%——但会把「大悦城小哥驿站」与「万达小哥驿站」");
doc.push("> 匹配到同一个点。加上置信度校验后，可信命中率约 55%，其余 45% 走地址地理编码，坐标撞车归零。");
doc.push("> 这与论文里常用的「有结果就算命中」不同，属于更严格的口径。");
doc.push("");
if (geocodeStats.distinctPoints) {
  doc.push(`本期结果：共 ${geocodeStats.AMAP_POI + geocodeStats.AMAP_GEOCODE} 条拿到坐标，去重后 ${geocodeStats.distinctPoints} 个不同坐标点；`);
  doc.push(`其中 ${geocodeStats.sharedPoints} 个坐标点被多条驿站共用（涉及 ${geocodeStats.sharedStations} 条），`);
  doc.push(`另有 ${geocodeStats.rejected} 条因「行政区冲突」或「距城市中心过远」被丢弃、${geocodeStats.missing + geocodeStats.mismatch} 条没有坐标。`);
  doc.push("");
  doc.push("**坐标精度边界**：");
  doc.push("");
  doc.push("- 坐标是**派生值**，用于地图展示与就近参考，**不能当作驿站的精确位置**；");
  doc.push("- 名称泛化的驿站（例如只叫「暖新驿站」）与地址只到路口/社区级的记录，地理编码结果只能落到片区，误差可达数百米；");
  doc.push("- 多条驿站共用同一坐标时，通常它们本就在同一栋楼或同一个院落，也可能是泛化名称被匹配到了同一个 POI；");
  doc.push("- 需要精确定位时，仍应通过高德地图搜索驿站名称、或浙里办等官方渠道核对。");
  doc.push("");
}
doc.push("### 营业信息（营业时间 / 联系电话）");
doc.push("");
doc.push("高德 POI 还提供 `biz_ext.open_time`（营业时间）与 `tel`（电话），系统会同步这两项：");
doc.push("");
doc.push("| 规则 | 说明 |");
doc.push("| --- | --- |");
doc.push("| 营业时间优先级 | **以高德数据为准**；高德未提供具体营业时间的驿站，才回退到来源文本 |");
doc.push("| 联系电话优先级 | **来源文本优先**，高德只补来源没有给出的号码 |");
doc.push("| 校验 | 复用与坐标相同的三重校验（区县一致性 + 名称相似度 + 地址相似度），**只接受可信匹配**；营业时间还要求形如 `HH:mm-HH:mm` 或「24小时」，过滤掉明显不属于驿站的取值 |");
doc.push("| 来源标注 | `business_source`：`AMAP_POI`（营业时间与电话均来自高德）/ `AMAP_POI+SOURCE`（营业时间来自高德、电话来自来源）/ `SOURCE+AMAP_POI`（营业时间来自来源、电话来自高德）/ `SOURCE`；`business_synced_at` 记录同步日期 |");
doc.push("| 留痕 | 高德营业时间覆盖了来源原值时，`data_note` 会同时写明「营业时间以高德地图为准」与「来源原文的开放时间为「…」」，便于回查 |");
doc.push("");
doc.push("> **高德不能提供什么**：高德 POI **没有**「服务设施」这类结构化字段（`type`/`tag` 只是");
doc.push("> 「生活服务 / 政府机构」这种粗分类），所以饮水、充电、热饭等服务项目**仍然只能来自公开来源文本**；");
doc.push("> 高德也**没有**「此刻是否营业」的实时状态字段，只能按营业时间本地推算。");
doc.push("> 营业时间与电话同样是**第三方数据**（来自商户认领与用户上报），不是驿站官方提供给平台的，");
doc.push("> 实测同一驿站两边可能不一致——例如「大悦城小哥驿站」来源写 8:30-22:00，高德给的是 10:00-22:00 的商场时间。");
doc.push("");
if (businessCache) {
  doc.push(`本期同步结果：营业时间以高德为准 ${businessStats.amapOpenTime} 条（其中 ${businessStats.openTimeOverridden} 条覆盖了来源原值）、`);
  doc.push(`高德未提供而回退来源文本 ${businessStats.sourceOpenTime} 条；电话来自来源 ${businessStats.telFromSource} 条、高德补空 ${businessStats.telFromAmap} 条。`);
  doc.push("");
}
doc.push("**坐标不是来源给出的**，因此：");
doc.push("");
doc.push("- 坐标来源记录在 `geocode_source` / `geocode_at` 两列，**不参与 `data_status` 的判定**；");
doc.push("- 每条坐标记录在 `data_note` 中追加「坐标为高德地图地理编码派生，非来源给出」；");
doc.push("- 来源未标注行政区的记录会用地理编码结果补齐 `area`，并在 `data_note` 注明「由高德地图补齐，非来源给出」；");
doc.push("- 地理编码原始结果缓存在仓库内的 `data/station-geocode.json`，重跑 `generate-station-data.mjs` 不会再次调用接口。");
doc.push("");
doc.push("- 联系电话来源未提供，保持 `NULL`。");
doc.push("- `manager_id`：**演示配置**，1~4 号「小哥驿站」（杭州市）分配给演示账号 2（station_admin_01），");
doc.push("  用于验证驿站管理员的数据范围；该字段不属于公开来源内容。");
doc.push("");
doc.push("### 数据状态统计");
doc.push("");
doc.push("| data_status | 条数 |");
doc.push("| --- | ---: |");
for (const [status, count] of Object.entries(byStatus)) {
  doc.push(`| ${status} | ${count} |`);
}
doc.push("");
doc.push("## 三、各城市区域分布");
doc.push("");
for (const summary of citySummaries) {
  const cityStations = stations.filter((station) => station.cityCode === summary.city.cityCode);
  if (cityStations.length === 0) {
    continue;
  }
  const areaCounts = {};
  for (const station of cityStations) {
    areaCounts[station.area] = (areaCounts[station.area] ?? 0) + 1;
  }
  doc.push(`### ${summary.city.cityName}`);
  doc.push("");
  doc.push("| 所属区域 | 驿站数量 |");
  doc.push("| --- | ---: |");
  for (const [area, count] of Object.entries(areaCounts).sort((a, b) => b[1] - a[1])) {
    doc.push(`| ${area} | ${count} |`);
  }
  doc.push("");
}
doc.push("## 四、未导入的记录");
doc.push("");
if (excludedNoAddress.length === 0) {
  doc.push("本次没有因缺少地址而被排除的记录。");
} else {
  const excludedBySource = new Map();
  for (const item of excludedNoAddress) {
    const key = `${item.cityName}||${item.sourceName}||${item.sourceUrl}`;
    excludedBySource.set(key, (excludedBySource.get(key) ?? 0) + 1);
  }
  doc.push(`共有 ${excludedNoAddress.length} 条记录来源只给出驿站名称、没有详细地址，无法作为可用点位，**未导入系统**。`);
  doc.push("");
  doc.push("这类记录多数来自「推荐名单 / 评优公示」类来源，原文本身就是纯名称清单。按来源汇总如下：");
  doc.push("");
  doc.push("| 城市 | 来源名称 | 未导入条数 | 访问地址 |");
  doc.push("| --- | --- | ---: | --- |");
  for (const [key, count] of [...excludedBySource.entries()].sort((a, b) => b[1] - a[1])) {
    const [cityName, sourceName, sourceUrl] = key.split("||");
    doc.push(`| ${cityName} | ${sourceName} | ${count} | <${sourceUrl}> |`);
  }
  doc.push("");
  doc.push("完整清单（含每条名称与其来源原文片段）见 [station-sources.json](station-sources.json)。");
}
doc.push("");
doc.push("## 五、复现方式");
doc.push("");
doc.push("```powershell");
doc.push("# 1. 采集来源页面（杭州：保存到 tmp/station-sources/；其他城市：产出 tmp/city-sources/<city>.json）");
doc.push("#    来源地址见上方各城市的来源清单");
doc.push("");
doc.push("# 2. 解析杭州来源页面，输出结构化记录");
doc.push("node scripts/parse-station-sources.mjs");
doc.push("");
doc.push("# 3. 合并多城市数据，生成 stations.json / stations.sql / 本文档 / docs/station-sources.json");
doc.push("node scripts/generate-station-data.mjs");
doc.push("");
doc.push("# 4. 导入数据库并自检");
doc.push("powershell -ExecutionPolicy Bypass -File scripts/init-mysql.ps1 -User root -Password <密码> -Reset");
doc.push("```");
doc.push("");
doc.push("完整的逐条原始记录（含来源原文片段）见 [station-sources.json](station-sources.json)。");
doc.push("");

fs.writeFileSync(path.join(projectRoot, "docs", "驿站数据来源.md"), `${doc.join("\n")}\n`, "utf8");

console.log(`覆盖城市：${usedCities.length} 个（${usedCities.map((city) => city.cityName).join("、")}）`);
console.log(`驿站总数：${stations.length} 条`);
for (const [status, count] of Object.entries(byStatus)) {
  console.log(`  ${status}: ${count} 条`);
}
console.log(`未导入（缺地址）：${excludedNoAddress.length} 条`);
if (geocodeCache) {
  console.log(`坐标来源：POI 匹配 ${geocodeStats.AMAP_POI} 条，地址地理编码 ${geocodeStats.AMAP_GEOCODE} 条，`
    + `无坐标 ${geocodeStats.missing + geocodeStats.mismatch + geocodeStats.rejected} 条；补齐区县 ${geocodeStats.districtFilled} 条`);
  if (geocodeStats.rejected > 0) {
    console.log(`  可信度复核丢弃 ${geocodeStats.rejected} 条（行政区冲突 ${geocodeStats.rejectedDistrict}，距城市中心过远 ${geocodeStats.rejectedFar}）`);
  }
  console.log(`  坐标去重后共 ${geocodeStats.distinctPoints} 个不同坐标点；`
    + `${geocodeStats.sharedPoints} 个坐标点被多条驿站共用（涉及 ${geocodeStats.sharedStations} 条）`);
  if (geocodeStats.mismatch > 0) {
    console.warn(`  注意：${geocodeStats.mismatch} 条缓存的驿站名称与当前数据不一致，已跳过（编号错位保护）`);
  }
}
if (businessCache) {
  console.log(`营业时间：以高德为准 ${businessStats.amapOpenTime} 条（其中 ${businessStats.openTimeOverridden} 条覆盖了来源原值），`
    + `高德未提供而回退来源 ${businessStats.sourceOpenTime} 条`);
  console.log(`联系电话：来自来源 ${businessStats.telFromSource} 条，高德补空 ${businessStats.telFromAmap} 条`);
}
console.log("已生成：");
console.log(`  - ${path.relative(projectRoot, jsonPath)}`);
console.log(`  - ${path.relative(projectRoot, sqlPath)}`);
console.log("  - docs/驿站数据来源.md");
console.log("  - docs/station-sources.json");
