package com.friendlystation.platform.admin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 系统内置的四个角色定义，内存实现与数据库实现的角色口径保持一致。 */
public final class RoleCatalog {
    private RoleCatalog() {
    }

    public record RoleDefinition(String roleCode, String roleName, String remark) {
    }

    public static final List<RoleDefinition> DEFINITIONS = List.of(
            new RoleDefinition("USER", "普通用户", "新就业群体用户"),
            new RoleDefinition("STATION_ADMIN", "驿站管理员", "负责本站点运营"),
            new RoleDefinition("STATION_STAFF", "驿站工作人员", "在本站点接待预约、处理诉求与报名"),
            new RoleDefinition("STATION_VOLUNTEER", "驿站志愿者", "在本站点协助接待与报名"),
            new RoleDefinition("PLATFORM_ADMIN", "管理员", "统一统筹平台，可创建子账号并分发功能权限")
    );

    public static boolean isValid(String roleCode) {
        return roleCode != null && DEFINITIONS.stream().anyMatch(item -> item.roleCode().equals(roleCode));
    }

    public static String roleName(String roleCode) {
        return DEFINITIONS.stream()
                .filter(item -> item.roleCode().equals(roleCode))
                .map(RoleDefinition::roleName)
                .findFirst()
                .orElse(roleCode == null ? "" : roleCode);
    }

    public static Map<String, Object> describe(String roleCode, long userCount) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("roleCode", roleCode);
        out.put("roleName", roleName(roleCode));
        out.put("remark", DEFINITIONS.stream()
                .filter(item -> item.roleCode().equals(roleCode))
                .map(RoleDefinition::remark)
                .findFirst()
                .orElse(""));
        out.put("userCount", userCount);
        return out;
    }

    public static List<String> codes() {
        return new ArrayList<>(DEFINITIONS.stream().map(RoleDefinition::roleCode).toList());
    }
}
