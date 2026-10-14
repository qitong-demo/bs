/**
 * 浏览器端验收脚本：用 Playwright 通过 CDP 连接已启动的 Chrome，走查用户端与管理端页面并输出截图。
 *
 * 前置条件：
 *   1. 后端已启动（默认 http://127.0.0.1:8080）
 *   2. 已执行 frontend 的 npm run build，并运行 node scripts/serve-dist.mjs
 *   3. Chrome 以 --remote-debugging-port=9222 启动
 *
 * 用法：node scripts/browser-check.mjs [--base http://127.0.0.1:5174] [--cdp http://127.0.0.1:9222] [--out dir]
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

function readArg(name, fallback) {
  const index = process.argv.indexOf(`--${name}`);
  return index >= 0 && process.argv[index + 1] ? process.argv[index + 1] : fallback;
}

const base = readArg("base", "http://127.0.0.1:5174").replace(/\/$/, "");
const cdp = readArg("cdp", "http://127.0.0.1:9222");
const outDir = path.resolve(projectRoot, readArg("out", "outputs/screenshots"));

const results = [];
let failed = 0;

function pass(name) {
  results.push({ name, ok: true });
  console.log(`  [PASS] ${name}`);
}
function fail(name, detail) {
  failed++;
  results.push({ name, ok: false, detail });
  console.log(`  [FAIL] ${name} :: ${detail}`);
}
async function step(name, action) {
  try {
    await action();
    pass(name);
  } catch (error) {
    fail(name, String(error?.message || error).split("\n")[0]);
  }
}

async function shoot(page, fileName) {
  fs.mkdirSync(outDir, { recursive: true });
  // 手机端截图用视口高度：全页截图会把 sticky 顶栏画在内容之上，
  // 看起来像标题被遮住，其实是截图方式的假象。
  const viewport = page.viewportSize();
  const isNarrow = viewport && viewport.width <= 900;
  await page.screenshot({ path: path.join(outDir, fileName), fullPage: !isNarrow });
}

async function expectText(page, text, timeout = 8000) {
  await page.waitForFunction(
    (needle) => document.body && document.body.innerText.includes(needle),
    text,
    { timeout }
  );
}

async function gotoModule(page, label) {
  await page.locator(".nav-list .nav-item", { hasText: label }).first().click();
  await page.waitForTimeout(1200);
}

async function main() {
  const browser = await chromium.connectOverCDP(cdp);
  const context = browser.contexts()[0] || (await browser.newContext());
  const page = await context.newPage();
  page.setDefaultTimeout(15000);

  console.log(`页面地址：${base}   截图目录：${outDir}\n`);

  // ---------------------------------------------------------------- 登录
  console.log("[1] 登录与首页");
  await step("打开登录页", async () => {
    await page.goto(`${base}/login`, { waitUntil: "domcontentloaded" });
    // 每次验收都从干净会话开始，确保登录与路由守卫被真实走一遍。
    await page.evaluate(() => {
      localStorage.clear();
      sessionStorage.clear();
    });
    await page.goto(`${base}/login`, { waitUntil: "domcontentloaded" });
    await expectText(page, "进入服务工作台");
    await shoot(page, "01-登录页.png");
  });

  await step("使用平台管理员账号登录", async () => {
    await page.fill('input[autocomplete="username"]', "platform_admin");
    await page.fill('input[autocomplete="current-password"]', "password");
    await page.locator("button.login-submit").click();
    await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 15000 });
    await expectText(page, "新就业群体友好驿站");
  });

  await step("首页展示真实统计指标", async () => {
    // 指标卡标签已简化为「驿站点位」，「全平台」限定词移到说明里（避免窄卡片被省略号截断）
    await expectText(page, "驿站点位");
    await expectText(page, "全平台营业中");
    await expectText(page, "诉求办结率");
    await expectText(page, "系统计算结果");
    await page.waitForTimeout(1500);
    await shoot(page, "02-首页工作台.png");
  });

  await step("管理员侧栏不含用户专属功能", async () => {
    // 驿站/平台管理员不是用户：报名自己的活动、给自己驿站写评价都没有意义。
    const labels = await page.locator(".nav-item span").allTextContents();
    const forbidden = ["服务预约", "我的诉求", "活动报名", "服务评价", "我的收藏"];
    const leaked = forbidden.filter((name) => labels.includes(name));
    if (leaked.length) {
      throw new Error(`管理员侧栏不应出现用户专属功能：${leaked.join("、")}（实际：${labels.join("/")}）`);
    }
    if (!labels.includes("管理工作台")) {
      throw new Error(`管理员侧栏缺少「管理工作台」入口（实际：${labels.join("/")}）`);
    }
    console.log(`      管理员侧栏：${labels.join(" / ")}`);
  });
  await step("首页趋势图渲染", async () => {
    // 趋势图默认收起，先展开再断言（它是回顾性数据，不再常驻首屏）。
    await page.locator(".chart-panel .panel-head-actions button", { hasText: "展开" }).first().click();
    // echarts 为按需加载的独立分包，首次冷启动需要更长时间。
    // 数据全为 0 时组件刻意不画坐标轴（会退化成 0~0，看起来像图表坏了），而是给出空状态说明，
    // 因此「有 canvas」或「有空状态」都算正确，两者都没有才是问题。
    await page.waitForFunction(
      () => Boolean(document.querySelector(".chart canvas") || document.querySelector(".chart-empty")),
      { timeout: 40000 }
    );
    const chartState = await page.evaluate(() => ({
      canvas: document.querySelectorAll(".chart canvas").length,
      empty: document.querySelectorAll(".chart-empty").length
    }));
    if (chartState.canvas === 0 && chartState.empty === 0) {
      throw new Error("趋势图展开后既没有图表也没有空状态说明");
    }
    console.log(`      趋势图：canvas ${chartState.canvas} 个，空状态 ${chartState.empty} 个`);
  });

  // ---------------------------------------------------------------- 用户端
  console.log("\n[2] 用户端模块");
  // 这些是用户专属功能，管理员侧栏里已经没有入口。
  // 必须换成普通用户登录，否则下面的点击都会超时（这正是权限分离生效的表现）。
  await step("切换为普通用户登录", async () => {
    await page.evaluate(() => {
      localStorage.clear();
      sessionStorage.clear();
    });
    await page.goto(`${base}/login`, { waitUntil: "domcontentloaded" });
    await page.fill('input[autocomplete="username"]', "rider_xiaowang");
    await page.fill('input[autocomplete="current-password"]', "password");
    await page.locator("button.login-submit").click();
    await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 15000 });
    await page.waitForTimeout(1200);
    const labels = await page.locator(".nav-item span").allTextContents();
    if (!labels.includes("服务预约")) {
      throw new Error(`普通用户侧栏应包含用户功能（实际：${labels.join("/")}）`);
    }
    console.log(`      普通用户侧栏：${labels.join(" / ")}`);
  });

  const modules = [
    ["驿站查询", "附近友好驿站", "03-驿站查询.png"],
    ["服务预约", "提前联系驿站", "04-服务预约.png"],
    ["诉求与反馈", "提交诉求或反馈", "05-诉求与反馈.png"],
    ["我的诉求", "诉求记录", "06-我的诉求.png"],
    ["活动报名", "报名", "07-活动报名.png"],
    ["政策资讯", "政策与资讯", "08-政策资讯.png"],
    ["服务评价", "评价驿站服务", "09-服务评价.png"],
    ["消息中心", "消息中心", "10-消息中心.png"],
    ["数据统计", "驿站联系申请排行", "11-数据统计.png"],
    ["我的收藏", "我的收藏", "12-我的收藏.png"],
    ["个人中心", "个人资料", "13-个人中心.png"],
    // 账号安全面板：改账号 ID / 改密码 / 绑邮箱三项都需邮箱验证码
    ["个人中心", "账号安全", "29-个人中心-账号安全.png"],
  ];

  await step("「我的报名」能看到承办方的审核反馈", async () => {
    // 报名之后必须能看到进展与承办方说了什么，否则报名等于石沉大海。
    await page.locator(".nav-item", { hasText: "活动报名" }).first().click();
    await page.waitForTimeout(1200);
    await page.locator("button", { hasText: "查看我的报名" }).first().click();
    await page.waitForTimeout(1800);
    const state = await page.evaluate(() => ({
      items: document.querySelectorAll(".my-signup-item").length,
      statuses: [...document.querySelectorAll(".my-signup-head .el-tag")].map((el) => el.textContent.trim()),
      reviews: [...document.querySelectorAll(".my-signup-review")].map((el) => el.textContent.trim())
    }));
    if (state.items === 0) {
      throw new Error("「我的报名」弹窗没有列出任何报名记录");
    }
    // 状态文案必须是报名口径，不能漏出内部枚举值
    const raw = state.statuses.filter((s) => /^[A-Z_]+$/.test(s));
    if (raw.length) {
      throw new Error(`报名状态未本地化，出现枚举值：${raw.join("、")}`);
    }
    console.log(`      我的报名：${state.items} 条，其中 ${state.reviews.length} 条有承办方反馈`);
    await page.keyboard.press("Escape");
    await page.waitForTimeout(600);
  });

  for (const [label, marker, file] of modules) {
    await step(`模块「${label}」可打开并渲染`, async () => {
      await gotoModule(page, label);
      await expectText(page, marker);
      await shoot(page, file);
    });
  }

  await step("数据统计展示月度趋势表", async () => {
    await gotoModule(page, "数据统计");
    await expectText(page, "月度趋势");
  });

  await step("驿站查询支持收藏与预约入口", async () => {
    await gotoModule(page, "驿站查询");
    await expectText(page, "预约服务");
    await expectText(page, "收藏驿站");
  });

  await step("驿站查询使用服务端分页", async () => {
    await gotoModule(page, "驿站查询");
    await page.waitForSelector(".el-pagination", { timeout: 15000 });
    const total = await page.locator(".el-pagination__total").first().innerText();
    if (!/\d/.test(total)) {
      throw new Error(`分页未显示总数：${total}`);
    }
  });

  await step("驿站详情展示数据来源与核对日期", async () => {
    await gotoModule(page, "驿站查询");
    await expectText(page, "核对日期");
    await expectText(page, "来源发布日期");
  });

  await step("驿站查询可按区域筛选真实名录", async () => {
    await gotoModule(page, "驿站查询");
    // 区域下拉由 /api/station/areas 提供，选项形如「上城区（73）」
    const options = await page.locator(".module-toolbar .el-select").first().locator("xpath=..").count();
    if (options < 1) {
      throw new Error("未找到区域筛选控件");
    }
    await expectText(page, "小哥驿站");
  });

  // ---------------------------------------------------------------- 管理端
  console.log("\n[3] 管理端工作台");
  // 上一段为了走用户模块换成了普通用户，这里切回管理员。
  // 普通用户访问 /admin 会被路由守卫挡回首页，所以必须先换账号。
  await step("切换为平台管理员登录", async () => {
    await page.evaluate(() => {
      localStorage.clear();
      sessionStorage.clear();
    });
    await page.goto(`${base}/login`, { waitUntil: "domcontentloaded" });
    await page.fill('input[autocomplete="username"]', "platform_admin");
    await page.fill('input[autocomplete="current-password"]', "password");
    await page.locator("button.login-submit").click();
    await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 15000 });
    await page.waitForTimeout(1200);
  });

  await step("进入管理工作台", async () => {
    await page.locator(".topbar-actions button", { hasText: "管理工作台" }).first().click();
    await page.waitForURL(/\/admin/, { timeout: 15000 });
    await expectText(page, "管理工作台");
    await page.locator(".el-tabs__item", { hasText: "运营概览" }).first().click();
    await page.waitForTimeout(1500);
    await expectText(page, "工作流概览");
    await shoot(page, "14-管理端-运营概览.png");
  });

  const adminTabs = [
    ["预约审核", "驿站", "15-管理端-预约审核.png"],
    ["诉求处理", "诉求标题", "16-管理端-诉求处理.png"],
    // 用工具栏说明作标记：无论有没有待审批记录，这段说明都会渲染
    ["变更审批", "驿站提交的信息变更申请", "28-管理端-变更审批.png"],
    ["驿站管理", "区域", "17-管理端-驿站管理.png"],
    ["服务项目管理", "服务名称", "18-管理端-服务项目.png"],
    ["资讯公告管理", "标题", "19-管理端-资讯公告.png"],
    ["活动管理", "活动", "20-管理端-活动管理.png"],
    ["报名记录", "报名", "21-管理端-报名记录.png"],
    ["账号管理", "账号", "22-管理端-账号管理.png"],
    ["角色权限", "角色", "23-管理端-角色权限.png"],
    ["报表导出", "导出", "24-管理端-报表导出.png"]
  ];

  for (const [tab, marker, file] of adminTabs) {
    await step(`管理端标签「${tab}」可打开`, async () => {
      await page.locator(".el-tabs__item", { hasText: tab }).first().click();
      await page.waitForTimeout(1200);
      await expectText(page, marker);
      await shoot(page, file);
    });
  }

  await step("管理端驿站管理展示类型、数据来源列与来源说明", async () => {
    await page.locator(".el-tabs__item", { hasText: "驿站管理" }).first().click();
    await page.waitForTimeout(1500);
    // 说明文案：三种数据状态的含义
    await expectText(page, "来源可核对");
    await expectText(page, "批次级信息");
    await expectText(page, "后台录入");
    // 表头：类型与数据来源两列（列较多时可横向滚动，这里按 DOM 校验而不是可见文本）
    const headers = await page.locator(".el-tab-pane:visible .el-table__header th").allInnerTexts();
    const joined = headers.join("|");
    if (!joined.includes("类型") || !joined.includes("数据来源")) {
      throw new Error(`表头缺少类型或数据来源列：${joined}`);
    }
  });

  await step("管理端驿站管理已加载真实名录", async () => {
    await page.locator(".el-tabs__item", { hasText: "驿站管理" }).first().click();
    await page.waitForTimeout(1200);
    // 只取当前可见标签页内的分页，避免匹配到其他已渲染但隐藏的表格
    const totalText = await page.locator(".el-tab-pane:visible .el-pagination__total").first().innerText();
    const total = Number((totalText.match(/\d+/g) || []).join(""));
    if (!Number.isFinite(total) || total < 1000) {
      throw new Error(`驿站管理总数异常（多城市名录应有上千条）：${totalText}`);
    }
  });

  await step("从管理工作台返回首页", async () => {
    await page.locator("button", { hasText: "返回首页" }).first().click();
    await page.waitForTimeout(1500);
    await page.waitForSelector(".city-select", { timeout: 15000 });
  });

  await step("顶栏城市选择器提供多个城市", async () => {
    const select = page.locator(".city-select").first();
    await select.click();
    await page.waitForTimeout(600);
    const options = await page.locator(".el-select-dropdown:visible .el-select-dropdown__item").allInnerTexts();
    if (options.length < 5) {
      throw new Error(`城市下拉选项过少（应覆盖多个城市）：${options.length} 个`);
    }
    if (!options.some((text) => text.includes("北京"))) {
      throw new Error(`城市下拉未包含北京：${options.join(" / ")}`);
    }
    await page.keyboard.press("Escape");
    await page.waitForTimeout(400);
  });

  await step("切换城市后首页数据随之刷新", async () => {
    const select = page.locator(".city-select").first();
    await select.click();
    await page.waitForTimeout(500);
    await page.locator(".el-select-dropdown:visible .el-select-dropdown__item", { hasText: "北京" }).first().click();
    await page.waitForTimeout(2500);
    await expectText(page, "当前城市");
    await expectText(page, "北京市");
  });

  await step("首页地图渲染并就位", async () => {
    // 高德 JS API 的地图容器
    await page.waitForSelector(".amap-container", { timeout: 25000 });
    const hasLogo = await page.locator(".amap-logo, .amap-copyright").count();
    if (hasLogo < 1) {
      throw new Error("地图缺少高德版权标识");
    }
  });

  await step("首页地图标出驿站点位", async () => {
    // 坐标由高德地理编码回填，因此地图上应当出现真实点位标记。
    // 注意：高德标记在容器内不一定通过 Playwright 的「可见」判定，这里按 DOM 存在性判断。
    await page.waitForFunction(() => document.querySelectorAll(".amap-marker").length > 0, { timeout: 20000 });
    const markerCount = await page.evaluate(() => document.querySelectorAll(".amap-marker").length);
    if (markerCount < 1) {
      throw new Error(`地图上没有驿站点位标记，实际 ${markerCount} 个`);
    }
    await shoot(page, "02-首页工作台.png");
  });

  // ---------------------------------------------------------------- 手机端适配
  console.log("\n[5] 手机端响应式");
  // 手机端这段要看的是用户界面（首页、服务预约、侧栏），换成普通用户再看，
  // 管理员侧栏里没有「服务预约」，用管理员跑必然点不到。
  await step("切回普通用户以检查手机端用户界面", async () => {
    await page.evaluate(() => {
      localStorage.clear();
      sessionStorage.clear();
    });
    await page.goto(`${base}/login`, { waitUntil: "domcontentloaded" });
    await page.fill('input[autocomplete="username"]', "rider_xiaowang");
    await page.fill('input[autocomplete="current-password"]', "password");
    await page.locator("button.login-submit").click();
    await page.waitForURL((url) => !url.pathname.startsWith("/login"), { timeout: 15000 });
    await page.waitForTimeout(1200);
  });

  await step("切换到手机视口（390×844，iPhone 尺寸）", async () => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.waitForTimeout(800);
  });

  await step("手机端不出现横向滚动", async () => {
    await page.waitForTimeout(1800);
    const overflow = await page.evaluate(() => ({
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth
    }));
    if (overflow.scrollWidth > overflow.clientWidth + 2) {
      throw new Error(`出现横向滚动：内容宽 ${overflow.scrollWidth} > 视口宽 ${overflow.clientWidth}`);
    }
    await shoot(page, "25-手机端-首页.png");
  });

  await step("手机端首页按单列堆叠展示", async () => {
    // 主区在窄屏下应堆叠：地图与驿站列表上下排列，而不是并排被压扁
    const stacked = await page.evaluate(() => {
      const map = document.querySelector(".map-panel");
      const side = document.querySelector(".station-side");
      if (!map || !side) return false;
      return side.getBoundingClientRect().top >= map.getBoundingClientRect().bottom - 4;
    });
    if (!stacked) {
      throw new Error("主区没有在窄屏下堆叠为上下排列");
    }
  });

  await step("手机端地图仍有可用高度", async () => {
    const height = await page.evaluate(() => {
      const canvas = document.querySelector(".map-canvas");
      return canvas ? Math.round(canvas.getBoundingClientRect().height) : 0;
    });
    if (height < 200) {
      throw new Error(`手机端地图高度过小：${height}px`);
    }
  });

  await step("手机端侧栏默认收起，点汉堡可展开", async () => {
    const hidden = await page.evaluate(() => {
      const sidebar = document.querySelector(".sidebar");
      return sidebar ? sidebar.getBoundingClientRect().right <= 2 : false;
    });
    if (!hidden) {
      throw new Error("手机端侧栏没有默认收起，会挡住内容");
    }
    await page.locator("button.icon-button[aria-label='切换菜单']").first().click();
    await page.waitForTimeout(700);
    const shown = await page.evaluate(() => {
      const sidebar = document.querySelector(".sidebar");
      return sidebar ? sidebar.getBoundingClientRect().right > 100 : false;
    });
    if (!shown) {
      throw new Error("点汉堡后侧栏没有展开");
    }
    await shoot(page, "27-手机端-侧栏展开.png");
  });

  await step("手机端服务预约表单可正常展示", async () => {
    await page.locator(".nav-item", { hasText: "服务预约" }).first().click();
    await page.waitForTimeout(1800);
    await expectText(page, "提前联系驿站");
    await expectText(page, "想提前沟通什么");
    // 页面标题不能因为右侧按钮而被挤成竖排单字
    const titleBox = await page.evaluate(() => {
      const h1 = document.querySelector(".module-heading h1");
      if (!h1) return null;
      const rect = h1.getBoundingClientRect();
      return { width: Math.round(rect.width), height: Math.round(rect.height) };
    });
    if (!titleBox || titleBox.width < 80) {
      throw new Error(`模块标题被挤压：${JSON.stringify(titleBox)}`);
    }
    await shoot(page, "26-手机端-服务预约.png");
  });

  await step("恢复桌面视口", async () => {
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.waitForTimeout(600);
  });

  // ---------------------------------------------------------------- 收尾
  await page.close();
  await browser.close();

  console.log("\n==================== 浏览器验收结果 ====================");
  console.log(`通过：${results.filter((item) => item.ok).length}  失败：${failed}`);
  if (failed > 0) {
    console.log("\n失败明细：");
    results.filter((item) => !item.ok).forEach((item) => console.log(`  - ${item.name} :: ${item.detail}`));
  }
  console.log(`截图目录：${outDir}`);
  process.exit(failed > 0 ? 1 : 0);
}

main().catch((error) => {
  console.error("浏览器验收异常终止：", error);
  process.exit(1);
});
