package com.friendlystation.platform.auth;

import com.friendlystation.platform.admin.AdminPermissionService;
import com.friendlystation.platform.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员对**他人**账号的操作：创建子账号、改他人账号 ID、重置他人密码。
 *
 * <p>与 {@link UserCredentialService}（改自己的凭据）分开：两者的验证方式不同。
 * 改自己的要用**自己的旧密码**；改他人的要用**管理员自己的密码**做二次确认，
 * 并在此基础上叠加被改账号的邮箱验证码。</p>
 *
 * <p>为什么管理员也要二次确认：管理员账号一旦被盗，最省事的破坏方式就是批量改密、
 * 把真实运营者全部锁在外面。要求输入管理员自己的密码，能在会话被劫持但密码未知时挡住这一步。</p>
 */
@Service
public class AdminCredentialService {
    private static final Logger log = LoggerFactory.getLogger(AdminCredentialService.class);

    private final AccountService accountService;
    private final PasswordService passwordService;
    private final AdminPermissionService adminPermissionService;
    private final VerificationCodeService verificationCodeService;
    private final CredentialVersionService credentialVersionService;

    public AdminCredentialService(AccountService accountService,
                                  PasswordService passwordService,
                                  AdminPermissionService adminPermissionService,
                                  VerificationCodeService verificationCodeService,
                                  CredentialVersionService credentialVersionService) {
        this.accountService = accountService;
        this.passwordService = passwordService;
        this.adminPermissionService = adminPermissionService;
        this.verificationCodeService = verificationCodeService;
        this.credentialVersionService = credentialVersionService;
    }

    /**
     * 创建管理员子账号，并一次性分发功能权限。
     *
     * @param permissions 授予的功能权限；不可授予的（如账号管理）会被 {@code PermissionCatalog} 拦下
     */
    @Transactional
    public Map<String, Object> createSubAccount(long adminId, String adminPassword, String username,
                                                String phone, String password, List<String> permissions) {
        requireAdminPassword(adminId, adminPassword);
        String checkedUsername = CredentialRules.requireUsername(username);
        String checkedPassword = CredentialRules.requirePassword(password, checkedUsername);
        String checkedPhone = requirePhone(phone);
        if (accountService.usernameExists(checkedUsername)) {
            throw new BusinessException("该账号 ID 已被占用");
        }
        // 权限先校验再创建：否则会出现"账号建好了、权限没授上"的半成品
        List<String> cleaned = com.friendlystation.platform.admin.PermissionCatalog.validateGrant(permissions);

        // 传**明文**给 AccountService：编码由它内部完成，与 register 的约定一致。
        // 这里若先编码一次，会被内部再编码一次，密码双重哈希后永远登录不上。
        AccountUser created = accountService.createAdminSubAccount(
                checkedUsername, checkedPhone, checkedPassword);
        adminPermissionService.grant(created.id(), cleaned);
        log.info("管理员 {} 创建子账号 {}，授予 {} 项权限", adminId, checkedUsername, cleaned.size());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", created.id());
        out.put("username", created.username());
        out.put("permissions", cleaned);
        out.put("message", "子账号已创建");
        out.put("dataStatus", "系统计算结果");
        return out;
    }

    /** 改他人账号 ID：管理员密码 + 被改账号邮箱的验证码。 */
    @Transactional
    public Map<String, Object> changeOtherUsername(long adminId, String adminPassword, long targetId,
                                                   String code, String newUsername) {
        requireAdminPassword(adminId, adminPassword);
        requireTarget(targetId);
        String target = CredentialRules.requireUsername(newUsername);
        String current = accountService.usernameOf(targetId);
        if (current == null) {
            throw new BusinessException("账号不存在");
        }
        if (current.equalsIgnoreCase(target)) {
            throw new BusinessException("新账号 ID 与当前账号相同");
        }
        if (accountService.usernameTakenByOther(target, targetId)) {
            throw new BusinessException("该账号 ID 已被占用");
        }
        // 验证码发往**被改账号**的邮箱：证明该账号所有者知情，而不是管理员单方面改名
        String email = verificationCodeService.boundEmailOf(targetId);
        verificationCodeService.verify(targetId, email, VerificationCodeSupport.PURPOSE_CHANGE_USERNAME, code);

        accountService.updateUsername(targetId, target);
        credentialVersionService.bump(targetId);
        log.info("管理员 {} 改账号 ID：userId={}，{} → {}", adminId, targetId, current, target);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", targetId);
        out.put("username", target);
        out.put("message", "账号 ID 已修改，该账号需要重新登录");
        out.put("requireRelogin", true);
        out.put("dataStatus", "系统计算结果");
        return out;
    }

    /**
     * 重置他人密码：管理员密码即可，**不需要被改账号的邮箱验证码**。
     *
     * <p>这是唯一不需要被改账号配合的场景——用户忘记密码、邮箱也收不到时，
     * 必须由管理员兜底。代价是管理员权限较重，因此要求其输入自己的密码。
     * 接口回归里有一条断言守住"普通用户不能改他人密码"。</p>
     */
    @Transactional
    public Map<String, Object> resetOtherPassword(long adminId, String adminPassword, long targetId,
                                                  String newPassword, String confirmPassword) {
        requireAdminPassword(adminId, adminPassword);
        requireTarget(targetId);
        String targetUsername = accountService.usernameOf(targetId);
        if (targetUsername == null) {
            throw new BusinessException("账号不存在");
        }
        CredentialRules.requireSame(newPassword, confirmPassword);
        String checked = CredentialRules.requirePassword(newPassword, targetUsername);

        AccountUser target = accountService.findByUsername(targetUsername);
        if (target != null && passwordService.matches(checked, target.passwordHash())) {
            throw new BusinessException("新密码不能与该账号当前密码相同");
        }
        accountService.updatePassword(targetId, passwordService.encode(checked));
        credentialVersionService.bump(targetId);
        // 只记事件不记密码
        log.info("管理员 {} 重置了账号 {} 的密码", adminId, targetId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", targetId);
        out.put("message", "密码已重置，该账号需要重新登录");
        out.put("requireRelogin", true);
        out.put("dataStatus", "系统计算结果");
        return out;
    }

    // ------------------------------------------------------------ 内部校验

    /** 二次确认：必须输入管理员**自己的**当前密码。 */
    private void requireAdminPassword(long adminId, String adminPassword) {
        AccountUser admin = accountService.findByUsername(accountService.usernameOf(adminId));
        if (admin == null) {
            throw new BusinessException("当前管理员账号不存在");
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new BusinessException("请输入你自己的登录密码以确认本次操作");
        }
        if (!passwordService.matches(adminPassword, admin.passwordHash())) {
            throw new BusinessException("登录密码不正确");
        }
    }

    /** 不允许管理员通过这个入口操作自己不存在的账号，避免把 id 写错却静默成功。 */
    private void requireTarget(long targetId) {
        if (targetId <= 0 || !accountService.exists(targetId)) {
            throw new BusinessException("目标账号不存在");
        }
    }

    private static String requirePhone(String phone) {
        String text = phone == null ? "" : phone.trim();
        if (text.isEmpty()) {
            throw new BusinessException("请填写手机号");
        }
        if (!text.matches("[0-9+\\-()\\s]{5,30}")) {
            throw new BusinessException("手机号格式不正确");
        }
        return text;
    }
}
