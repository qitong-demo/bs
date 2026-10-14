package com.friendlystation.platform.auth;

/**
 * 账号凭据版本：改账号 ID 或密码时递增，让已签发的令牌立即失效。
 *
 * <p>为什么需要它：JWT 是无状态的，"改完强制重新登录"如果只靠前端清掉本地令牌，
 * 旧令牌在过期前（默认 120 分钟）**依然可用**——那等于没失效。
 * 把版本写进令牌并在拦截器比对，才是真的失效。</p>
 *
 * <p>代价是每个已认证请求要多读一次账号版本。对本案规模可以接受；
 * 若将来压力上来，可在账号表加缓存或把版本放进 Redis。</p>
 */
public interface CredentialVersionService {

    /** 账号当前的凭据版本；账号不存在时返回 -1（调用方据此判定令牌无效）。 */
    int versionOf(long userId);

    /** 凭据变更后递增版本，使所有已签发令牌失效。 */
    void bump(long userId);
}
