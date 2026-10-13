package com.friendlystation.platform.common;

import com.friendlystation.platform.workflow.WorkflowStatus;

import java.util.Map;

/**
 * 「诉求与反馈」的分类。
 *
 * <p><b>为什么要分类</b>：这个模块原先只承载「诉求」——用户单向提出问题、平台来解决。
 * 但骑手与驿站之间还有大量**正向的互助**需要被记录和转达，例如
 * 「驿站放了免费雨衣，骑手用过之后自己买了补上」「骑手受伤，驿站工作人员帮忙包扎，骑手来道谢」。
 * 这类内容既不是问题也不是投诉，却是平台里最值得留存的部分。</p>
 *
 * <p>因此把模块扩成三类，都走同一套受理流程（提交 → 受理 → 转达/处理 → 完成），
 * 只是**状态文案随分类变化**：诉求叫「已办结」，表扬叫「已致谢」，建议叫「已回复」。</p>
 */
public final class AppealCategory {
    /** 反映问题：需要平台或驿站解决的具体问题。 */
    public static final String REQUEST = "REQUEST";
    /** 表扬感谢：想对驿站或工作人员的帮助说声谢谢。 */
    public static final String PRAISE = "PRAISE";
    /** 提出建议：希望平台或驿站改进的地方。 */
    public static final String SUGGESTION = "SUGGESTION";

    private static final Map<String, String> LABELS = Map.of(
            REQUEST, "反映问题",
            PRAISE, "表扬感谢",
            SUGGESTION, "提出建议"
    );

    /** 各类别的默认子类型，用户未选时兜底。 */
    private static final Map<String, String> DEFAULT_TYPES = Map.of(
            REQUEST, "驿站服务",
            PRAISE, "热心帮助",
            SUGGESTION, "服务建议"
    );

    /**
     * 状态文案随分类变化，而且刻意**不用**「已提交 / 已受理 / 处理中 / 已办结」这套工单腔。
     *
     * <p>原因：这个模块里的内容往往是一来一回的人情往来，不是一张待关闭的工单。
     * 骑手写了「感谢驿站帮我包扎伤口」，界面上回一句「已办结」是不合适的；
     * 驿站回一句「举手之劳，不必客气」才是这段互动真正的样子。</p>
     *
     * <p>注意：**统计口径仍然使用「办结」**（开题报告要求后台统计「办结诉求数」与「办结率」），
     * 这里改的只是面向用户的状态措辞，两边不要混为一谈。</p>
     */
    private static final Map<String, Map<String, String>> STATUS_LABELS = Map.of(
            REQUEST, Map.of(
                    WorkflowStatus.APPEAL_PENDING, "已收到，会有人跟进",
                    WorkflowStatus.APPEAL_ASSIGNED, "已安排专人对接",
                    WorkflowStatus.APPEAL_PROCESSING, "正在处理中",
                    WorkflowStatus.APPEAL_RESOLVED, "已处理并回复你",
                    WorkflowStatus.APPEAL_CLOSED, "本次跟进已结束"),
            PRAISE, Map.of(
                    WorkflowStatus.APPEAL_PENDING, "心意已收到",
                    WorkflowStatus.APPEAL_ASSIGNED, "正在转达给驿站",
                    WorkflowStatus.APPEAL_PROCESSING, "驿站正在回应",
                    WorkflowStatus.APPEAL_RESOLVED, "驿站已回应，谢谢你的心意",
                    WorkflowStatus.APPEAL_CLOSED, "这段心意已存档"),
            SUGGESTION, Map.of(
                    WorkflowStatus.APPEAL_PENDING, "建议已收到",
                    WorkflowStatus.APPEAL_ASSIGNED, "已转达相关同事",
                    WorkflowStatus.APPEAL_PROCESSING, "正在评估可行性",
                    WorkflowStatus.APPEAL_RESOLVED, "已回复处理意见",
                    WorkflowStatus.APPEAL_CLOSED, "本次建议已归档")
    );

    private AppealCategory() {
    }

    /** 归一化分类：非法或缺失时回退为「反映问题」，保证历史数据与旧前端仍可用。 */
    public static String normalize(Object raw) {
        String text = raw == null ? "" : String.valueOf(raw).trim().toUpperCase();
        return LABELS.containsKey(text) ? text : REQUEST;
    }

    public static String label(String category) {
        return LABELS.getOrDefault(normalize(category), LABELS.get(REQUEST));
    }

    /** 该分类的默认子类型。 */
    public static String defaultType(String category) {
        return DEFAULT_TYPES.getOrDefault(normalize(category), "驿站服务");
    }

    /** 分类感知的状态文案；未知状态回退到通用文案。 */
    public static String statusLabel(String category, String status) {
        Map<String, String> mapping = STATUS_LABELS.get(normalize(category));
        if (mapping != null && mapping.containsKey(status)) {
            return mapping.get(status);
        }
        return WorkflowStatus.label(status);
    }

    /** 是否为正向反馈（用于统计与界面区分）。 */
    public static boolean isPositive(String category) {
        return PRAISE.equals(normalize(category));
    }

    // ---------------------------------------------------------------- 反馈对象

    /** 反馈给驿站（由该驿站的驿站管理员受理）。 */
    public static final String TARGET_STATION = "STATION";
    /** 反馈给平台（由平台管理员受理）。 */
    public static final String TARGET_PLATFORM = "PLATFORM";

    private static final Map<String, String> TARGET_LABELS = Map.of(
            TARGET_STATION, "驿站",
            TARGET_PLATFORM, "平台管理员"
    );

    /**
     * 归一化反馈对象。
     *
     * <p>开题报告「诉求分派与处理功能」写明：<b>平台管理员将诉求分派给驿站管理员或相关处理人员</b>，
     * 因此反馈对象本来就有「驿站」和「平台」两种，由用户提交时选择。</p>
     */
    public static String normalizeTarget(Object raw) {
        String text = raw == null ? "" : String.valueOf(raw).trim().toUpperCase();
        return TARGET_LABELS.containsKey(text) ? text : TARGET_PLATFORM;
    }

    public static String targetLabel(String target) {
        return TARGET_LABELS.getOrDefault(normalizeTarget(target), TARGET_LABELS.get(TARGET_PLATFORM));
    }

    /**
     * 确定反馈对象：**用户显式选择优先**；没有传时按分类与子类型合理推断，
     * 保持旧客户端与历史数据的行为不变。
     *
     * <p>推断规则：表扬感谢一律给驿站（要谢的是具体帮助过你的那个驿站）；
     * 子类型里带「驿站」的（如驿站服务、设施建议）给驿站；其余给平台管理员。</p>
     */
    public static String resolveTarget(Object raw, String category, String type) {
        String text = raw == null ? "" : String.valueOf(raw).trim().toUpperCase();
        if (TARGET_LABELS.containsKey(text)) {
            return text;
        }
        if (isPositive(category)) {
            return TARGET_STATION;
        }
        String appealType = type == null ? "" : type;
        if (appealType.contains("驿站") || appealType.contains("设施")) {
            return TARGET_STATION;
        }
        return TARGET_PLATFORM;
    }

    /** 受理方名称：反馈给驿站时是「驿站管理员」，反馈给平台时是「平台管理员」。 */
    public static String receiverName(String target) {
        return TARGET_STATION.equals(normalizeTarget(target)) ? "驿站管理员" : "平台管理员";
    }

    /** 受理方的角色编码，用于权限与数据范围判断。 */
    public static String receiverRole(String target) {
        return TARGET_STATION.equals(normalizeTarget(target)) ? "STATION_ADMIN" : "PLATFORM_ADMIN";
    }
}
