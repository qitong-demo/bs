package com.friendlystation.platform.workflow;

import com.friendlystation.platform.common.BusinessException;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 活动承办方与报名状态的口径。
 *
 * <p>活动分三类承办方：驿站、平台管理员、第三方外部机构。
 * 第三方活动只做展示与链接跳转，**不接受站内报名**——报名和审核都在对方平台，
 * 我们既不掌握名单，也无法替对方联系报名人。</p>
 */
public final class ActivityOrganizer {

    private ActivityOrganizer() {
    }

    /** 驿站承办。 */
    public static final String TYPE_STATION = "STATION";
    /** 平台管理员承办。 */
    public static final String TYPE_PLATFORM = "PLATFORM";
    /** 第三方外部机构承办：只展示 + 跳转链接。 */
    public static final String TYPE_EXTERNAL = "EXTERNAL";

    private static final Map<String, String> TYPE_LABELS = Map.of(
            TYPE_STATION, "驿站",
            TYPE_PLATFORM, "平台管理员",
            TYPE_EXTERNAL, "第三方机构"
    );

    /** 归一化承办方类型，非法值一律视为驿站。 */
    public static String normalize(Object raw) {
        String text = raw == null ? "" : String.valueOf(raw).trim().toUpperCase();
        return TYPE_LABELS.containsKey(text) ? text : TYPE_STATION;
    }

    public static String typeLabel(String type) {
        return TYPE_LABELS.getOrDefault(normalize(type), TYPE_LABELS.get(TYPE_STATION));
    }

    /** 该承办方的活动是否接受站内报名。 */
    public static boolean acceptsSignup(String type) {
        return !TYPE_EXTERNAL.equals(normalize(type));
    }

    // ---------------------------------------------------------------- 报名状态

    /** 已报名，等待承办方联系。 */
    public static final String SIGNUP_SIGNED_UP = "SIGNED_UP";
    /** 承办方已联系。 */
    public static final String SIGNUP_CONTACTED = "CONTACTED";
    /** 审核通过。 */
    public static final String SIGNUP_APPROVED = "APPROVED";
    /** 审核未通过。 */
    public static final String SIGNUP_REJECTED = "REJECTED";
    /** 报名人自行取消。 */
    public static final String SIGNUP_CANCELLED = "CANCELLED";

    private static final Map<String, String> SIGNUP_LABELS = Map.of(
            SIGNUP_SIGNED_UP, "已报名，等待联系",
            SIGNUP_CONTACTED, "承办方已联系",
            SIGNUP_APPROVED, "审核通过",
            SIGNUP_REJECTED, "审核未通过",
            SIGNUP_CANCELLED, "已取消"
    );

    public static String signupLabel(String status) {
        String key = normalizeSignupStatus(status);
        return SIGNUP_LABELS.getOrDefault(key, key);
    }

    /** 归一化报名状态，兼容历史数据里的 SUBMITTED。 */
    public static String normalizeSignupStatus(Object raw) {
        String key = raw == null ? "" : String.valueOf(raw).trim().toUpperCase();
        return "SUBMITTED".equals(key) || key.isEmpty() ? SIGNUP_SIGNED_UP : key;
    }

    /** 承办方可设置的报名状态。 */
    public static boolean isReviewTarget(String status) {
        String key = status == null ? "" : status.trim().toUpperCase();
        return SIGNUP_CONTACTED.equals(key) || SIGNUP_APPROVED.equals(key) || SIGNUP_REJECTED.equals(key);
    }

    // ---------------------------------------------------------------- 报名信息校验

    private static final Pattern PHONE = Pattern.compile("[0-9+\\-()\\s]{5,30}");

    /** 报名人姓名必填：承办方要靠它去找人。 */
    public static String requireContactName(Object raw) {
        String text = raw == null ? "" : String.valueOf(raw).trim();
        if (text.isEmpty()) {
            throw new BusinessException("请填写报名人姓名，承办方需要据此联系你");
        }
        if (text.length() > 50) {
            throw new BusinessException("报名人姓名不能超过 50 个字符");
        }
        return text;
    }

    /** 联系电话必填且格式合理：这是承办方联系报名人的唯一途径。 */
    public static String requirePhone(Object raw) {
        String text = raw == null ? "" : String.valueOf(raw).trim();
        if (text.isEmpty()) {
            throw new BusinessException("请填写联系电话，承办方需要据此联系你");
        }
        if (!PHONE.matcher(text).matches()) {
            throw new BusinessException("联系电话格式不正确");
        }
        return text;
    }
}
