/**
 * 把非程序文件从 git 跟踪中移除（**磁盘文件保留**），并写入 .gitignore 防止再次提交。
 *
 * 需求：仓库只保留程序。确认移除的四组：
 *   A 临时垃圾文件（drawio 代理测试、range 分片、extract/refs 中间产物、QA 截图、下载脚本）
 *   B 论文与课题材料（论文 / 任务书 / 开题报告 / 中期 / 论文资料底稿 / *.docx / *.zip）
 *   C 图表与答辩材料（output、outputs、用例图、答辩 PPT）
 *   D 文档（docs/ 与三份说明 md）
 *
 * 确认保留：backend、frontend、scripts、docker-compose.yml、.gitignore、.agents、data
 *
 * 用法：node scripts/untrack-non-program-files.mjs [--apply]
 */
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const apply = process.argv.includes("--apply");

/** 需要移除的顶层目录（整目录移除）。 */
const REMOVE_DIRS = [
  "论文", "论文资料底稿", "任务书", "开题报告", "中期",
  "output", "outputs", "docs"
];

/** 需要移除的根目录文件（精确名）。 */
const REMOVE_FILES = [
  // A 临时垃圾
  "drawio-proxy-test.bin",
  "range1m.bin", "range1m.headers", "range-test.part", "test-range.bin", "test-range.headers",
  "extract0.txt", "extract1.txt", "extract2.txt", "extract3.txt",
  "refs.txt", "template.json",
  "frontend-qa-comparison.png", "frontend-qa-screenshot.png", "frontend-qa-viewport.png",
  "draw.io-31.4.5-windows-installer.exe",
  "download_drawio_chunks.ps1", "inspect_drawio_range.ps1",
  "draw_usecase.py", "create_visio_usecase.py",
  // B 论文与课题材料（根目录文档）
  "任务书.zip", "开题报告.zip",
  "课题申报表 .docx", "项目设计.docx", "论文前三章.docx",
  // C 图表与答辩材料
  "系统总用例图_可编辑.vsdx", "系统总用例图.png", "系统总用例图.svg",
  "功能结构图(1).vsdx", "打开系统总用例图_drawio.cmd",
  // D 文档
  "README.md", "AUDIT_AND_EVIDENCE.md", "DEVELOPMENT_PROGRESS.md", "design-qa.md"
];

const tracked = execFileSync("git", ["-c", "core.quotepath=false", "ls-files"], {
  cwd: projectRoot,
  encoding: "utf8"
})
  .split(/\r?\n/)
  .filter(Boolean);

const toRemove = tracked.filter((file) => {
  const top = file.split("/")[0];
  if (REMOVE_DIRS.includes(top)) return true;
  return REMOVE_FILES.includes(file);
});

const toKeep = tracked.filter((file) => !toRemove.includes(file));

const sum = (list) => {
  let bytes = 0;
  for (const f of list) {
    try {
      bytes += fs.statSync(path.join(projectRoot, f)).size;
    } catch {
      /* 文件可能已被删除，忽略 */
    }
  }
  return (bytes / 1024 / 1024).toFixed(2);
};

console.log(`当前跟踪 ${tracked.length} 个文件\n`);
console.log(`将移除 ${toRemove.length} 个（${sum(toRemove)} MB）：`);
const byGroup = {};
for (const f of toRemove) {
  const key = REMOVE_DIRS.includes(f.split("/")[0]) ? f.split("/")[0] : "（根目录文件）";
  byGroup[key] = (byGroup[key] || 0) + 1;
}
for (const [k, v] of Object.entries(byGroup).sort((a, b) => b[1] - a[1])) {
  console.log(`  ${String(v).padStart(4)} 个  ${k}`);
}
console.log(`\n保留 ${toKeep.length} 个（${sum(toKeep)} MB）：`);
const keepByGroup = {};
for (const f of toKeep) {
  const key = f.split("/")[0];
  keepByGroup[key] = (keepByGroup[key] || 0) + 1;
}
for (const [k, v] of Object.entries(keepByGroup).sort((a, b) => b[1] - a[1])) {
  console.log(`  ${String(v).padStart(4)} 个  ${k}`);
}

if (!apply) {
  console.log("\n未加 --apply，未做任何改动。");
  process.exit(0);
}

// 分批执行，避免命令行过长
const BATCH = 80;
let removed = 0;
for (let i = 0; i < toRemove.length; i += BATCH) {
  const batch = toRemove.slice(i, i + BATCH);
  execFileSync("git", ["rm", "--cached", "-r", "-q", "--", ...batch], { cwd: projectRoot });
  removed += batch.length;
}
console.log(`\n已从 git 移除 ${removed} 个文件（磁盘文件保持不变）`);

// 追加 .gitignore 规则
const ignorePath = path.join(projectRoot, ".gitignore");
const current = fs.readFileSync(ignorePath, "utf8");
const marker = "# 非程序文件：仅保留在本地，不提交到仓库";
if (!current.includes(marker)) {
  const block = [
    "",
    marker,
    "# 仓库只保留程序（backend / frontend / scripts / data / .agents）。",
    "# 下面这些用 git rm --cached 取消跟踪后，靠这里防止再次被 git add 进来。",
    "论文/",
    "论文资料底稿/",
    "任务书/",
    "开题报告/",
    "中期/",
    "output/",
    "outputs/",
    "docs/",
    "任务书.zip",
    "开题报告.zip",
    "*.docx",
    "*.vsdx",
    "系统总用例图*",
    "功能结构图*",
    "打开系统总用例图_drawio.cmd",
    "README.md",
    "AUDIT_AND_EVIDENCE.md",
    "DEVELOPMENT_PROGRESS.md",
    "design-qa.md",
    "# 临时产物",
    "*.bin",
    "*.part",
    "extract*.txt",
    "refs.txt",
    "template.json",
    "frontend-qa-*.png",
    "download_drawio_chunks.ps1",
    "inspect_drawio_range.ps1",
    "draw_usecase.py",
    "create_visio_usecase.py",
    ""
  ].join("\n");
  fs.writeFileSync(ignorePath, current + block, "utf8");
  console.log("已追加 .gitignore 规则");
}
