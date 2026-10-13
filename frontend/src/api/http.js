import axios from "axios";

/** 全局 axios 实例：统一携带 JWT、统一错误提示与登录失效处理。 */
export const http = axios.create({
  baseURL: "/api",
  timeout: 12000
});

http.interceptors.request.use((config) => {
  const token = localStorage.getItem("friendly_station_token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    if (status === 401) {
      localStorage.removeItem("friendly_station_token");
      localStorage.removeItem("friendly_station_role");
      if (!window.location.pathname.startsWith("/login")) {
        window.location.href = "/login";
      }
    }
    error.userMessage = error.response?.data?.message
      || (error.response ? `请求失败（HTTP ${status}）` : "无法连接服务端，请确认后端已启动（8080）");
    return Promise.reject(error);
  }
);

/**
 * 统一解包后端 ApiResponse：业务码非 200 时抛出带中文消息的错误。
 */
export async function request(config) {
  const response = await http.request(config);
  const body = response.data;
  if (body && typeof body === "object" && "code" in body) {
    if (body.code !== 200) {
      const error = new Error(body.message || "接口返回业务错误");
      error.userMessage = body.message || "接口返回业务错误";
      throw error;
    }
    return body.data;
  }
  return body;
}

/** 失败时返回兜底值，用于页面容错展示。 */
export async function safeRequest(config, fallback) {
  try {
    return await request(config);
  } catch (error) {
    return fallback;
  }
}

/** 读取错误对象上的中文提示。 */
export function errorMessage(error, fallback = "操作失败，请稍后重试") {
  return error?.userMessage || error?.response?.data?.message || error?.message || fallback;
}

/** 导出 CSV：走后端鉴权接口并以 Blob 触发浏览器下载。 */
export async function downloadCsv(type, fileName) {
  const response = await http.get(`/stat/export/${type}`, { responseType: "blob" });
  const url = window.URL.createObjectURL(new Blob([response.data], { type: "text/csv;charset=utf-8" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName || `${type}.csv`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);
}
