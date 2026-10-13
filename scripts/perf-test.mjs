/**
 * 性能与并发测试：对只读接口做并发压测，对写入路径做并发正确性校验，并生成 Markdown 报告。
 *
 * 用法：node scripts/perf-test.mjs [--api http://127.0.0.1:8080] [--requests 60] [--concurrency 10]
 * 说明：本地单实例开发环境，结果用于验证实现的并发正确性与响应量级，不代表生产容量规划。
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

function readArg(name, fallback) {
  const index = process.argv.indexOf(`--${name}`);
  return index >= 0 && process.argv[index + 1] ? process.argv[index + 1] : fallback;
}

const apiBase = readArg("api", "http://127.0.0.1:8080").replace(/\/$/, "");
const totalRequests = Number(readArg("requests", 60));
const concurrency = Number(readArg("concurrency", 10));
const reportPath = path.join(projectRoot, "docs", "性能与并发测试报告.md");

async function login(username, password = "password") {
  const response = await fetch(`${apiBase}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password })
  });
  const body = await response.json();
  if (body.code !== 200) throw new Error(`登录失败：${username} -> ${body.message}`);
  return body.data.token;
}

async function call(method, url, token, payload) {
  const started = performance.now();
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (payload !== undefined) headers["Content-Type"] = "application/json";
  let status = 0;
  let body = null;
  try {
    const response = await fetch(`${apiBase}${url}`, {
      method,
      headers,
      body: payload === undefined ? undefined : JSON.stringify(payload)
    });
    status = response.status;
    const text = await response.text();
    body = text ? JSON.parse(text) : null;
  } catch (error) {
    body = { message: error.message };
  }
  return { status, body, elapsed: performance.now() - started };
}

/** 以固定并发度跑完指定请求数，返回每次请求的耗时与状态。 */
async function runLoad(name, method, url, token, requests, parallel) {
  const samples = [];
  let cursor = 0;
  async function worker() {
    while (cursor < requests) {
      cursor += 1;
      samples.push(await call(method, url, token));
    }
  }
  const started = performance.now();
  await Promise.all(Array.from({ length: parallel }, worker));
  const wall = performance.now() - started;

  const latencies = samples.map((item) => item.elapsed).sort((a, b) => a - b);
  const pick = (ratio) => latencies[Math.min(latencies.length - 1, Math.floor(latencies.length * ratio))];
  const failures = samples.filter((item) => item.status !== 200).length;

  return {
    name,
    method,
    url,
    requests,
    parallel,
    failures,
    wallMs: wall,
    qps: (requests / wall) * 1000,
    min: latencies[0],
    p50: pick(0.5),
    p95: pick(0.95),
    p99: pick(0.99),
    max: latencies[latencies.length - 1],
    avg: latencies.reduce((sum, value) => sum + value, 0) / latencies.length
  };
}

function format(number, digits = 1) {
  return Number(number).toFixed(digits);
}

/** 用于生成活动标题的时间戳，避免同名。 */
function stampOf(date) {
  return `${date.getFullYear()}${String(date.getMonth() + 1).padStart(2, "0")}${String(date.getDate()).padStart(2, "0")}${String(date.getHours()).padStart(2, "0")}${String(date.getMinutes()).padStart(2, "0")}${String(date.getSeconds()).padStart(2, "0")}`;
}

async function main() {
  const adminToken = await login("platform_admin");
  const userToken = await login("rider_xiaowang");

  const stationPage = await call("GET", "/api/station/page?current=1&size=1", null);
  const stationId = stationPage.body?.data?.records?.[0]?.id;
  if (!stationId) throw new Error("未取到驿站数据，请先初始化演示数据");

  console.log(`目标服务：${apiBase}`);
  console.log(`压测参数：每个接口 ${totalRequests} 次请求，并发 ${concurrency}\n`);

  const loadTargets = [
    ["统计概览", "GET", "/api/stat/overview", null],
    ["驿站分页查询", "GET", "/api/station/page?current=1&size=10", null],
    ["驿站详情", "GET", `/api/station/${stationId}`, null],
    ["资讯分页查询", "GET", "/api/news/page?current=1&size=10", null],
    ["活动分页查询", "GET", "/api/activity/page?current=1&size=10", null],
    ["诉求后台分页", "GET", "/api/appeal/admin/page?current=1&size=10", adminToken]
  ];

  console.log("[1] 只读接口并发压测");
  const loadResults = [];
  for (const [name, method, url, token] of loadTargets) {
    const result = await runLoad(name, method, url, token, totalRequests, concurrency);
    loadResults.push(result);
    console.log(
      `  ${name.padEnd(14)} 成功率 ${result.requests - result.failures}/${result.requests}  ` +
      `平均 ${format(result.avg)}ms  P95 ${format(result.p95)}ms  最大 ${format(result.max)}ms  ` +
      `吞吐 ${format(result.qps)} req/s`
    );
  }

  console.log("\n[2] 写入路径并发正确性");

  // 2.1 同一用户并发报名同一活动，应只有一次成功。
  // 每次运行都新建一个专用活动，保证「首次报名」的并发语义可重复验证。
  const testActivity = await call("POST", "/api/activity/admin", adminToken, {
    title: `并发报名测试活动 ${stampOf(new Date())}`,
    stationId,
    activityTime: "2026-12-31 09:00:00",
    address: "并发测试地点",
    maxCount: 100,
    content: "由 scripts/perf-test.mjs 自动创建，用于验证并发报名互斥。"
  });
  const activityId = testActivity.body?.data?.id;
  if (!activityId) throw new Error("创建并发测试活动失败，请检查管理员权限与活动接口");

  const signupResults = await Promise.all(
    Array.from({ length: concurrency }, () => call("POST", "/api/activity/signup", userToken, { activityId }))
  );
  const signupSuccess = signupResults.filter((item) => item.status === 200).length;
  const signupRejected = signupResults.filter((item) => item.status === 400).length;
  console.log(`  并发抢报同一活动：成功 ${signupSuccess} 次，被拒 ${signupRejected} 次（测试活动 id=${activityId}）`);

  // 2.2 并发提交预约，应全部成功且编号唯一
  // 预约事项已改为用户自填（serviceName），不再传固定的服务项目 id。
  const createResults = await Promise.all(
    Array.from({ length: concurrency }, () =>
      call("POST", "/api/appointment", userToken, {
        stationId,
        serviceName: "性能测试沟通事项",
        date: "2026-12-20",
        slot: "09:00-11:00"
      })
    )
  );
  const createdIds = createResults.filter((item) => item.status === 200).map((item) => item.body.data.id);
  const uniqueIds = new Set(createdIds);
  console.log(`  并发提交预约：成功 ${createdIds.length} 次，唯一编号 ${uniqueIds.size} 个`);

  // 2.3 并发审核同一预约，应只有一次成功
  const appointmentId = createdIds[0];
  const auditResults = await Promise.all(
    Array.from({ length: concurrency }, () =>
      call("PUT", "/api/appointment/audit", adminToken, { id: appointmentId, status: "CONFIRMED" })
    )
  );
  const auditSuccess = auditResults.filter((item) => item.status === 200).length;
  const auditRejected = auditResults.filter((item) => item.status === 400).length;
  console.log(`  并发审核同一预约：成功 ${auditSuccess} 次，被拒 ${auditRejected} 次`);

  // 2.4 并发分派并办结同一诉求，办结应只成功一次
  const appeal = await call("POST", "/api/appeal", userToken, {
    title: "并发测试诉求",
    type: "驿站服务",
    area: "西湖区",
    content: "性能测试脚本创建"
  });
  const appealId = appeal.body?.data?.id;
  const handleResults = await Promise.all(
    Array.from({ length: concurrency }, () =>
      call("PUT", "/api/appeal/handle", adminToken, { id: appealId, status: "RESOLVED", content: "并发办结" })
    )
  );
  const handleSuccess = handleResults.filter((item) => item.status === 200).length;
  const handleRejected = handleResults.filter((item) => item.status === 400).length;
  console.log(`  并发办结同一诉求：成功 ${handleSuccess} 次，被拒 ${handleRejected} 次`);

  const checks = [
    { name: "同一用户并发报名同一活动只成功一次", ok: signupSuccess === 1 && signupRejected === concurrency - 1, detail: `成功 ${signupSuccess}，被拒 ${signupRejected}` },
    { name: "并发提交预约全部成功且编号唯一", ok: createdIds.length === concurrency && uniqueIds.size === concurrency, detail: `成功 ${createdIds.length}，唯一 ${uniqueIds.size}` },
    { name: "并发审核同一预约只成功一次", ok: auditSuccess === 1 && auditRejected === concurrency - 1, detail: `成功 ${auditSuccess}，被拒 ${auditRejected}` },
    { name: "并发办结同一诉求只成功一次", ok: handleSuccess === 1 && handleRejected === concurrency - 1, detail: `成功 ${handleSuccess}，被拒 ${handleRejected}` }
  ];

  console.log("\n[3] 并发正确性结论");
  let failed = 0;
  for (const check of checks) {
    console.log(`  [${check.ok ? "PASS" : "FAIL"}] ${check.name}（${check.detail}）`);
    if (!check.ok) failed += 1;
  }

  // ---------------------------------------------------------------- 生成报告
  const now = new Date();
  const stamp = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")} ${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`;
  const lines = [];
  lines.push("# 性能与并发测试报告");
  lines.push("");
  lines.push(`- 测试时间：${stamp}`);
  lines.push(`- 测试对象：${apiBase}`);
  lines.push("- 测试脚本：`scripts/perf-test.mjs`");
  lines.push(`- 参数：每个接口 ${totalRequests} 次请求，并发度 ${concurrency}`);
  lines.push("");
  lines.push("## 测试环境与边界");
  lines.push("");
  lines.push("| 项目 | 内容 |");
  lines.push("| --- | --- |");
  lines.push("| 运行方式 | 本机单实例，前后端与 MySQL 均在同一台开发机 |");
  lines.push("| 后端 | Spring Boot 3.3.5（内嵌 Tomcat，默认线程池），MySQL profile |");
  lines.push("| 数据库 | 本机 MySQL 8.0；驿站名录为 7 城市 1344 条真实公开来源记录，业务数据为测试期间写入 |");
  lines.push("| 压测客户端 | Node.js `fetch`，固定并发度循环发起请求 |");
  lines.push("");
  lines.push("> 说明：本报告用于验证实现的并发正确性与响应量级，**不是**生产环境容量规划结论。");
  lines.push("> 业务数据规模为演示级，不能据此推断生产环境下的吞吐与延迟表现。");
  lines.push("");
  lines.push("## 一、只读接口并发压测");
  lines.push("");
  lines.push("| 接口 | 方法 | 请求数 | 并发 | 失败数 | 平均(ms) | P50(ms) | P95(ms) | P99(ms) | 最大(ms) | 吞吐(req/s) |");
  lines.push("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |");
  for (const item of loadResults) {
    lines.push(
      `| ${item.name} | ${item.method} | ${item.requests} | ${item.parallel} | ${item.failures} | ` +
      `${format(item.avg)} | ${format(item.p50)} | ${format(item.p95)} | ${format(item.p99)} | ${format(item.max)} | ${format(item.qps)} |`
    );
  }
  lines.push("");
  lines.push("## 二、写入路径并发正确性");
  lines.push("");
  lines.push("| 校验项 | 预期 | 实测 | 结论 |");
  lines.push("| --- | --- | --- | --- |");
  lines.push(`| 同一用户并发报名同一活动 | 仅 1 次成功 | 成功 ${signupSuccess}，被拒 ${signupRejected} | ${signupSuccess === 1 ? "通过" : "未通过"} |`);
  lines.push(`| 并发提交预约 | 全部成功且编号唯一 | 成功 ${createdIds.length}，唯一编号 ${uniqueIds.size} | ${createdIds.length === concurrency && uniqueIds.size === concurrency ? "通过" : "未通过"} |`);
  lines.push(`| 并发审核同一预约 | 仅 1 次成功 | 成功 ${auditSuccess}，被拒 ${auditRejected} | ${auditSuccess === 1 ? "通过" : "未通过"} |`);
  lines.push(`| 并发办结同一诉求 | 仅 1 次成功 | 成功 ${handleSuccess}，被拒 ${handleRejected} | ${handleSuccess === 1 ? "通过" : "未通过"} |`);
  lines.push("");
  lines.push("并发正确性依赖以下实现约束：");
  lines.push("");
  lines.push("- 活动报名：报名前对 `activity` 行加 `SELECT ... FOR UPDATE` 行锁，把同一活动的并发报名串行化；`activity_signup` 表对 `(activity_id, user_id)` 建唯一索引，并将数据库唯一约束冲突转换为 400 中文提示。");
  lines.push("- 预约审核与取消：使用带状态条件的 `UPDATE ... WHERE status = ?` 条件更新并检查影响行数，只有仍处于待确认状态的记录会被修改，并发下只成功一次。");
  lines.push("- 诉求分派与办结：同样使用带状态条件的条件更新，只有未办结 / 未关闭的诉求可以被处理，保证每个状态变更只留一条处理记录。");
  lines.push("- 内存演示实现：对应的写操作使用 `synchronized` 串行化，保证演示模式下行为一致。");
  lines.push("");
  lines.push("## 三、测试期间产生的数据");
  lines.push("");
  lines.push("| 数据 | 说明 |");
  lines.push("| --- | --- |");
  lines.push("| 预约记录 | 并发正确性检查会写入若干条测试预约，沟通事项由脚本生成 |");
  lines.push(`| 测试活动 | 每次运行创建 1 个「并发报名测试活动」用于验证报名互斥（本次 id=${activityId}） |`);
  lines.push("| 测试诉求 | 每次运行创建 1 条「并发测试诉求」用于验证办结互斥 |");
  lines.push("| 活动报名 | 针对上述测试活动产生 1 条有效报名记录 |");
  lines.push("");
  lines.push("这些数据仅用于测试，可在重新执行 `scripts/init-mysql.ps1 -Reset` 后清空。");
  lines.push("");
  lines.push("## 四、结论与局限");
  lines.push("");
  lines.push(failed === 0
    ? "在本地演示数据规模下，只读接口未出现失败请求，写入路径的并发互斥符合预期。"
    : `有 ${failed} 项并发校验未通过，需要进一步排查。`);
  lines.push("");
  lines.push("尚未覆盖的内容：");
  lines.push("");
  lines.push("- 未做长时间稳定性与内存泄漏测试；");
  lines.push("- 未做多实例部署下的分布式并发测试（当前为单实例）；");
  lines.push("- 未与生产环境 SLO 指标对标，也未使用专业压测工具（如 JMeter、wrk）做阶梯加压；");
  lines.push("- 演示数据量远小于真实业务规模，索引与查询计划未在真实数据量下验证。");
  lines.push("");
  lines.push("## 五、复现方式");
  lines.push("");
  lines.push("```powershell");
  lines.push("# 1. 启动后端（MySQL 模式，需先执行 scripts/init-mysql.ps1）");
  lines.push("cd backend");
  lines.push("java -jar target/platform-0.0.1-SNAPSHOT.jar --spring.profiles.active=mysql");
  lines.push("");
  lines.push("# 2. 运行性能与并发测试");
  lines.push("node scripts/perf-test.mjs --requests 60 --concurrency 10");
  lines.push("```");
  lines.push("");

  fs.mkdirSync(path.dirname(reportPath), { recursive: true });
  fs.writeFileSync(reportPath, `${lines.join("\n")}\n`, "utf8");
  console.log(`\n报告已生成：${reportPath}`);
  process.exit(failed === 0 ? 0 : 1);
}

main().catch((error) => {
  console.error("性能测试异常终止：", error);
  process.exit(1);
});
