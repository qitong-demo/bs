package com.friendlystation.platform.admin;

import com.friendlystation.platform.common.BusinessException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 内存 profile 下的权限分配，与 mysql 实现同一套规则。 */
@Service
@Profile("!mysql")
public class DemoAdminPermissionService implements AdminPermissionService {

    /** 账号 → 权限编码集合。 */
    private final Map<Long, List<String>> granted = new ConcurrentHashMap<>();

    @Override
    public List<Map<String, Object>> catalog() {
        return PermissionCatalog.DEFINITIONS;
    }

    @Override
    public List<String> grantedOf(long userId) {
        return granted.getOrDefault(userId, List.of()).stream().sorted().toList();
    }

    @Override
    public synchronized Map<String, Object> grant(long userId, List<String> codes) {
        // 演示模式的账号是硬编码的 1~3，只校验存在性
        if (userId < 1 || userId > 3) {
            throw new BusinessException("账号不存在");
        }
        List<String> cleaned = PermissionCatalog.validateGrant(codes);
        granted.put(userId, new ArrayList<>(cleaned));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", userId);
        out.put("permissions", cleaned);
        out.put("count", cleaned.size());
        out.put("dataStatus", "系统计算结果");
        return out;
    }

    @Override
    public List<String> effectiveOf(long userId, String roleCode) {
        // 驿站管理员走角色固定权限，不查授权表（与 mysql 实现同口径）
        if ("STATION_ADMIN".equalsIgnoreCase(roleCode)) {
            return PermissionCatalog.STATION_ADMIN_PERMISSIONS;
        }
        // 主管理员账号 id=3（platform_admin）不受授权表限制
        if (userId == 3L) {
            return PermissionCatalog.DEFINITIONS.stream()
                    .map(item -> String.valueOf(item.get("permissionCode")))
                    .toList();
        }
        return grantedOf(userId);
    }
}
