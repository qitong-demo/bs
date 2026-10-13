package com.friendlystation.platform.workflow;

import java.util.Map;
import java.util.Set;

/** 预约与诉求的状态常量、中文标签和允许的状态流转。 */
public final class WorkflowStatus {
    private WorkflowStatus() {
    }

    public static final String APPOINTMENT_PENDING = "PENDING_STATION_CONFIRMATION";
    public static final String APPOINTMENT_CONFIRMED = "CONFIRMED";
    public static final String APPOINTMENT_REJECTED = "REJECTED";
    public static final String APPOINTMENT_CANCELLED = "CANCELLED";
    public static final String APPOINTMENT_FINISHED = "FINISHED";

    public static final String APPEAL_PENDING = "PENDING_ACCEPTANCE";
    public static final String APPEAL_ASSIGNED = "ASSIGNED";
    public static final String APPEAL_PROCESSING = "PROCESSING";
    public static final String APPEAL_RESOLVED = "RESOLVED";
    public static final String APPEAL_CLOSED = "CLOSED";

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry(APPOINTMENT_PENDING, "待驿站确认"),
            Map.entry(APPOINTMENT_CONFIRMED, "已确认"),
            Map.entry(APPOINTMENT_REJECTED, "已拒绝"),
            Map.entry(APPOINTMENT_CANCELLED, "已取消"),
            Map.entry(APPOINTMENT_FINISHED, "已完成"),
            Map.entry(APPEAL_PENDING, "待受理"),
            Map.entry(APPEAL_ASSIGNED, "已分派"),
            Map.entry(APPEAL_PROCESSING, "处理中"),
            Map.entry(APPEAL_RESOLVED, "已办结"),
            Map.entry(APPEAL_CLOSED, "已关闭")
    );

    private static final Map<String, String> ACTION_LABELS = Map.of(
            "SUBMIT", "提交诉求",
            "ASSIGN", "分派处理人",
            "HANDLE", "处理中",
            "RESOLVE", "办结",
            "CLOSE", "关闭",
            "EVALUATE", "用户评价"
    );

    /** 驿站管理员可审核的目标状态。 */
    private static final Set<String> AUDIT_TARGETS = Set.of(APPOINTMENT_CONFIRMED, APPOINTMENT_REJECTED);

    /** 诉求处理可提交的目标状态。 */
    private static final Set<String> HANDLE_TARGETS = Set.of(APPEAL_PROCESSING, APPEAL_RESOLVED, APPEAL_CLOSED);

    public static String label(String status) {
        return status == null || status.isBlank() ? "未知" : LABELS.getOrDefault(status, status);
    }

    public static String actionLabel(String action) {
        return action == null || action.isBlank() ? "操作" : ACTION_LABELS.getOrDefault(action, action);
    }

    public static boolean isAuditTarget(String status) {
        return status != null && AUDIT_TARGETS.contains(status);
    }

    public static boolean isHandleTarget(String status) {
        return status != null && HANDLE_TARGETS.contains(status);
    }

    /** 只有待确认的预约可以被审核。 */
    public static boolean canAuditAppointment(String status) {
        return APPOINTMENT_PENDING.equals(status);
    }

    /** 待受理、已分派、处理中的诉求都可以继续处理。 */
    public static boolean canHandleAppeal(String status) {
        return APPEAL_PENDING.equals(status) || APPEAL_ASSIGNED.equals(status) || APPEAL_PROCESSING.equals(status);
    }

    /** 只有已办结的诉求可以评价。 */
    public static boolean canEvaluateAppeal(String status) {
        return APPEAL_RESOLVED.equals(status);
    }

    /** 诉求类型决定受理方：驿站职责范围内的事项交给驿站管理员。 */
    public static String receiverType(String appealType) {
        return "驿站服务".equals(appealType) || "设施建议".equals(appealType) ? "STATION_ADMIN" : "PLATFORM_ADMIN";
    }

    public static String receiverName(String appealType) {
        return "STATION_ADMIN".equals(receiverType(appealType)) ? "所属驿站管理员" : "平台管理员";
    }

    public static String handlingBoundary(String appealType) {
        return "STATION_ADMIN".equals(receiverType(appealType))
                ? "驿站职责范围内的服务与设施事项"
                : "平台协调或转办建议；不代表行政机关受理";
    }
}
