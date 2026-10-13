/**
 * 生产静态服务：托管 frontend/dist 并把 /api 反向代理到后端，用于本地验收与部署演示。
 *
 * 用法：node scripts/serve-dist.mjs [--port 5174] [--api http://127.0.0.1:8080]
 * 说明：只使用 Node 内置模块，不依赖 Vite / esbuild，也不引入额外运行时依赖。
 */
import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const distRoot = path.join(projectRoot, "frontend", "dist");

function readArg(name, fallback) {
  const index = process.argv.indexOf(`--${name}`);
  return index >= 0 && process.argv[index + 1] ? process.argv[index + 1] : fallback;
}

const port = Number(readArg("port", process.env.PORT || 5174));
const apiBase = new URL(readArg("api", process.env.API_BASE || "http://127.0.0.1:8080"));

const MIME_TYPES = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".mjs": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".gif": "image/gif",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon",
  ".woff": "font/woff",
  ".woff2": "font/woff2",
  ".map": "application/json; charset=utf-8"
};

if (!fs.existsSync(distRoot)) {
  console.error(`未找到构建产物：${distRoot}\n请先在 frontend 目录执行 npm run build。`);
  process.exit(1);
}

function proxyToApi(request, response) {
  const target = new URL(request.url, apiBase);
  const proxyRequest = http.request(
    {
      hostname: target.hostname,
      port: target.port || 80,
      path: target.pathname + target.search,
      method: request.method,
      headers: { ...request.headers, host: target.host }
    },
    (proxyResponse) => {
      response.writeHead(proxyResponse.statusCode || 502, proxyResponse.headers);
      proxyResponse.pipe(response);
    }
  );
  proxyRequest.on("error", (error) => {
    response.writeHead(502, { "Content-Type": "application/json; charset=utf-8" });
    response.end(JSON.stringify({ code: 502, message: `后端服务不可用：${error.message}`, data: null }));
  });
  request.pipe(proxyRequest);
}

function serveStatic(request, response) {
  const requestPath = decodeURIComponent((request.url || "/").split("?")[0]);
  const relative = requestPath === "/" ? "index.html" : requestPath.replace(/^\/+/, "");
  let filePath = path.join(distRoot, relative);

  // 阻止路径穿越；目录或未知路由回退到 index.html，交给前端路由处理。
  if (!filePath.startsWith(distRoot)) {
    response.writeHead(403).end("Forbidden");
    return;
  }
  if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
    filePath = path.join(distRoot, "index.html");
  }

  const extension = path.extname(filePath).toLowerCase();
  const headers = { "Content-Type": MIME_TYPES[extension] || "application/octet-stream" };
  if (extension === ".html") {
    headers["Cache-Control"] = "no-cache";
  } else if (requestPath.startsWith("/assets/")) {
    headers["Cache-Control"] = "public, max-age=31536000, immutable";
  }
  response.writeHead(200, headers);
  fs.createReadStream(filePath).pipe(response);
}

const server = http.createServer((request, response) => {
  if ((request.url || "").startsWith("/api/")) {
    proxyToApi(request, response);
    return;
  }
  serveStatic(request, response);
});

server.listen(port, "127.0.0.1", () => {
  console.log(`静态服务已启动：http://127.0.0.1:${port}/  （/api -> ${apiBase.origin}）`);
});
