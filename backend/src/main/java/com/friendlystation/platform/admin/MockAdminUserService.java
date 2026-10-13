package com.friendlystation.platform.admin;

import com.friendlystation.platform.auth.AccountUser;
import com.friendlystation.platform.auth.MockAccountService;
import com.friendlystation.platform.common.BusinessException;
import com.friendlystation.platform.common.FormatUtils;
import com.friendlystation.platform.common.PageResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 非 mysql profile 的账号与角色管理，读写 {@link MockAccountService} 中同一份演示账号。 */
@Service
@Profile("!mysql")
public class MockAdminUserService implements AdminUserService {
    private final MockAccountService accountService;

    public MockAdminUserService(MockAccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public PageResult<Map<String, Object>> userPage(long current, long size, String role, String keyword) {
        String word = keyword == null ? "" : keyword.trim();
        List<Map<String, Object>> all = accountService.list().stream()
                .filter(user -> role == null || role.isBlank() || role.equals(user.role()))
                .filter(user -> word.isEmpty() || matches(word, user.username(), user.realName(), user.phone(),
                        user.area(), user.jobType(), RoleCatalog.roleName(user.role())))
                .map(this::view)
                .toList();
        return PageResult.of(current, size, all);
    }

    @Override
    public Map<String, Object> userDetail(long userId) {
        AccountUser user = accountService.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return view(user);
    }

    @Override
    public Map<String, Object> updateUser(long userId, Map<String, Object> payload) {
        accountService.updateProfile(
                userId,
                FormatUtils.text(payload.get("realName"), null),
                FormatUtils.text(payload.get("phone"), null),
                FormatUtils.text(payload.get("jobType"), null),
                FormatUtils.text(payload.get("area"), null)
        );
        return userDetail(userId);
    }

    @Override
    public Map<String, Object> updateStatus(long userId, String status) {
        String normalized = normalizeStatus(status);
        accountService.updateStatus(userId, normalized);
        return userDetail(userId);
    }

    @Override
    public Map<String, Object> setRoles(long userId, List<String> roleCodes) {
        List<String> codes = sanitize(roleCodes);
        accountService.updateRole(userId, codes.get(0));
        Map<String, Object> out = userDetail(userId);
        out.put("roles", codes);
        out.put("roleNote", "内存演示模式每个账号只保存一个角色，已使用列表中的第一个角色");
        return out;
    }

    @Override
    public List<Map<String, Object>> roles() {
        List<AccountUser> users = accountService.list();
        return RoleCatalog.DEFINITIONS.stream()
                .map(definition -> RoleCatalog.describe(definition.roleCode(),
                        users.stream().filter(user -> definition.roleCode().equals(user.role())).count()))
                .toList();
    }

    private Map<String, Object> view(AccountUser user) {
        boolean enabled = accountService.isEnabled(user.id());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", user.id());
        out.put("username", user.username());
        out.put("realName", user.realName() == null ? "" : user.realName());
        out.put("phone", user.phone() == null ? "" : user.phone());
        out.put("jobType", user.jobType() == null ? "" : user.jobType());
        out.put("area", user.area() == null ? "" : user.area());
        out.put("role", user.role());
        out.put("roleName", RoleCatalog.roleName(user.role()));
        out.put("roles", List.of(user.role()));
        out.put("status", enabled ? 1 : 0);
        out.put("statusLabel", enabled ? "正常" : "停用");
        out.put("dataStatus", "系统计算结果");
        return out;
    }

    private static List<String> sanitize(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            throw new BusinessException("至少需要保留一个角色");
        }
        List<String> codes = roleCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (codes.isEmpty()) {
            throw new BusinessException("至少需要保留一个角色");
        }
        for (String code : codes) {
            if (!RoleCatalog.isValid(code)) {
                throw new BusinessException("角色编码无效：" + code);
            }
        }
        return codes;
    }

    private static String normalizeStatus(String status) {
        String value = status == null ? "" : status.trim();
        if (value.equals("正常") || value.equals("1") || value.equalsIgnoreCase("ENABLED")) {
            return "正常";
        }
        if (value.equals("停用") || value.equals("0") || value.equalsIgnoreCase("DISABLED")) {
            return "停用";
        }
        throw new BusinessException("账号状态只能是 正常 或 停用");
    }

    private static boolean matches(String word, String... values) {
        for (String value : values) {
            if (value != null && value.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
