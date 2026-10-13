/**
 * 从公开来源页面解析驿站名录，输出结构化 JSON 供数据接入使用。
 *
 * 支持的来源：
 *   1. 杭州城管驿站开放名单（杭州网，2025-07-09）—— 序号 / 驿站名称 / 具体地址
 *   2. 杭州市委社会工作部「小哥驿站」公告（2025-05-27）—— 逐个驿站的地址、开放时间与服务设施
 *
 * 用法：node scripts/parse-station-sources.mjs [--out tmp/station-sources/parsed.json]
 * 说明：只做解析与规范化，不做任何推测性补全；无法从来源确定字段留空。
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const sourceDir = path.join(projectRoot, "tmp", "station-sources");

function readArg(name, fallback) {
  const index = process.argv.indexOf(`--${name}`);
  return index >= 0 && process.argv[index + 1] ? process.argv[index + 1] : fallback;
}

const outPath = path.resolve(projectRoot, readArg("out", "tmp/station-sources/parsed.json"));

/** 去掉标签、HTML 实体与多余空白。 */
function clean(html) {
  return html
    .replace(/<[^>]+>/g, "")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/\s+/g, " ")
    .trim();
}

/** 从地址中提取所属行政区（杭州市辖区）。 */
function districtOf(name, address) {
  const text = `${name} ${address}`;
  const districts = ["上城区", "拱墅区", "西湖区", "滨江区", "萧山区", "余杭区", "临平区", "钱塘区", "富阳区", "临安区", "桐庐县", "淳安县", "建德市"];
  for (const district of districts) {
    if (text.includes(district)) {
      return district;
    }
  }
  return "";
}

/** 解析杭州城管驿站名单：三列表格 序号 / 驿站名称 / 具体地址。 */
function parseChengguan() {
  const file = path.join(sourceDir, "chengguan-263.html");
  if (!fs.existsSync(file)) {
    return { error: `缺少来源文件：${file}` };
  }
  const html = fs.readFileSync(file, "utf8");
  const stations = [];
  const rowPattern = /<tr[^>]*>([\s\S]*?)<\/tr>/gi;
  let match;
  while ((match = rowPattern.exec(html)) !== null) {
    const cells = [...match[1].matchAll(/<td[^>]*>([\s\S]*?)<\/td>/gi)].map((cell) => clean(cell[1]));
    if (cells.length < 3) {
      continue; // 跳过表头
    }
    const index = Number(cells[0]);
    const name = cells[1];
    const address = cells[2];
    if (!Number.isFinite(index) || !name) {
      continue;
    }
    stations.push({
      seq: index,
      name,
      address,
      district: districtOf(name, address),
      rawName: cells[1],
      rawAddress: cells[2]
    });
  }
  return { file, count: stations.length, stations };
}

/** 解析「小哥驿站」公告正文中的四家驿站。 */
function parseXiaoge() {
  const file = path.join(sourceDir, "xiaoge-yizhan.html");
  if (!fs.existsSync(file)) {
    return { error: `缺少来源文件：${file}` };
  }
  const html = fs.readFileSync(file, "utf8");
  const text = clean(html);
  const names = ["钱江苑小哥驿站", "大悦城小哥驿站", "万达小哥驿站", "黄龙小哥驿站"];
  const stations = names.map((name) => {
    const start = text.indexOf(name);
    // 段落范围截止到下一个「小哥驿站」标题出现的位置（排除自身在正文中的重复提及）。
    const next = names
      .filter((other) => other !== name)
      .map((other) => text.indexOf(other, start + name.length))
      .filter((index) => index > start);
    const end = next.length ? Math.min(...next) : Math.min(text.length, start + 900);
    const block = start >= 0 ? text.slice(start, end) : "";
    const addressMatch = block.match(/位于([^，。；]{4,60}?)[，。；]/);
    const hoursMatch = block.match(/(\d{1,2}[:：]\d{2})\s*[—\-~至]\s*(\d{1,2}[:：]\d{2})/);
    const fullDay = block.includes("24小时");
    return {
      name,
      address: addressMatch ? addressMatch[1].trim() : "",
      openTime: fullDay
        ? "24 小时开放"
        : hoursMatch
          ? `每日 ${hoursMatch[1].replace(/[:：]/, ":")}-${hoursMatch[2].replace(/[:：]/, ":")}`
          : "",
      excerpt: block.slice(0, 400),
      district: districtOf(name, addressMatch ? addressMatch[1] : "")
    };
  });
  return { file, count: stations.length, stations };
}

function main() {
  const chengguan = parseChengguan();
  const xiaoge = parseXiaoge();

  const report = {
    generatedAt: new Date().toISOString(),
    sources: {
      chengguan: {
        name: "杭州城管驿站开放名单",
        publisher: "杭州网（杭州日报报业集团）",
        url: "https://hznews.hangzhou.com.cn/chengshi/content/2025-07/09/content_9035229.htm",
        publishedAt: "2025-07-09",
        description: "全市 263 座杭州城管驿站名单，含驿站名称与具体地址；每日 7:00-18:00 开放。"
      },
      xiaoge: {
        name: "杭州市政府 2025 年度民生实事「小哥驿站」公告",
        publisher: "中共杭州市委社会工作部",
        url: "https://www.hzshgzb.gov.cn/content_1001.html",
        publishedAt: "2025-05-27",
        description: "16 家新建「小哥驿站」中已建成投用的 4 家，含地址、开放时间与服务设施。"
      }
    },
    chengguanCount: chengguan.count ?? 0,
    xiaogeCount: xiaoge.count ?? 0,
    chengguanStations: chengguan.stations ?? [],
    xiaogeStations: xiaoge.stations ?? [],
    errors: [chengguan.error, xiaoge.error].filter(Boolean)
  };

  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  fs.writeFileSync(outPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");

  const districts = {};
  for (const station of report.chengguanStations) {
    const key = station.district || "未识别";
    districts[key] = (districts[key] ?? 0) + 1;
  }
  const missingAddress = report.chengguanStations.filter((s) => !s.address).length;
  const duplicateNames = report.chengguanStations.length - new Set(report.chengguanStations.map((s) => s.name)).size;

  console.log(`输出：${outPath}`);
  console.log(`城管驿站解析：${report.chengguanCount} 条（缺地址 ${missingAddress} 条，重名 ${duplicateNames} 条）`);
  console.log("行政区分布：", JSON.stringify(districts, null, 2));
  console.log(`小哥驿站解析：${report.xiaogeCount} 条`);
  for (const station of report.xiaogeStations) {
    console.log(`  - ${station.name} | ${station.address || "（未解析到地址）"} | ${station.openTime || "（未解析到时间）"} | ${station.district}`);
  }
  if (report.errors.length) {
    console.log("错误：", report.errors);
  }
  console.log("\n前 5 条城管驿站样例：");
  report.chengguanStations.slice(0, 5).forEach((s) => console.log(`  ${s.seq}. ${s.name} | ${s.address} | ${s.district}`));
}

main();
