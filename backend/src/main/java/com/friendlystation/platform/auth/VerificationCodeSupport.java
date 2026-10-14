package com.friendlystation.platform.auth;

import com.friendlystation.platform.common.BusinessException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * 邮箱验证码的公共约定与工具。
 *
 * <p>两套实现（mysql 落库 / 内存 Map）共用这里的用途常量、格式校验与哈希算法，
 * 保证两种 profile 行为一致——这是本项目的既定约定。</p>
 */
public final class VerificationCodeSupport {

    private VerificationCodeSupport() {
    }

    /** 改账号 ID。 */
    public static final String PURPOSE_CHANGE_USERNAME = "CHANGE_USERNAME";
    /** 改密码。 */
    public static final String PURPOSE_CHANGE_PASSWORD = "CHANGE_PASSWORD";
    /** 改联系方式（手机号等）。 */
    public static final String PURPOSE_CHANGE_CONTACT = "CHANGE_CONTACT";
    /** 绑定邮箱（此时账号还没有邮箱，收件地址由用户输入）。 */
    public static final String PURPOSE_BIND_EMAIL = "BIND_EMAIL";

    /** 验证码有效期（秒）。 */
    public static final int CODE_TTL_SECONDS = 300;
    /** 同一邮箱 + 同一用途的重发间隔（秒）。 */
    public static final int RESEND_INTERVAL_SECONDS = 60;
    /** 单个验证码最多允许校验失败几次。 */
    public static final int MAX_ATTEMPTS = 5;

    /** 用途中文名，用于邮件正文与错误提示。 */
    public static final Map<String, String> PURPOSE_LABELS = Map.of(
            PURPOSE_CHANGE_USERNAME, "修改账号",
            PURPOSE_CHANGE_PASSWORD, "修改密码",
            PURPOSE_CHANGE_CONTACT, "修改联系方式",
            PURPOSE_BIND_EMAIL, "绑定邮箱"
    );

    /** 校验并归一化用途，非法用途直接抛业务异常。 */
    public static String requirePurpose(String purpose) {
        String text = purpose == null ? "" : purpose.trim().toUpperCase();
        if (!PURPOSE_LABELS.containsKey(text)) {
            throw new BusinessException("验证码用途不合法");
        }
        return text;
    }

    /** 邮箱格式与长度校验。 */
    public static String requireEmail(String email) {
        String text = email == null ? "" : email.trim().toLowerCase();
        if (text.isEmpty()) {
            throw new BusinessException("请填写邮箱地址");
        }
        if (text.length() > 120) {
            throw new BusinessException("邮箱地址过长");
        }
        if (!text.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new BusinessException("邮箱格式不正确");
        }
        return text;
    }

    /** 对外展示时打码，避免接口响应与日志里出现完整邮箱。 */
    public static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        String name = email.substring(0, at);
        String domain = email.substring(at);
        String head = name.substring(0, Math.min(2, name.length()));
        return head + "***" + domain;
    }

    /** 验证码哈希。只存哈希不存明文，避免库被看到就等于验证码泄露。 */
    public static String hash(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(code.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("验证码哈希计算失败", exception);
        }
    }

    /** 生成 6 位数字验证码。 */
    public static String randomCode(java.security.SecureRandom random) {
        return String.format("%06d", random.nextInt(1_000_000));
    }
}
