package com.friendlystation.platform.admin;

import com.friendlystation.platform.common.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 功能权限目录与分配规则。
 *
 * <p>权限编码写在这里作为**唯一来源**：两个 profile 的实现、前端标签可见性、
 * 校验逻辑都从这里取，避免各处各写一份而漂移。</p>
 */
@Component
public class PermissionCatalog {

    /** 仅管理员本人可用、**不可授予子账号**的权限。 */
    public static final String ACCOUNT_MANAGE = "ACCOUNT_MANAGE";

    /** 权限目录。顺序即管理端展示顺序。 */
    public static final List<Map<String, Object>> DEFINITIONS = List.of(
            entry("APPOINTMENT_AUDIT", "预约审核", "确认、拒绝预约、标记爽约"),
            entry("APPEAL_HANDLE", "诉求处理", "分派、回复、办结诉求"),
            entry("ACTIVITY_MANAGE", "活动管理", "活动增删改、取消活动"),
            entry("SIGNUP_MANAGE", "报名记录", "查看报名、联系与审核、给出反馈"),
            entry("CHANGE_APPROVE", "变更审批", "审批驿站提交的信息与位置变更申请"),
            entry("STATION_MANAGE", "驿站管理", "新增、修改、删除驿站"),
            entry("SERVICE_MANAGE", "服务项目管理", "服务项目增删改"),
            entry("NEWS_MANAGE", "资讯公告管理", "政策与公告的增删改、声明与建议创作、状态变更"),
            entry("DATA_REVIEW", "数据复核", "处理来源待核对、坐标为高德派生的驿站条目"),
            entry("REPORT_EXPORT", "报表导出", "导出各类台账"),
            // 放在最后，且 grantable=false
            entry(ACCOUNT_MANAGE, "账号管理", "新建子账号、分发权限、改账号 ID、重置密码")
    );

    private static Map<String, Object> entry(String code, String name, String remark) {
        return Map.of(
                "permissionCode", code,
                "permissionName", name,
                "remark", remark,
                "grantable", !ACCOUNT_MANAGE.equals(code)
        );
    }

    public static Set<String> allCodes() {
        return DEFINITIONS.stream()
                .map(item -> String.valueOf(item.get("permissionCode")))
                .collect(java.util.stream.Collectors.toSet());
    }

    /** 可授予子账号的权限编码。 */
    public static Set<String> grantableCodes() {
        return DEFINITIONS.stream()
                .filter(item -> Boolean.TRUE.equals(item.get("grantable")))
                .map(item -> String.valueOf(item.get("permissionCode")))
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 校验待授予的权限列表。
     *
     * <p>两道拦截：① 编码必须存在；② **不可把不可授予的权限授给子账号**——
     * 否则子账号能给自己加权限，整套权限体系就形同虚设。</p>
     */
    public static List<String> validateGrant(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        Set<String> all = allCodes();
        Set<String> grantable = grantableCodes();
        List<String> cleaned = codes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.trim().toUpperCase())
                .distinct()
                .toList();
        for (String code : cleaned) {
            if (!all.contains(code)) {
                throw new BusinessException("权限编码不存在：" + code);
            }
            if (!grantable.contains(code)) {
                throw new BusinessException("「" + nameOf(code) + "」仅管理员本人可用，不能授予子账号");
            }
        }
        return cleaned;
    }

    public static String nameOf(String code) {
        return DEFINITIONS.stream()
                .filter(item -> code.equals(item.get("permissionCode")))
                .map(item -> String.valueOf(item.get("permissionName")))
                .findFirst()
                .orElse(code);
    }

    /**
     * 驿站管理员的固定权限集合。
     *
     * <p>驿站管理员**不走授权表**：他的职责由角色决定（管好自己那几家驿站），
     * 不需要管理员再逐个勾权限。若也走授权表，新建的驿站管理员默认没有任何权限，
     * 反而要管理员手工配一遍才能干活。</p>
     */
    public static final List<String> STATION_ADMIN_PERMISSIONS = List.of(
            "APPOINTMENT_AUDIT", "APPEAL_HANDLE", "ACTIVITY_MANAGE",
            "SIGNUP_MANAGE", "REPORT_EXPORT", "SERVICE_MANAGE"
    );

    /** 主管理员的内置账号 ID 约定：账号名 platform_admin。 */
    public static final String PRIMARY_ADMIN_USERNAME = "platform_admin";
}
