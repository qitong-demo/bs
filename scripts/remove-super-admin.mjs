/**
 * 清理幽灵角色 SUPER_ADMIN 的引用。
 *
 * 背景：SUPER_ADMIN 此前只在 schema.sql 里定义了角色行，从未分配给任何账号，
 * 却被 27 个文件的 @RequireRole / 前端角色常量引用。角色模型收敛为三个身份后必须清掉。
 *
 * 只做**删除该元素**，不改动其他权限项；每处改动都打印出来供核对。
 * 用法：node scripts/remove-super-admin.mjs [--apply]
 */
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const apply = process.argv.includes("--apply");

// core.quotepath=false：否则 git 会给含中文的路径加引号并转义，导致读文件失败
const files = execFileSync("git", ["-c", "core.quotepath=false", "grep", "-I", "-l", "SUPER_ADMIN"], {
  cwd: projectRoot,
  encoding: "utf8"
})
  .split(/\r?\n/)
  .map((line) => line.trim().replace(/^"(.*)"$/, "$1"))
  .filter(Boolean)
  // openapi.json 与接口文档是导出产物，重新导出即可，不在这里改
  .filter((file) => !file.endsWith("openapi.json") && !file.includes("接口文档.md"))
  // schema.sql 里保留一段说明文字，解释为什么删掉这个角色，属有意保留
  .filter((file) => !file.endsWith("schema.sql"));

console.log(`待处理 ${files.length} 个文件（导出产物由脚本重新生成，跳过）\n`);

let changedFiles = 0;
let changedSpots = 0;

for (const file of files) {
  const full = path.join(projectRoot, file);
  const original = fs.readFileSync(full, "utf8");
  let text = original;
  const notes = [];

  // 1) 后置元素：", "SUPER_ADMIN"" → 删除
  const trailing = /,\s*"SUPER_ADMIN"/g;
  if (trailing.test(text)) {
    const hits = (text.match(trailing) || []).length;
    text = text.replace(trailing, "");
    notes.push(`删除后置元素 ×${hits}`);
  }

  // 2) 前置元素："SUPER_ADMIN", → 删除
  const leading = /"SUPER_ADMIN"\s*,\s*/g;
  if (leading.test(text)) {
    const hits = (text.match(leading) || []).length;
    text = text.replace(leading, "");
    notes.push(`删除前置元素 ×${hits}`);
  }

  // 3) 单元素数组只剩它、或独立字符串（文档/常量列表）→ 整行提示人工确认
  if (text.includes("SUPER_ADMIN")) {
    const lines = text.split(/\r?\n/).filter((line) => line.includes("SUPER_ADMIN"));
    notes.push(`仍需人工处理 ${lines.length} 行`);
    for (const line of lines.slice(0, 3)) {
      console.log(`  [人工] ${file}: ${line.trim().slice(0, 100)}`);
    }
  }

  if (text !== original) {
    changedFiles += 1;
    changedSpots += notes.length;
    console.log(`  ${apply ? "改写" : "待改"} ${file} — ${notes.join("；")}`);
    if (apply) {
      fs.writeFileSync(full, text, "utf8");
    }
  }
}

console.log(`\n共 ${changedFiles} 个文件、${changedSpots} 处${apply ? "已改写" : "待改写"}`);
if (!apply) {
  console.log("未加 --apply，未写入任何文件。");
}
