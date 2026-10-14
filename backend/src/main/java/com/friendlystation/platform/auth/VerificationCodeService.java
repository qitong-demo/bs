package com.friendlystation.platform.auth;

import java.util.Map;

/**
 * 邮箱验证码服务。
 *
 * <p>两种 profile 各有一套实现（mysql 落库 / 内存 Map），对外行为一致。</p>
 */
public interface VerificationCodeService {

    /**
     * 签发并发送验证码。
     *
     * @param userId     申请账号；用于绑定验证码归属，防止 A 的验证码被用于改 B
     * @param email      收件邮箱
     * @param purpose    用途，取值见 {@link VerificationCodeSupport}
     * @param exposeCode 未配置 SMTP 时是否在返回值里带出验证码，让演示环境能继续走通
     * @return delivered / expireSeconds / target / 演示提示
     */
    Map<String, Object> issue(long userId, String email, String purpose, boolean exposeCode);

    /**
     * 校验验证码，通过后标记为已用（防重放）。
     *
     * @throws com.friendlystation.platform.common.BusinessException 错误 / 过期 / 已用 / 尝试过多
     */
    void verify(long userId, String email, String purpose, String code);

    /** 当前账号已绑定的邮箱；未绑定时抛业务异常（调用方据此提示先绑定）。 */
    String boundEmailOf(long userId);
}
