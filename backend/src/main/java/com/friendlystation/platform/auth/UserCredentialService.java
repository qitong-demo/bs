package com.friendlystation.platform.auth;

import com.friendlystation.platform.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 账号安全：改账号 ID、改密码、绑定邮箱。
 *
 * <p>三者都要求**邮箱验证码**。验证码与当前密码是两道不同的防线：
 * 验证码证明"能收到该邮箱"，当前密码证明"知道原密码"——改密码时两者都要，
 * 只靠其一都可能在邮箱或会话被攻破后被人接管账号。</p>
 *
 * <p>改账号 ID 或密码后 {@link CredentialVersionService#bump} 会让所有已签发令牌失效，
 * 用户必须重新登录。这不是可选项：账号 ID 是令牌与权限的主体标识，
 * 沿用旧令牌会出现"令牌属于旧账号、数据属于新账号"的错位。</p>
 */
@Service
public class UserCredentialService {
    private static final Logger log = LoggerFactory.getLogger(UserCredentialService.class);

    private final AccountService accountService;
    private final VerificationCodeService verificationCodeService;
    private final CredentialVersionService credentialVersionService;
    private final PasswordService passwordService;

    public UserCredentialService(AccountService accountService,
                                 VerificationCodeService verificationCodeService,
                                 CredentialVersionService credentialVersionService,
                                 PasswordService passwordService) {
        this.accountService = accountService;
        this.verificationCodeService = verificationCodeService;
        this.credentialVersionService = credentialVersionService;
        this.passwordService = passwordService;
    }

    /**
     * 改账号 ID。
     *
     * @param code 发送到**当前已绑定邮箱**的验证码
     */
    @Transactional
    public Map<String, Object> changeUsername(long userId, String code, String newUsername) {
        String target = CredentialRules.requireUsername(newUsername);
        String current = accountService.usernameOf(userId);
        if (current == null) {
            throw new BusinessException("账号不存在");
        }
        if (current.equalsIgnoreCase(target)) {
            throw new BusinessException("新账号 ID 与当前账号相同");
        }
        // 唯一性先查，避免白白消耗一次验证码
        if (accountService.usernameTakenByOther(target, userId)) {
            throw new BusinessException("该账号 ID 已被占用");
        }
        // 验证码发到已绑定邮箱；未绑定会在这里抛出"请先绑定邮箱"
        String email = verificationCodeService.boundEmailOf(userId);
        verificationCodeService.verify(userId, email, VerificationCodeSupport.PURPOSE_CHANGE_USERNAME, code);

        accountService.updateUsername(userId, target);
        credentialVersionService.bump(userId);
        log.info("账号 ID 已修改：userId={}，{} → {}", userId, current, target);
        return reloginHint("账号 ID 已修改", target);
    }

    /**
     * 改密码。
     *
     * @param code 发送到**当前已绑定邮箱**的验证码
     */
    @Transactional
    public Map<String, Object> changePassword(long userId, String code, String oldPassword,
                                              String newPassword, String confirmPassword) {
        AccountUser user = accountService.findByUsername(accountService.usernameOf(userId));
        if (user == null) {
            throw new BusinessException("账号不存在");
        }
        // 当前密码错误不去消耗验证码校验次数，先拦下来
        if (!passwordService.matches(oldPassword, user.passwordHash())) {
            throw new BusinessException("当前密码不正确");
        }
        CredentialRules.requireSame(newPassword, confirmPassword);
        String checked = CredentialRules.requirePassword(newPassword, user.username());
        if (passwordService.matches(checked, user.passwordHash())) {
            throw new BusinessException("新密码不能与当前密码相同");
        }
        String email = verificationCodeService.boundEmailOf(userId);
        verificationCodeService.verify(userId, email, VerificationCodeSupport.PURPOSE_CHANGE_PASSWORD, code);

        accountService.updatePassword(userId, passwordService.encode(checked));
        credentialVersionService.bump(userId);
        // 只记事件不记密码内容
        log.info("账号密码已修改：userId={}", userId);
        return reloginHint("密码已修改", user.username());
    }

    /**
     * 绑定或更换邮箱。
     *
     * <p>与另外两个不同：此时验证码是发到**新邮箱**的，用来证明新邮箱可用。
     * 因此首次绑定不需要"已绑定邮箱"这个前提。</p>
     */
    @Transactional
    public Map<String, Object> bindEmail(long userId, String email, String code) {
        String target = VerificationCodeSupport.requireEmail(email);
        verificationCodeService.verify(userId, target, VerificationCodeSupport.PURPOSE_BIND_EMAIL, code);
        accountService.updateEmail(userId, target);
        // 邮箱不是登录凭据，不递增版本——否则用户每次换邮箱都要被迫重新登录
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "邮箱已绑定");
        out.put("email", VerificationCodeSupport.maskEmail(target));
        out.put("dataStatus", "系统计算结果");
        return out;
    }

    /** 当前账号的凭据概况，供「账号安全」页面展示。 */
    public Map<String, Object> credentialInfo(long userId) {
        Map<String, Object> out = new LinkedHashMap<>();
        String email = accountService.emailOf(userId);
        out.put("username", accountService.usernameOf(userId));
        out.put("emailBound", email != null && !email.isBlank());
        out.put("email", email == null ? "" : VerificationCodeSupport.maskEmail(email));
        return out;
    }

    private static Map<String, Object> reloginHint(String message, String username) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", message);
        out.put("username", username);
        // 前端据此注销本地会话并跳登录页；后端已让旧令牌失效，两边同时生效
        out.put("requireRelogin", true);
        out.put("dataStatus", "系统计算结果");
        return out;
    }
}
