package com.friendlystation.platform.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 要求当前账号拥有指定的**功能权限**之一。
 *
 * <p>作用在 {@link RequireRole} 之上：角色决定"是不是管理员这一类"，
 * 功能权限决定"这个管理员具体能不能做这件事"。</p>
 *
 * <p>为什么必须有它：权限体系如果只驱动前端标签可见性，子账号绕过界面
 * 直接调接口就能越权——那套权限就只是装饰。**校验必须在服务端**。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    /** 需要的权限编码；具备其中任意一个即放行。 */
    String[] value();
}
