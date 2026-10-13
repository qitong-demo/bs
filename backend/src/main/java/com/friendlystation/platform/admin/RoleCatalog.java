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
            new RoleDefinition("STATION_ADMIN", "驿站管理员", "负责本驿站运营"),
            new RoleDefinition("PLATFORM_ADMIN", "平台管理员", "负责平台全局管理"),
            new RoleDefinition("SUPER_ADMIN", "超级管理员", "负责系统最高权限")
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
