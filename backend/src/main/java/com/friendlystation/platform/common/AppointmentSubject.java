package com.friendlystation.platform.common;

import java.util.regex.Pattern;

/**
 * 预约事项与联系电话的取值与校验。
 *
 * <p><b>为什么要有这个类</b>：平台的「驿站服务预约」不是预约使用饮水、空调这类设施
 * （这类小事既不需要预约，驿站也没法逐个受理），而是
 * 「<b>我有一件事，想提前联系驿站确认是否可行</b>」。
 * 因此预约事项由用户**自行填写**，不再从固定服务项目里选，
 * 并要求留下联系电话，便于驿站管理员回电确认。</p>
 *
 * <p>mysql 实现与内存实现共用这里的规则，避免两套实现校验不一致。</p>
 */
public final class AppointmentSubject {
    /** 事项说明最大长度，与 service_appointment.service_name 列宽一致。 */
    public static final int MAX_LENGTH = 100;
    /** 联系电话最大长度，与 contact_phone 列宽一致。 */
    public static final int PHONE_MAX_LENGTH = 30;

    /** 允许数字、加号、连字符、括号与空格，长度 5~30（兼容座机与手机号）。 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("[0-9+\\-()\\s]{5,30}");

    private AppointmentSubject() {
    }

    /**
     * 取出并校验用户自填的预约事项。
     *
     * @throws BusinessException 未填写或超长时抛出，前端会收到 400 与中文提示
     */
    public static String require(Object raw) {
        String text = raw == null ? "" : String.valueOf(raw).trim();
        if (text.isEmpty()) {
            throw new BusinessException("请填写想提前沟通的事项，例如「想确认能否借用场地开会」");
        }
        if (text.length() > MAX_LENGTH) {
            throw new BusinessException("事项说明请控制在 " + MAX_LENGTH + " 字以内");
        }
        return text;
    }

    /**
     * 取出并校验联系电话。电话为可选项，但一旦填写就必须是像样的号码。
     *
     * @throws BusinessException 格式不合法时抛出
     */
    public static String optionalPhone(Object raw) {
        String text = raw == null ? "" : String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return "";
        }
        if (!PHONE_PATTERN.matcher(text).matches()) {
            throw new BusinessException("联系电话格式不正确，请填写数字、空格或 -() 组成的号码");
        }
        return text;
    }
}
