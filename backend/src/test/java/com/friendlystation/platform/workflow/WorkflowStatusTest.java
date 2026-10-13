package com.friendlystation.platform.workflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowStatusTest {
    @Test
    void mapsStatusCodesToChineseLabels() {
        assertEquals("待驿站确认", WorkflowStatus.label(WorkflowStatus.APPOINTMENT_PENDING));
        assertEquals("已确认", WorkflowStatus.label(WorkflowStatus.APPOINTMENT_CONFIRMED));
        assertEquals("已拒绝", WorkflowStatus.label(WorkflowStatus.APPOINTMENT_REJECTED));
        assertEquals("待受理", WorkflowStatus.label(WorkflowStatus.APPEAL_PENDING));
        assertEquals("已分派", WorkflowStatus.label(WorkflowStatus.APPEAL_ASSIGNED));
        assertEquals("处理中", WorkflowStatus.label(WorkflowStatus.APPEAL_PROCESSING));
        assertEquals("已办结", WorkflowStatus.label(WorkflowStatus.APPEAL_RESOLVED));
    }

    @Test
    void returnsUnknownForNullOrBlankStatus() {
        assertEquals("未知", WorkflowStatus.label(null));
        assertEquals("未知", WorkflowStatus.label("  "));
    }

    @Test
    void keepsUnmappedStatusCodeAsIs() {
        assertEquals("SOME_NEW_STATE", WorkflowStatus.label("SOME_NEW_STATE"));
    }

    @Test
    void onlyPendingAppointmentCanBeAudited() {
        assertTrue(WorkflowStatus.canAuditAppointment(WorkflowStatus.APPOINTMENT_PENDING));
        assertFalse(WorkflowStatus.canAuditAppointment(WorkflowStatus.APPOINTMENT_CONFIRMED));
        assertFalse(WorkflowStatus.canAuditAppointment(WorkflowStatus.APPOINTMENT_CANCELLED));
    }

    @Test
    void finishedAppealCannotBeHandledAgain() {
        assertTrue(WorkflowStatus.canHandleAppeal(WorkflowStatus.APPEAL_PENDING));
        assertTrue(WorkflowStatus.canHandleAppeal(WorkflowStatus.APPEAL_ASSIGNED));
        assertTrue(WorkflowStatus.canHandleAppeal(WorkflowStatus.APPEAL_PROCESSING));
        assertFalse(WorkflowStatus.canHandleAppeal(WorkflowStatus.APPEAL_RESOLVED));
        assertFalse(WorkflowStatus.canHandleAppeal(WorkflowStatus.APPEAL_CLOSED));
    }

    @Test
    void onlyResolvedAppealCanBeEvaluated() {
        assertTrue(WorkflowStatus.canEvaluateAppeal(WorkflowStatus.APPEAL_RESOLVED));
        assertFalse(WorkflowStatus.canEvaluateAppeal(WorkflowStatus.APPEAL_ASSIGNED));
    }

    @Test
    void validatesAuditAndHandleTargets() {
        assertTrue(WorkflowStatus.isAuditTarget("CONFIRMED"));
        assertTrue(WorkflowStatus.isAuditTarget("REJECTED"));
        assertFalse(WorkflowStatus.isAuditTarget("CANCELLED"));
        assertTrue(WorkflowStatus.isHandleTarget("PROCESSING"));
        assertTrue(WorkflowStatus.isHandleTarget("RESOLVED"));
        assertTrue(WorkflowStatus.isHandleTarget("CLOSED"));
        assertFalse(WorkflowStatus.isHandleTarget("PENDING_ACCEPTANCE"));
    }

    @Test
    void routesStationMattersToStationAdmin() {
        assertEquals("STATION_ADMIN", WorkflowStatus.receiverType("驿站服务"));
        assertEquals("STATION_ADMIN", WorkflowStatus.receiverType("设施建议"));
        assertEquals("PLATFORM_ADMIN", WorkflowStatus.receiverType("权益保障"));
        assertEquals("平台管理员", WorkflowStatus.receiverName("权益保障"));
        assertTrue(WorkflowStatus.handlingBoundary("权益保障").contains("不代表行政机关受理"));
    }

    @Test
    void mapsRecordActionsToChineseLabels() {
        assertEquals("提交诉求", WorkflowStatus.actionLabel("SUBMIT"));
        assertEquals("分派处理人", WorkflowStatus.actionLabel("ASSIGN"));
        assertEquals("办结", WorkflowStatus.actionLabel("RESOLVE"));
        assertEquals("用户评价", WorkflowStatus.actionLabel("EVALUATE"));
        assertEquals("操作", WorkflowStatus.actionLabel(null));
    }
}
