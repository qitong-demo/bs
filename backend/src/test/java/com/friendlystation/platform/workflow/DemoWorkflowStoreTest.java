package com.friendlystation.platform.workflow;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DemoWorkflowStoreTest {
    @Test
    void appointmentCanBeAudited() {
        DemoWorkflowStore store = new DemoWorkflowStore();
        // 预约事项改为用户自填（serviceName 必填），不再是固定的服务项目 id
        Map<String, Object> created = store.addAppointment(
                Map.of("station", "湖滨驿站", "serviceName", "想确认能否借用场地开培训", "date", "2026-10-07"), 1L);
        Map<String, Object> audited = store.auditAppointment(Map.of("id", created.get("id"), "status", "CONFIRMED"), 2L);
        assertEquals("CONFIRMED", audited.get("status"));
        assertEquals(2L, audited.get("operatorId"));
    }

    @Test
    void appointmentRequiresSubject() {
        DemoWorkflowStore store = new DemoWorkflowStore();
        // 未填写沟通事项应被拒绝，避免出现「预约了但不知道要沟通什么」的记录
        var error = assertThrows(com.friendlystation.platform.common.BusinessException.class,
                () -> store.addAppointment(Map.of("station", "湖滨驿站", "date", "2026-10-07"), 1L));
        assertTrue(error.getMessage().contains("想提前沟通的事项"));
    }

    @Test
    void appointmentRejectsMalformedPhone() {
        DemoWorkflowStore store = new DemoWorkflowStore();
        var error = assertThrows(com.friendlystation.platform.common.BusinessException.class,
                () -> store.addAppointment(Map.of(
                        "station", "湖滨驿站",
                        "serviceName", "想确认能否借用场地开培训",
                        "contactPhone", "不是电话号码",
                        "date", "2026-10-07"), 1L));
        assertTrue(error.getMessage().contains("联系电话"));
    }

    @Test
    void duplicateActivitySignupIsRejected() {
        DemoWorkflowStore store = new DemoWorkflowStore();
        Map<String, Object> request = Map.of("activityId", 9);
        store.addSignup(request, 1L);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> store.addSignup(request, 1L));
        assertEquals("该活动已报名", error.getMessage());
    }

    @Test
    void appealCanBeAssignedAndResolved() {
        DemoWorkflowStore store = new DemoWorkflowStore();
        Map<String, Object> created = store.addAppeal(Map.of("title", "充电设施故障", "type", "驿站服务"), 1L);
        store.assignAppeal(Map.of("id", created.get("id")), 2L);
        Map<String, Object> resolved = store.handleAppeal(Map.of("id", created.get("id"), "status", "RESOLVED"), 2L);
        assertEquals("RESOLVED", resolved.get("status"));
        assertEquals("STATION_ADMIN", resolved.get("receiverType"));
    }
}
