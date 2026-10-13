/**
 * 统一的 CLI 启动器：把临时目录指向项目内的 .tmp-run 后再启动 Vite / Vitest。
 *
 * 背景：在部分 Windows 环境（含杀毒软件实时扫描）下，esbuild 在系统临时目录创建的
 * `esbuild-*` 中间文件会在构建过程中被占用，导致 `remove ...: Access is denied` 构建失败；
 * 这些文件在构建结束后又可以正常删除，说明不是权限配置问题，而是构建瞬间的文件占用。
 * 把 TEMP/TMP/TMPDIR 指向项目内的 .tmp-run 可以稳定规避该问题，且不会污染系统临时目录。
 *
 * 用法：node scripts/with-local-temp.mjs <包名> <参数...>
 * 例如：node scripts/with-local-temp.mjs vite build
 */
import fs from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const [packageName, ...args] = process.argv.slice(2);

if (!packageName) {
  console.error("用法：node scripts/with-local-temp.mjs <包名> <参数...>");
  process.exit(1);
}

// 1. 准备项目内临时目录
const localTemp = path.join(frontendRoot, ".tmp-run");
fs.mkdirSync(localTemp, { recursive: true });

// 2. 解析目标包的可执行入口
const packageJsonPath = path.join(frontendRoot, "node_modules", packageName, "package.json");
if (!fs.existsSync(packageJsonPath)) {
  console.error(`未找到依赖 ${packageName}，请先执行 npm install。`);
  process.exit(1);
}
const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, "utf8"));
const binField = packageJson.bin;
const binRelative = typeof binField === "string"
  ? binField
  : binField?.[packageName] ?? Object.values(binField ?? {})[0];
if (!binRelative) {
  console.error(`${packageName} 未声明可执行入口。`);
  process.exit(1);
}
const binPath = path.join(frontendRoot, "node_modules", packageName, binRelative);

// 3. 以继承 stdio 的方式启动，并把临时目录指向项目内
const child = spawn(process.execPath, [binPath, ...args], {
  cwd: frontendRoot,
  stdio: "inherit",
  env: {
    ...process.env,
    TEMP: localTemp,
    TMP: localTemp,
    TMPDIR: localTemp
  }
});

child.on("exit", (code, signal) => {
  if (signal) {
    process.kill(process.pid, signal);
    return;
  }
  process.exit(code ?? 1);
});

child.on("error", (error) => {
  console.error(`启动 ${packageName} 失败：${error.message}`);
  process.exit(1);
});
