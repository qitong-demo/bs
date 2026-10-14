/**
 * 账号安全：改账号 ID、改密码、绑定邮箱。
 *
 * 三者都需要邮箱验证码。三种角色（普通用户 / 驿站管理员 / 管理员）共用同一套——
 * 改的都是"自己的"凭据，规则一致。
 */
import { request } from "./http";

/** 凭据概况：账号 ID 与是否已绑定邮箱（邮箱已打码）。 */
export function getCredentialInfo() {
  return request({ url: "/user/credentials" });
}

/** 邮件服务是否已配置；未配置时界面必须标注"验证码不会真实发送"。 */
export function getMailStatus() {
  return request({ url: "/user/verification/status" });
}

/**
 * 发送验证码。
 *
 * purpose 取值：CHANGE_USERNAME / CHANGE_PASSWORD / CHANGE_CONTACT / BIND_EMAIL。
 * 除 BIND_EMAIL 外，收件地址一律取账号已绑定邮箱，不接受前端指定。
 */
export function sendVerificationCode(purpose, email) {
  const data = email ? { purpose, email } : { purpose };
  return request({ url: "/user/verification/send", method: "post", data });
}

/** 改账号 ID（需验证码）。成功后所有旧令牌失效，必须重新登录。 */
export function changeUsername(newUsername, code) {
  return request({ url: "/user/credentials/username", method: "put", data: { newUsername, code } });
}

/** 改密码（需当前密码 + 验证码）。成功后必须重新登录。 */
export function changePassword(oldPassword, newPassword, confirmPassword, code) {
  return request({
    url: "/user/credentials/password",
    method: "put",
    data: { oldPassword, newPassword, confirmPassword, code }
  });
}

/** 绑定或更换邮箱；验证码发往新邮箱。 */
export function bindEmail(email, code) {
  return request({ url: "/user/credentials/email", method: "put", data: { email, code } });
}
