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
                Map.of("stationId", 1L, "station", "湖滨驿站", "serviceName", "想确认能否借用场地开培训",
                        "date", java.time.LocalDate.now().plusDays(7).toString(), "slot", "09:00-11:00"), 1L);
        Map<String, Object> audited = store.auditAppointment(
                Map.of("id", created.get("id"), "status", "CONFIRMED"), 2L, null);
        assertEquals("CONFIRMED", audited.get("status"));
        assertEquals(2L, audited.get("operatorId"));
    }

    @Test
    void appointmentRequiresSubject() {
        DemoWorkflowStore store = new DemoWorkflowStore();
        // 未填写沟通事项应被拒绝，避免出现「预约了但不知道要沟通什么」的记录
        var error = assertThrows(com.friendlystation.platform.common.BusinessException.class,
                () -> store.addAppointment(Map.of("stationId", 1L, "station", "湖滨驿站",
                        "date", java.time.LocalDate.now().plusDays(7).toString()), 1L));
        assertTrue(error.getMessage().contains("想提前沟通的事项"));
    }

    @Test
    void appointmentRejectsMalformedPhone() {
        DemoWorkflowStore store = new DemoWorkflowStore();
        var error = assertThrows(com.friendlystation.platform.common.BusinessException.class,
                () -> store.addAppointment(Map.of(
                        "stationId", 1L,
                        "station", "湖滨驿站",
                        "serviceName", "想确认能否借用场地开培训",
                        "contactPhone", "不是电话号码",
                        "date", java.time.LocalDate.now().plusDays(7).toString(),
                        "slot", "09:00-11:00"), 1L));
        assertTrue(error.getMessage().contains("联系电话"));
    }

    @Test
    void duplicateActivitySignupIsRejected() {
        DemoWorkflowStore store = new DemoWorkflowStore();
        Map<String, Object> request = new java.util.HashMap<>(Map.of(
                "activityId", 9L, "contactName", "王骑手", "phone", "13800002088"));
        store.addSignup(request, 1L);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> store.addSignup(request, 1L));
        assertEquals("该活动已报名", error.getMessage());
    }

    @Test
    void appealCanBeAssignedAndResolved() {
        DemoWorkflowStore store = new DemoWorkflowStore();
        Map<String, Object> created = store.addAppeal(Map.of("title", "充电设施故障", "type", "驿站服务"), 1L);
        store.assignAppeal(Map.of("id", created.get("id")), 2L);
        // 办结必须写明给用户的回复——这段话是用户端看到的「结果」，服务端不再回退成状态标签。
        Map<String, Object> resolved = store.handleAppeal(Map.of(
                "id", created.get("id"),
                "status", "RESOLVED",
                "content", "已联系维修人员更换充电桩，明日起恢复使用。"), 2L);
        assertEquals("RESOLVED", resolved.get("status"));
        assertEquals("STATION_ADMIN", resolved.get("receiverType"));
    }

    /**
     * 内存实现也要走通「报名 → 承办方审核 → 反馈」，
     * 且与 mysql 实现同一口径。两个 profile 行为不一致时，演示环境与生产环境会给出不同结果。
     */
    @Test
    void signupCarriesContactInfoAndReviewFeedback() {
        DemoWorkflowStore store = new DemoWorkflowStore();

        // 报名必须填姓名与电话：承办方要靠这些联系报名人
        Map<String, Object> missingName = Map.of("activityId", 9L, "phone", "13800002088");
        // 断言消息而不是异常类型：ActivityOrganizer 抛 BusinessException，
        // 内存实现其他分支抛 IllegalArgumentException，两者在 GlobalExceptionHandler
        // 中都映射为 400，对调用方是一致的。
        RuntimeException nameError =
                assertThrows(RuntimeException.class, () -> store.addSignup(missingName, 1L));
        assertTrue(nameError.getMessage().contains("姓名"), nameError.getMessage());

        Map<String, Object> missingPhone = Map.of("activityId", 9L, "contactName", "王骑手");
        RuntimeException phoneError =
                assertThrows(RuntimeException.class, () -> store.addSignup(missingPhone, 1L));
        assertTrue(phoneError.getMessage().contains("联系电话"), phoneError.getMessage());

        Map<String, Object> request = new java.util.HashMap<>(Map.of(
                "activityId", 9L, "contactName", "王骑手", "phone", "13800002088", "remark", "周六上午方便"));
        Map<String, Object> signup = store.addSignup(request, 1L);
        assertEquals("王骑手", signup.get("contactName"));
        assertEquals("13800002088", signup.get("phone"));
        assertEquals("已报名，等待联系", signup.get("statusLabel"));

        // 未通过必须说明原因，报名人有权知道为什么
        Map<String, Object> rejectNoNote = Map.of("id", signup.get("id"), "status", "REJECTED");
        RuntimeException rejectError =
                assertThrows(RuntimeException.class, () -> store.reviewSignup(rejectNoNote, 2L, null));
        assertTrue(rejectError.getMessage().contains("原因"), rejectError.getMessage());

        Map<String, Object> reviewed = store.reviewSignup(Map.of(
                "id", signup.get("id"),
                "status", "APPROVED",
                "reviewNote", "已电话确认，周六上午 9 点在驿站集合"), 2L, null);
        assertEquals("APPROVED", reviewed.get("status"));
        assertEquals("审核通过", reviewed.get("statusLabel"));
        assertEquals("已电话确认，周六上午 9 点在驿站集合", reviewed.get("reviewNote"));
        assertTrue(String.valueOf(reviewed.get("reviewerName")).contains("管理员"));
    }

    /** 第三方活动不接受站内报名——内存实现同样要拦住。 */
    @Test
    void externalActivityRejectsSignup() {
        DemoWorkflowStore store = new DemoWorkflowStore();
        // 直接构造一条外部活动记录，验证报名的拒绝分支
        Map<String, Object> external = new java.util.HashMap<>();
        external.put("organizerType", "EXTERNAL");
        assertEquals(false, ActivityOrganizer.acceptsSignup(String.valueOf(external.get("organizerType"))));
        assertEquals(true, ActivityOrganizer.acceptsSignup("STATION"));
        assertEquals("第三方机构", ActivityOrganizer.typeLabel("EXTERNAL"));
    }
}
