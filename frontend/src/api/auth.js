import { http, request } from "./http";

/** 登录：返回 token、角色与显示名。 */
export async function login(payload) {
  try {
    const data = await request({ url: "/auth/login", method: "post", data: payload });
    if (!data?.token) {
      const error = new Error("登录接口未返回有效凭证");
      error.userMessage = "登录接口未返回有效凭证";
      throw error;
    }
    return data;
  } catch (error) {
    if (!error.response) {
      error.userMessage = error.userMessage || "无法连接服务端，请确认后端已启动（8080）";
    } else if (error.response.status === 401 || error.response.data?.code === 401) {
      error.userMessage = "登录凭证已失效，请重新登录";
    } else if (error.response.data?.message) {
      error.userMessage = error.response.data.message;
    } else {
      error.userMessage = "登录服务暂时不可用，请稍后重试";
    }
    throw error;
  }
}

export function register(payload) {
  return request({ url: "/auth/register", method: "post", data: payload });
}

/** 校验当前 token 是否仍然有效，并取回账号信息。 */
export function currentUser() {
  return request({ url: "/user/info" });
}

export function updateProfile(payload) {
  return request({ url: "/user/info", method: "put", data: payload });
}

export { http };
