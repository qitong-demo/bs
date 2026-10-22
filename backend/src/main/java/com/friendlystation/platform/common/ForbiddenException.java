package com.friendlystation.platform.common;

/**
 * 已登录但无权访问该资源（通常是数据范围或归属不匹配）。
 *
 * <p>与 {@link BusinessException} 区分：400 表示输入/状态不对，403 表示身份与目标不匹配。
 * 前端能据此区分「改一下再试」与「你本来就不该操作这条数据」。</p>
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
