package com.friendlystation.platform.auth;

import com.friendlystation.platform.common.BusinessException;

import java.util.regex.Pattern;

/**
 * 账号 ID 与密码的规则。
 *
 * <p>集中在一处，避免"注册时宽松、改密时严格"这类口径漂移。</p>
 */
public final class CredentialRules {

    private CredentialRules() {
    }

    /** 账号 ID：4~20 位，字母开头，仅字母数字下划线。 */
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{3,19}$");

    /** 邮箱格式与 {@link VerificationCodeSupport#requireEmail} 保持一致。 */
    public static String requireUsername(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            throw new BusinessException("请填写新的账号 ID");
        }
        if (!USERNAME.matcher(text).matches()) {
            throw new BusinessException("账号 ID 需 4~20 位、字母开头，只能包含字母、数字与下划线");
        }
        return text;
    }

    /**
     * 校验新密码。
     *
     * @param username 账号 ID，用于「密码不得与账号 ID 相同」的判断
     */
    public static String requirePassword(String raw, String username) {
        String text = raw == null ? "" : raw;
        if (text.isEmpty()) {
            throw new BusinessException("请填写新密码");
        }
        if (text.length() < 8 || text.length() > 32) {
            throw new BusinessException("密码长度需在 8~32 位之间");
        }
        boolean hasLetter = text.chars().anyMatch(Character::isLetter);
        boolean hasDigit = text.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BusinessException("密码需同时包含字母与数字");
        }
        if (username != null && !username.isBlank() && text.equalsIgnoreCase(username)) {
            throw new BusinessException("密码不能与账号 ID 相同");
        }
        return text;
    }

    /** 两次输入是否一致。 */
    public static void requireSame(String first, String second) {
        if (first == null || !first.equals(second)) {
            throw new BusinessException("两次输入的密码不一致");
        }
    }
}
