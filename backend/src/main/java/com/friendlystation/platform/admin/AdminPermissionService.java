package com.friendlystation.platform.admin;

import java.util.List;
import java.util.Map;

/**
 * 管理员子账号的功能权限。
 *
 * <p>管理员是统一身份，靠**子账号 + 功能授权**分工。子账号的角色仍是管理员，
 * 但实际能做什么由这里决定。</p>
 *
 * <p>约定：**主账号（内置 platform_admin）视为拥有全部权限**，不受授权表限制。
 * 否则一旦误删自己的权限就没人能恢复了。</p>
 */
public interface AdminPermissionService {

    /** 权限目录（含是否可授予子账号）。 */
    List<Map<String, Object>> catalog();

    /** 某账号被显式授予的权限编码。 */
    List<String> grantedOf(long userId);

    /** 覆盖式授权；传空列表表示收回全部可授予权限。 */
    Map<String, Object> grant(long userId, List<String> codes);

    /**
     * 某账号**实际生效**的权限。
     *
     * <p>主管理员返回全部权限；子账号返回被授予的部分。前端据此决定显示哪些标签页。</p>
     */
    List<String> effectiveOf(long userId, String roleCode);
}
