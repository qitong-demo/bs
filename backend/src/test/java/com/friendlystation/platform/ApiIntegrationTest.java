package com.friendlystation.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端接口集成测试。
 *
 * <p>使用默认（非 mysql）profile 启动完整 Spring 上下文，覆盖认证、角色权限、预约与诉求闭环、
 * 活动报名、服务评价、管理端增删改查与报表导出，不依赖外部数据库。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ------------------------------------------------------------ 基础工具

    private String body(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode call(String method, String path, String token, Object payload) throws Exception {
        var request = switch (method) {
            case "post" -> post(path);
            case "put" -> put(path);
            case "delete" -> delete(path);
            default -> get(path);
        };
        request.contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (payload != null) {
            request.content(body(payload).getBytes(StandardCharsets.UTF_8));
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        String raw = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode node = raw.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
        return objectMapper.valueToTree(Map.of("httpStatus", result.getResponse().getStatus(), "body", node));
    }

    private int httpStatus(JsonNode response) {
        return response.path("httpStatus").asInt();
    }

    private JsonNode bodyOf(JsonNode response) {
        return response.path("body");
    }

    private JsonNode dataOf(JsonNode response) {
        return bodyOf(response).path("data");
    }

    private String messageOf(JsonNode response) {
        return bodyOf(response).path("message").asText();
    }

    private String login(String username) throws Exception {
        JsonNode response = call("post", "/api/auth/login", null,
                Map.of("username", username, "password", "password"));
        assertEquals(200, httpStatus(response), "登录应成功：" + username);
        String token = dataOf(response).path("token").asText();
        assertFalse(token.isBlank(), "登录应返回 token");
        return token;
    }

    // ------------------------------------------------------------ 认证与权限

    @Test
    void anonymousRequestIsRejectedWith401() throws Exception {
        JsonNode response = call("get", "/api/appointment/my", null, null);
        assertEquals(401, httpStatus(response));
    }

    @Test
    void invalidTokenIsRejectedWith401() throws Exception {
        JsonNode response = call("get", "/api/appointment/my", "not-a-real-token", null);
        assertEquals(401, httpStatus(response));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        JsonNode response = call("post", "/api/auth/login", null,
                Map.of("username", "rider_xiaowang", "password", "wrong-password"));
        assertEquals(400, httpStatus(response));
    }

    @Test
    void normalUserCannotAccessAdminEndpoints() throws Exception {
        String userToken = login("rider_xiaowang");
        assertEquals(403, httpStatus(call("get", "/api/admin/users", userToken, null)));
        assertEquals(403, httpStatus(call("get", "/api/stat/export/appeals", userToken, null)));
        assertEquals(403, httpStatus(call("put", "/api/appointment/audit", userToken,
                Map.of("id", 1, "status", "CONFIRMED"))));
    }

    @Test
    void stationAdminCannotManageAccountsButCanAudit() throws Exception {
        String stationToken = login("station_admin_01");
        assertEquals(403, httpStatus(call("get", "/api/admin/users", stationToken, null)));
        JsonNode page = call("get", "/api/appointment/admin/page?current=1&size=5", stationToken, null);
        assertEquals(200, httpStatus(page));
    }

    @Test
    void stationAdminOnlySeesAppointmentsOfManagedStations() throws Exception {
        String userToken = login("rider_xiaowang");
        String stationToken = login("station_admin_01");
        String adminToken = login("platform_admin");

        // 演示配置：1~4 号「小哥驿站」由 station_admin_01 负责，5 号及之后未分配负责人
        assertEquals(200, httpStatus(call("post", "/api/appointment", userToken, Map.of(
                "stationId", 1, "serviceName", "集成测试沟通事项", "date", "2026-11-24", "slot", "09:00-11:00"))));
        assertEquals(200, httpStatus(call("post", "/api/appointment", userToken, Map.of(
                "stationId", 5, "serviceName", "集成测试沟通事项", "date", "2026-11-25", "slot", "09:00-11:00"))));

        JsonNode stationPage = dataOf(call("get", "/api/appointment/admin/page?current=1&size=50", stationToken, null));
        assertFalse(stationPage.path("records").isEmpty(), "驿站管理员应能看到自己负责驿站的预约");
        for (JsonNode row : stationPage.path("records")) {
            long stationId = row.path("stationId").asLong();
            assertTrue(stationId <= 4, "驿站管理员看到了非负责驿站的预约：" + stationId);
        }

        JsonNode adminPage = dataOf(call("get", "/api/appointment/admin/page?current=1&size=50", adminToken, null));
        assertTrue(adminPage.path("total").asInt() >= stationPage.path("total").asInt(),
                "平台管理员可见范围应不小于驿站管理员");
    }

    @Test
    void stationAdminOnlySeesAppealsInManagedAreas() throws Exception {
        String userToken = login("rider_xiaowang");
        String stationToken = login("station_admin_01");

        // 负责驿站的所在区域：从驿站详情动态取回，避免把区域写死在测试里
        Set<String> managedAreas = new HashSet<>();
        for (long stationId : new long[]{1L, 2L, 3L, 4L}) {
            managedAreas.add(dataOf(call("get", "/api/station/" + stationId, null, null)).path("area").asText());
        }
        assertFalse(managedAreas.isEmpty());

        // 范围内与范围外各建一条诉求
        String inScopeArea = managedAreas.iterator().next();
        assertEquals(200, httpStatus(call("post", "/api/appeal", userToken, Map.of(
                "title", "数据范围测试诉求（范围内）", "type", "权益保障", "area", inScopeArea, "content", "范围内"))));
        assertEquals(200, httpStatus(call("post", "/api/appeal", userToken, Map.of(
                "title", "数据范围测试诉求（范围外）", "type", "权益保障", "area", "建德市", "content", "范围外"))));

        JsonNode stationPage = dataOf(call("get", "/api/appeal/admin/page?current=1&size=100", stationToken, null));
        boolean sawInScope = false;
        for (JsonNode row : stationPage.path("records")) {
            String area = row.path("area").asText();
            assertTrue(managedAreas.contains(area), "驿站管理员不应看到负责区域之外的诉求：" + area);
            if (inScopeArea.equals(area)) {
                sawInScope = true;
            }
        }
        assertTrue(sawInScope, "负责区域内的诉求应可见");
    }

    // ------------------------------------------------------------ 公开查询

    @Test
    void publicQueriesReturnRealPayloads() throws Exception {
        assertEquals(200, httpStatus(call("get", "/api/station/page?current=1&size=5", null, null)));
        assertEquals(200, httpStatus(call("get", "/api/news/page?current=1&size=5", null, null)));
        assertEquals(200, httpStatus(call("get", "/api/notice/page?current=1&size=5", null, null)));
        assertEquals(200, httpStatus(call("get", "/api/activity/page?current=1&size=5", null, null)));

        JsonNode page = dataOf(call("get", "/api/station/page?current=1&size=5", null, null));
        assertTrue(page.path("total").asInt() > 0, "演示数据应至少有一个驿站");
        assertTrue(page.path("records").get(0).has("stationName"));
        assertTrue(page.path("records").get(0).path("services").isArray());
    }

    @Test
    void missingStationReturnsBusinessError() throws Exception {
        JsonNode response = call("get", "/api/station/999999", null, null);
        assertEquals(400, httpStatus(response));
        assertEquals("驿站不存在", messageOf(response));
    }

    @Test
    void unknownEndpointReturns404InsteadOfServerError() throws Exception {
        JsonNode response = call("get", "/api/not-a-real-endpoint", null, null);
        assertEquals(404, httpStatus(response));
        assertEquals("请求的接口或资源不存在", messageOf(response));
    }

    @Test
    void statisticsOverviewIsFullyAggregated() throws Exception {
        JsonNode data = dataOf(call("get", "/api/stat/overview", null, null));
        for (String key : new String[]{"stationCount", "openStationCount", "closedStationCount", "appointmentCount",
                "appealCount", "finishRate", "activityCount", "signupCount", "evaluationCount", "averageScore",
                "dataScope", "dataStatus", "generatedAt"}) {
            assertTrue(data.has(key), "统计概览应包含字段 " + key);
        }
        assertEquals("系统计算结果", data.path("dataStatus").asText());
    }

    @Test
    void monthlyTrendReturnsSixMonths() throws Exception {
        JsonNode data = dataOf(call("get", "/api/stat/appeal/month", null, null));
        assertEquals(6, data.path("months").size());
        assertEquals(6, data.path("appointments").size());
        assertEquals(6, data.path("finishRate").size());
    }

    // ------------------------------------------------------------ 预约闭环

    @Test
    void appointmentFlowRunsFromSubmitToAudit() throws Exception {
        String userToken = login("rider_xiaowang");
        String adminToken = login("platform_admin");
        int stationId = firstStationId();

        JsonNode created = call("post", "/api/appointment", userToken, Map.of(
                "stationId", stationId, "serviceName", "集成测试沟通事项", "date", "2026-11-20", "slot", "09:00-11:00"));
        assertEquals(200, httpStatus(created), messageOf(created));
        assertEquals("PENDING_STATION_CONFIRMATION", dataOf(created).path("status").asText());
        assertEquals("待驿站确认", dataOf(created).path("statusLabel").asText());
        long appointmentId = dataOf(created).path("id").asLong();

        JsonNode mine = dataOf(call("get", "/api/appointment/my", userToken, null));
        assertTrue(mine.isArray() && mine.size() >= 1);

        JsonNode audit = call("put", "/api/appointment/audit", adminToken,
                Map.of("id", appointmentId, "status", "CONFIRMED"));
        assertEquals(200, httpStatus(audit), messageOf(audit));
        assertEquals("CONFIRMED", dataOf(audit).path("status").asText());

        JsonNode repeated = call("put", "/api/appointment/audit", adminToken,
                Map.of("id", appointmentId, "status", "REJECTED"));
        assertEquals(400, httpStatus(repeated));
        assertEquals("该预约已处理，不能重复审核", messageOf(repeated));

        JsonNode invalidTarget = call("put", "/api/appointment/audit", adminToken,
                Map.of("id", appointmentId, "status", "WHATEVER"));
        assertEquals(400, httpStatus(invalidTarget));
    }

    @Test
    void appointmentCanBeCancelledByOwnerOnly() throws Exception {
        String userToken = login("rider_xiaowang");
        JsonNode created = call("post", "/api/appointment", userToken, Map.of(
                "stationId", firstStationId(), "serviceName", "集成测试沟通事项", "date", "2026-11-21", "slot", "14:00-16:00"));
        long appointmentId = dataOf(created).path("id").asLong();

        JsonNode cancelled = call("put", "/api/appointment/" + appointmentId + "/cancel", userToken, null);
        assertEquals(200, httpStatus(cancelled), messageOf(cancelled));
        assertEquals("CANCELLED", dataOf(cancelled).path("status").asText());

        JsonNode again = call("put", "/api/appointment/" + appointmentId + "/cancel", userToken, null);
        assertEquals(400, httpStatus(again));
        assertEquals("只有待确认的预约可以取消", messageOf(again));
    }

    // ------------------------------------------------------------ 诉求闭环

    @Test
    void appealFlowRecordsEveryTransition() throws Exception {
        String userToken = login("rider_xiaowang");
        String adminToken = login("platform_admin");

        JsonNode created = call("post", "/api/appeal", userToken, Map.of(
                "title", "集成测试诉求", "type", "驿站服务", "area", "西湖区",
                "content", "集成测试内容", "anonymous", false));
        assertEquals(200, httpStatus(created), messageOf(created));
        assertEquals("PENDING_ACCEPTANCE", dataOf(created).path("status").asText());
        assertEquals("STATION_ADMIN", dataOf(created).path("receiverType").asText());
        long appealId = dataOf(created).path("id").asLong();

        JsonNode records = dataOf(call("get", "/api/appeal/" + appealId + "/records", userToken, null));
        assertEquals(1, records.size());
        assertEquals("SUBMIT", records.get(0).path("action").asText());

        assertEquals(200, httpStatus(call("put", "/api/appeal/assign", adminToken,
                Map.of("id", appealId, "content", "分派处理"))));

        JsonNode resolved = call("put", "/api/appeal/handle", adminToken,
                Map.of("id", appealId, "status", "RESOLVED", "content", "已办结"));
        assertEquals(200, httpStatus(resolved), messageOf(resolved));
        assertEquals("RESOLVED", dataOf(resolved).path("status").asText());
        assertFalse(dataOf(resolved).path("finishTime").asText().isBlank(), "办结应写入办结时间");

        JsonNode detail = dataOf(call("get", "/api/appeal/" + appealId, userToken, null));
        assertTrue(detail.path("records").size() >= 3, "应留存提交、分派与办结记录");

        JsonNode evaluation = call("post", "/api/appeal/evaluate", userToken,
                Map.of("appealId", appealId, "score", 5, "content", "处理及时"));
        assertEquals(200, httpStatus(evaluation), messageOf(evaluation));

        JsonNode duplicateHandle = call("put", "/api/appeal/handle", adminToken,
                Map.of("id", appealId, "status", "RESOLVED", "content", "重复办结"));
        assertEquals(400, httpStatus(duplicateHandle));
        assertEquals("该诉求已办结或关闭，不能重复处理", messageOf(duplicateHandle));
    }

    @Test
    void appealCannotBeEvaluatedBeforeResolution() throws Exception {
        String userToken = login("rider_xiaowang");
        JsonNode created = call("post", "/api/appeal", userToken, Map.of(
                "title", "未办结的诉求", "type", "权益保障", "area", "拱墅区", "content", "内容"));
        long appealId = dataOf(created).path("id").asLong();

        JsonNode evaluation = call("post", "/api/appeal/evaluate", userToken,
                Map.of("appealId", appealId, "score", 4, "content", "提前评价"));
        assertEquals(400, httpStatus(evaluation));
        assertEquals("诉求办结后才能评价", messageOf(evaluation));
    }

    @Test
    void anonymousAppealHidesSubmitterInAdminList() throws Exception {
        String userToken = login("rider_xiaowang");
        String adminToken = login("platform_admin");
        call("post", "/api/appeal", userToken, Map.of(
                "title", "匿名集成测试诉求", "type", "其他问题", "area", "西湖区",
                "content", "匿名内容", "anonymous", true));

        JsonNode page = dataOf(call("get", "/api/appeal/admin/page?current=1&size=50&keyword=匿名集成测试诉求", adminToken, null));
        assertTrue(page.path("total").asInt() >= 1);
        assertEquals("匿名用户", page.path("records").get(0).path("userName").asText());
    }

    // ------------------------------------------------------------ 活动与评价

    @Test
    void activitySignupRejectsDuplicates() throws Exception {
        String userToken = login("rider_xiaowang");
        int activityId = firstActivityId();

        JsonNode first = call("post", "/api/activity/signup", userToken, Map.of("activityId", activityId, "contactName", "王骑手", "phone", "13800002088"));
        assertEquals(200, httpStatus(first), messageOf(first));

        JsonNode duplicate = call("post", "/api/activity/signup", userToken, Map.of("activityId", activityId, "contactName", "王骑手", "phone", "13800002088"));
        assertEquals(400, httpStatus(duplicate));
        assertTrue(messageOf(duplicate).contains("已报名"));

        JsonNode missing = call("post", "/api/activity/signup", userToken, Map.of("activityId", 999999));
        assertEquals(400, httpStatus(missing));
        assertEquals("活动不存在", messageOf(missing));

        JsonNode mySignups = dataOf(call("get", "/api/activity/my", userToken, null));
        assertTrue(mySignups.isArray() && mySignups.size() >= 1);
    }

    @Test
    void stationEvaluationValidatesScoreAndStation() throws Exception {
        String userToken = login("rider_xiaowang");
        int stationId = firstStationId();

        assertEquals(200, httpStatus(call("post", "/api/evaluation/station", userToken,
                Map.of("stationId", stationId, "score", 5, "content", "集成测试评价"))));

        JsonNode badScore = call("post", "/api/evaluation/station", userToken,
                Map.of("stationId", stationId, "score", 9, "content", "越界评分"));
        assertEquals(400, httpStatus(badScore));
        assertEquals("综合评分必须在 1 到 5 之间", messageOf(badScore));

        JsonNode badStation = call("post", "/api/evaluation/station", userToken,
                Map.of("stationId", 999999, "score", 5, "content", "不存在的驿站"));
        assertEquals(400, httpStatus(badStation));
        assertEquals("驿站不存在", messageOf(badStation));

        JsonNode summary = dataOf(call("get", "/api/evaluation/station/summary?stationId=" + stationId, null, null));
        assertTrue(summary.path("count").asInt() >= 1);
        assertTrue(summary.path("averageScore").asDouble() >= 1.0);

        JsonNode page = dataOf(call("get", "/api/evaluation/station/page?current=1&size=5&stationId=" + stationId, null, null));
        assertTrue(page.path("total").asInt() >= 1);
        assertTrue(page.path("records").get(0).has("userName"));
    }

    // ------------------------------------------------------------ 管理端维护

    @Test
    void stationCrudRejectsInvalidStatusAndMissingRecord() throws Exception {
        String adminToken = login("platform_admin");

        JsonNode created = call("post", "/api/station", adminToken, Map.of(
                "stationName", "集成测试驿站", "area", "滨江区", "address", "集成测试路 1 号",
                "contactPhone", "0571-99999999", "openTime", "09:00-18:00"));
        assertEquals(200, httpStatus(created), messageOf(created));
        long stationId = dataOf(created).path("id").asLong();

        JsonNode updated = call("put", "/api/station/" + stationId, adminToken, Map.of(
                "stationName", "集成测试驿站（改名）", "area", "滨江区", "address", "集成测试路 2 号"));
        assertEquals(200, httpStatus(updated), messageOf(updated));
        assertTrue(dataOf(updated).path("stationName").asText().contains("改名"));

        JsonNode badStatus = call("put", "/api/station/" + stationId + "/status?status=UNKNOWN", adminToken, null);
        assertEquals(400, httpStatus(badStatus));
        assertEquals("驿站状态只能是 OPEN 或 CLOSED", messageOf(badStatus));

        assertEquals(200, httpStatus(call("put", "/api/station/" + stationId + "/status?status=CLOSED", adminToken, null)));
        assertEquals(200, httpStatus(call("delete", "/api/station/" + stationId, adminToken, null)));
        assertEquals(400, httpStatus(call("delete", "/api/station/" + stationId, adminToken, null)));
        assertEquals(400, httpStatus(call("put", "/api/station/" + stationId, adminToken,
                Map.of("stationName", "x", "area", "y", "address", "z"))));
    }

    @Test
    void stationServiceItemCrudWorksAgainstRealStore() throws Exception {
        String adminToken = login("platform_admin");
        int stationId = firstStationId();

        JsonNode page = dataOf(call("get", "/api/station/service/page?current=1&size=10&stationId=" + stationId, null, null));
        assertTrue(page.path("total").asInt() >= 1, "演示数据应包含驿站服务项目");

        JsonNode created = call("post", "/api/station/service", adminToken, Map.of(
                "stationId", stationId, "serviceName", "集成测试服务", "serviceType", "GENERAL", "needAppoint", true));
        assertEquals(200, httpStatus(created), messageOf(created));
        long serviceId = dataOf(created).path("id").asLong();

        JsonNode listed = dataOf(call("get", "/api/station/service/list?stationId=" + stationId, null, null));
        assertTrue(listed.isArray() && listed.size() >= 1);

        assertEquals(200, httpStatus(call("put", "/api/station/service/" + serviceId + "/status?status=0", adminToken, null)));
        assertEquals(200, httpStatus(call("delete", "/api/station/service/" + serviceId, adminToken, null)));
        assertEquals(400, httpStatus(call("delete", "/api/station/service/" + serviceId, adminToken, null)));
    }

    @Test
    void newsNoticeAndActivityCrudWorks() throws Exception {
        String adminToken = login("platform_admin");

        JsonNode news = call("post", "/api/news", adminToken, Map.of(
                "title", "集成测试资讯", "category", "政策", "content", "正文"));
        assertEquals(200, httpStatus(news), messageOf(news));
        long newsId = dataOf(news).path("id").asLong();
        assertEquals(200, httpStatus(call("put", "/api/news/" + newsId, adminToken,
                Map.of("title", "集成测试资讯（改）", "category", "动态", "content", "新正文"))));
        assertEquals("集成测试资讯（改）", dataOf(call("get", "/api/news/" + newsId, null, null)).path("title").asText());
        assertEquals(200, httpStatus(call("delete", "/api/news/" + newsId, adminToken, null)));
        assertEquals(400, httpStatus(call("get", "/api/news/" + newsId, null, null)));

        JsonNode emptyTitle = call("post", "/api/news", adminToken, Map.of("title", "  ", "category", "政策"));
        assertEquals(400, httpStatus(emptyTitle));
        assertEquals("资讯标题不能为空", messageOf(emptyTitle));

        JsonNode notice = call("post", "/api/notice", adminToken, Map.of(
                "title", "集成测试公告", "noticeType", "通知", "content", "公告正文"));
        assertEquals(200, httpStatus(notice), messageOf(notice));
        assertEquals(200, httpStatus(call("delete", "/api/notice/" + dataOf(notice).path("id").asLong(), adminToken, null)));

        JsonNode activity = call("post", "/api/activity/admin", adminToken, Map.of(
                "title", "集成测试活动", "stationId", firstStationId(), "activityTime", "2026-12-01 09:00:00",
                "address", "集成测试地点", "maxCount", 10, "content", "活动正文"));
        assertEquals(200, httpStatus(activity), messageOf(activity));
        long activityId = dataOf(activity).path("id").asLong();
        assertTrue(dataOf(activity).has("remaining"));

        JsonNode badCapacity = call("post", "/api/activity/admin", adminToken, Map.of(
                "title", "名额非法活动", "stationId", firstStationId(), "activityTime", "2026-12-02 09:00:00", "maxCount", 0));
        assertEquals(400, httpStatus(badCapacity));
        assertEquals("活动名额必须大于 0", messageOf(badCapacity));

        assertEquals(200, httpStatus(call("delete", "/api/activity/admin/" + activityId, adminToken, null)));
    }

    @Test
    void activityCanReferenceANewlyCreatedStation() throws Exception {
        String adminToken = login("platform_admin");

        JsonNode station = call("post", "/api/station", adminToken, Map.of(
                "stationName", "集成测试活动驿站", "area", "滨江区", "address", "集成测试路 9 号"));
        assertEquals(200, httpStatus(station), messageOf(station));
        long stationId = dataOf(station).path("id").asLong();

        JsonNode activity = call("post", "/api/activity/admin", adminToken, Map.of(
                "title", "新驿站活动", "stationId", stationId,
                "activityTime", "2026-12-05 09:00:00", "maxCount", 5));
        assertEquals(200, httpStatus(activity), messageOf(activity));
        assertEquals(stationId, dataOf(activity).path("stationId").asLong());
        assertEquals("集成测试活动驿站", dataOf(activity).path("stationName").asText());
    }

    // ------------------------------------------------------------ 账号与角色

    @Test
    void adminCanListUsersAndChangeRoles() throws Exception {
        String adminToken = login("platform_admin");

        JsonNode users = dataOf(call("get", "/api/admin/users?current=1&size=10", adminToken, null));
        assertTrue(users.path("total").asInt() >= 3);
        assertTrue(users.path("records").get(0).has("roleName"));

        JsonNode roles = dataOf(call("get", "/api/admin/roles", adminToken, null));
        // 系统只有三个身份：普通用户 / 驿站管理员 / 管理员（原 SUPER_ADMIN 已删除，
        // 它只定义过角色行、从未分配给任何账号，属"从未启用的幽灵角色"）
        assertEquals(3, roles.size());

        long userId = users.path("records").get(0).path("id").asLong();
        JsonNode updated = call("put", "/api/admin/users/" + userId + "/roles", adminToken,
                Map.of("roleCodes", new String[]{"USER"}));
        assertEquals(200, httpStatus(updated), messageOf(updated));

        JsonNode invalidRole = call("put", "/api/admin/users/" + userId + "/roles", adminToken,
                Map.of("roleCodes", new String[]{"NOT_A_ROLE"}));
        assertEquals(400, httpStatus(invalidRole));
        assertTrue(messageOf(invalidRole).contains("角色编码无效"));

        JsonNode emptyRoles = call("put", "/api/admin/users/" + userId + "/roles", adminToken,
                Map.of("roleCodes", new String[]{}));
        assertEquals(400, httpStatus(emptyRoles));
        assertEquals("至少需要保留一个角色", messageOf(emptyRoles));
    }

    @Test
    void profileEndpointsReturnCurrentAccount() throws Exception {
        String userToken = login("rider_xiaowang");
        JsonNode info = dataOf(call("get", "/api/user/info", userToken, null));
        assertEquals("rider_xiaowang", info.path("username").asText());

        JsonNode updated = call("put", "/api/user/info", userToken,
                Map.of("realName", "骑手小王", "phone", "13800002088", "jobType", "外卖骑手", "area", "西湖区"));
        assertEquals(200, httpStatus(updated), messageOf(updated));
        assertEquals("骑手小王", dataOf(updated).path("realName").asText());
    }

    // ------------------------------------------------------------ 报表导出

    @Test
    void csvExportIsAvailableForAdministratorsOnly() throws Exception {
        String adminToken = login("platform_admin");
        String userToken = login("rider_xiaowang");

        for (String type : new String[]{"appeals", "appointments", "stations", "signups", "evaluations"}) {
            MvcResult result = mockMvc.perform(get("/api/stat/export/" + type)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andReturn();
            String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertTrue(csv.length() > 1, "导出 " + type + " 应有内容");
            assertTrue(result.getResponse().getHeader("Content-Disposition").contains(".csv"));
        }

        JsonNode unknown = call("get", "/api/stat/export/unknown", adminToken, null);
        assertEquals(400, httpStatus(unknown));
        assertTrue(messageOf(unknown).contains("不支持的导出类型"));

        assertEquals(403, httpStatus(call("get", "/api/stat/export/appeals", userToken, null)));
    }

    // ------------------------------------------------------------ 辅助

    private int firstStationId() throws Exception {
        JsonNode page = dataOf(call("get", "/api/station/page?current=1&size=1", null, null));
        int total = page.path("total").asInt();
        assertTrue(total > 0, "演示数据应至少包含一个驿站");
        return page.path("records").get(0).path("id").asInt();
    }

    private int firstActivityId() throws Exception {
        JsonNode page = dataOf(call("get", "/api/activity/page?current=1&size=1", null, null));
        assertTrue(page.path("total").asInt() > 0, "演示数据应至少包含一个活动");
        JsonNode first = page.path("records").get(0);
        assertTrue(first.path("remaining").asInt() > 0, "演示活动应有剩余名额");
        return first.path("id").asInt();
    }
}
