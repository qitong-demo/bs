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

        // 服务结束后进入真正的终态：CONFIRMED -> FINISHED
        JsonNode finished = call("put", "/api/appointment/" + appointmentId + "/finish", adminToken, null);
        assertEquals(200, httpStatus(finished), messageOf(finished));
        assertEquals("FINISHED", dataOf(finished).path("status").asText());
        JsonNode finishAgain = call("put", "/api/appointment/" + appointmentId + "/finish", adminToken, null);
        assertEquals(400, httpStatus(finishAgain));
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
        // 演示数据里 rider 已经评价过 1、3 号驿站；重复评价现在会被拒绝，
        // 因此这里动态挑一个「当前用户还没评价过」的驿站。
        long stationId = firstUnratedStationId(userToken);

        assertEquals(200, httpStatus(call("post", "/api/evaluation/station", userToken,
                Map.of("stationId", stationId, "score", 5, "content", "集成测试评价"))));

        JsonNode duplicate = call("post", "/api/evaluation/station", userToken,
                Map.of("stationId", stationId, "score", 4, "content", "重复评价"));
        assertEquals(400, httpStatus(duplicate));
        assertTrue(messageOf(duplicate).contains("不能重复评价"));

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
        long firstView = dataOf(call("get", "/api/news/" + newsId, null, null)).path("viewCount").asLong();
        long secondView = dataOf(call("get", "/api/news/" + newsId, null, null)).path("viewCount").asLong();
        assertTrue(secondView > firstView, "资讯详情被打开后阅读数应增长");
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

    @Test
    void signupCanBeCancelledBeforeReview() throws Exception {
        String userToken = login("rider_xiaowang");
        String adminToken = login("platform_admin");

        JsonNode activity = call("post", "/api/activity/admin", adminToken, Map.of(
                "title", "报名取消测试", "stationId", firstStationId(),
                "activityTime", "2026-12-10 09:00:00", "maxCount", 10));
        long activityId = dataOf(activity).path("id").asLong();

        JsonNode signup = call("post", "/api/activity/signup", userToken, Map.of(
                "activityId", activityId, "contactName", "取消测试", "phone", "13800000009"));
        assertEquals(200, httpStatus(signup), messageOf(signup));
        long signupId = dataOf(signup).path("id").asLong();

        JsonNode cancelled = call("put", "/api/activity/signup/" + signupId + "/cancel", userToken, null);
        assertEquals(200, httpStatus(cancelled), messageOf(cancelled));
        assertEquals("CANCELLED", dataOf(cancelled).path("status").asText());

        assertEquals(400, httpStatus(call("put", "/api/activity/signup/" + signupId + "/cancel", userToken, null)));
        assertEquals(400, httpStatus(call("put", "/api/activity/signup/review", adminToken,
                Map.of("id", signupId, "status", "APPROVED", "reviewNote", "取消后不应再审核"))));

        // 取消后可以重新报名（同一唯一索引记录被重新激活，不会撞 uk_activity_user）
        JsonNode again = call("post", "/api/activity/signup", userToken, Map.of(
                "activityId", activityId, "contactName", "取消测试", "phone", "13800000009"));
        assertEquals(200, httpStatus(again), messageOf(again));
        assertEquals("SIGNED_UP", dataOf(again).path("status").asText());
    }

    @Test
    void appointmentAuditWritesNotification() throws Exception {
        String userToken = login("rider_xiaowang");
        String adminToken = login("platform_admin");

        JsonNode created = call("post", "/api/appointment", userToken, Map.of(
                "stationId", firstStationId(), "serviceName", "通知回归测试",
                "date", java.time.LocalDate.now().plusDays(8).toString(), "slot", "09:00-11:00"));
        long appointmentId = dataOf(created).path("id").asLong();
        JsonNode audited = call("put", "/api/appointment/audit", adminToken,
                Map.of("id", appointmentId, "status", "CONFIRMED", "reason", "请按时到场"));
        assertEquals(200, httpStatus(audited), messageOf(audited));

        JsonNode page = dataOf(call("get", "/api/notification/page?current=1&size=50", userToken, null));
        JsonNode target = null;
        for (JsonNode row : page.path("records")) {
            if (row.path("bizId").asLong() == appointmentId && "APPOINTMENT".equals(row.path("bizType").asText())) {
                target = row;
            }
        }
        assertNotNull(target, "预约审核后应给预约人写入通知");
        assertEquals("预约已确认", target.path("title").asText());
        assertTrue(target.path("content").asText().contains("请按时到场"));

        long unread = dataOf(call("get", "/api/notification/unread-count", userToken, null)).path("count").asLong();
        assertTrue(unread >= 1);
        assertEquals(200, httpStatus(call("put", "/api/notification/" + target.path("id").asLong() + "/read",
                userToken, null)));
        long afterRead = dataOf(call("get", "/api/notification/unread-count", userToken, null)).path("count").asLong();
        assertEquals(unread - 1, afterRead);
    }

    @Test
    void newWorkItemsNotifyStationManager() throws Exception {
        String userToken = login("rider_xiaowang");
        String stationToken = login("station_admin_01");
        String adminToken = login("platform_admin");
        int stationId = firstStationId();

        assertEquals(200, httpStatus(call("post", "/api/appointment", userToken, Map.of(
                "stationId", stationId, "serviceName", "负责人通知测试",
                "date", java.time.LocalDate.now().plusDays(9).toString(), "slot", "09:00-11:00"))));

        assertEquals(200, httpStatus(call("post", "/api/appeal", userToken, Map.of(
                "title", "负责人诉求通知测试", "content", "内容", "category", "REQUEST",
                "receiverTarget", "STATION", "stationId", stationId, "area", "西湖区"))));

        JsonNode activity = call("post", "/api/activity/admin", adminToken, Map.of(
                "title", "负责人报名通知测试", "stationId", stationId,
                "activityTime", "2025-01-01 09:00:00", "maxCount", 5));
        long activityId = dataOf(activity).path("id").asLong();
        assertEquals(200, httpStatus(call("post", "/api/activity/signup", userToken, Map.of(
                "activityId", activityId, "contactName", "通知测试", "phone", "13800000009"))));

        JsonNode page = dataOf(call("get", "/api/notification/page?current=1&size=100", stationToken, null));
        boolean appointmentNotified = false;
        boolean appealNotified = false;
        boolean signupNotified = false;
        boolean signupLinkPresent = false;
        for (JsonNode row : page.path("records")) {
            String title = row.path("title").asText();
            appointmentNotified |= "新的预约待确认".equals(title);
            appealNotified |= "新的诉求待受理".equals(title);
            signupNotified |= "新的活动报名".equals(title);
            if ("新的活动报名".equals(title) && row.path("link").asText().contains("tab=signups")) {
                signupLinkPresent = true;
            }
        }
        assertTrue(appointmentNotified, "新预约应通知驿站负责人");
        assertTrue(appealNotified, "定向驿站的新诉求应通知负责人");
        assertTrue(signupNotified, "新报名应通知活动所属驿站负责人");
        assertTrue(signupLinkPresent, "报名通知应带管理端处理链接");
    }

    @Test
    void platformAdminCanReviewAnySignup() throws Exception {
        String userToken = login("rider_xiaowang");
        String stationToken = login("station_admin_01");
        String adminToken = login("platform_admin");
        int stationId = firstStationId();

        JsonNode activity = call("post", "/api/activity/admin", adminToken, Map.of(
                "title", "平台处理报名测试-" + System.nanoTime(), "stationId", stationId,
                "activityTime", "2026-12-31 09:00:00", "maxCount", 5, "status", "PUBLISHED"));
        long activityId = dataOf(activity).path("id").asLong();
        JsonNode signup = call("post", "/api/activity/signup", userToken, Map.of(
                "activityId", activityId, "contactName", "平台处理", "phone", "13800000010"));
        long signupId = dataOf(signup).path("id").asLong();

        long stationPending = dataOf(call("get", "/api/activity/admin/signups/pending-count", stationToken, null))
                .path("count").asLong();
        long adminPending = dataOf(call("get", "/api/activity/admin/signups/pending-count", adminToken, null))
                .path("count").asLong();
        assertTrue(stationPending >= 1, "驿站端应看到待处理报名");
        assertTrue(adminPending >= 1, "平台端应看到待处理报名");

        assertEquals(200, httpStatus(call("put", "/api/activity/signup/review", adminToken, Map.of(
                "id", signupId, "status", "APPROVED", "reviewNote", "平台审核通过"))));
        JsonNode adminSignups = dataOf(call("get", "/api/activity/admin/signups", adminToken, null));
        boolean approved = false;
        for (JsonNode row : adminSignups) {
            if (row.path("id").asLong() == signupId) {
                approved = "APPROVED".equals(row.path("status").asText());
            }
        }
        assertTrue(approved, "平台管理员应能直接审核任意报名");
    }

    @Test
    void serviceAdminPageIsScopedToManagedStations() throws Exception {
        String stationToken = login("station_admin_01");
        String adminToken = login("platform_admin");

        JsonNode stationPage = dataOf(call("get", "/api/station/service/admin/page?current=1&size=50", stationToken, null));
        assertTrue(stationPage.path("records").size() > 0, "驿站端应能看到自己驿站的服务项目");
        for (JsonNode row : stationPage.path("records")) {
            long stationId = row.path("stationId").asLong();
            assertTrue(stationId >= 1 && stationId <= 4, "驿站端不应看到其他驿站的服务项目：" + stationId);
        }
        JsonNode other = dataOf(call("get", "/api/station/service/admin/page?current=1&size=10&stationId=5", stationToken, null));
        assertEquals(0, other.path("total").asInt(), "驿站端不应能查 5 号驿站的服务项目");

        JsonNode platform = dataOf(call("get", "/api/station/service/admin/page?current=1&size=10&stationId=5", adminToken, null));
        assertTrue(platform.path("total").asInt() > 0, "平台端应能查 5 号驿站的服务项目");
    }

    @Test
    void stationAdminManagesOnlyOwnServiceItems() throws Exception {
        String stationToken = login("station_admin_01");
        int stationId = firstStationId();

        JsonNode own = call("post", "/api/station/service", stationToken, Map.of(
                "stationId", stationId, "serviceName", "权限联调服务", "serviceType", "GENERAL", "needAppoint", false));
        assertEquals(200, httpStatus(own), messageOf(own));
        long serviceId = dataOf(own).path("id").asLong();

        JsonNode other = call("post", "/api/station/service", stationToken, Map.of(
                "stationId", 5, "serviceName", "越权服务", "serviceType", "GENERAL", "needAppoint", false));
        assertEquals(403, httpStatus(other));

        assertEquals(200, httpStatus(call("delete", "/api/station/service/" + serviceId, stationToken, null)));
    }

    @Test
    void appealEvaluationIsMarkedAndNotRepeatable() throws Exception {
        String userToken = login("rider_xiaowang");
        String adminToken = login("platform_admin");
        String title = "评价标记测试-" + System.nanoTime();

        JsonNode appeal = call("post", "/api/appeal", userToken, Map.of(
                "title", title, "content", "内容", "category", "REQUEST", "area", "西湖区",
                "receiverTarget", "PLATFORM"));
        long appealId = dataOf(appeal).path("id").asLong();
        assertEquals(200, httpStatus(call("put", "/api/appeal/handle", adminToken,
                Map.of("id", appealId, "status", "RESOLVED", "content", "已处理，感谢反馈"))));
        assertEquals(200, httpStatus(call("post", "/api/appeal/evaluate", userToken,
                Map.of("appealId", appealId, "score", 5, "content", "满意"))));

        JsonNode mine = dataOf(call("get", "/api/appeal/my", userToken, null));
        boolean evaluated = false;
        for (JsonNode row : mine) {
            if (row.path("id").asLong() == appealId) {
                evaluated = row.path("evaluated").asBoolean();
            }
        }
        assertTrue(evaluated, "评价后列表应标记 evaluated=true");
        assertEquals(400, httpStatus(call("post", "/api/appeal/evaluate", userToken,
                Map.of("appealId", appealId, "score", 4, "content", "重复评价"))));
    }

    @Test
    void suspensionBlocksAppointmentsInWindow() throws Exception {
        String adminToken = login("platform_admin");
        String userToken = login("rider_xiaowang");
        int stationId = firstStationId();
        String day = java.time.LocalDate.now().plusDays(6).toString();

        // 暂停前先有一条落在窗口内的预约，用来验证“暂停通知已预约用户”
        JsonNode before = call("post", "/api/appointment", userToken, Map.of(
                "stationId", stationId, "serviceName", "暂停前预约", "date", day, "slot", "09:00-11:00"));
        assertEquals(200, httpStatus(before), messageOf(before));

        JsonNode suspended = call("put", "/api/station/" + stationId + "/suspend", adminToken, Map.of(
                "from", day + " 00:00:00", "to", day + " 23:59:59", "reason", "设备检修"));
        assertEquals(200, httpStatus(suspended), messageOf(suspended));
        assertEquals(day + " 00:00:00", dataOf(suspended).path("suspendedFrom").asText());

        JsonNode notifications = dataOf(call("get", "/api/notification/page?current=1&size=50", userToken, null));
        boolean notified = false;
        for (JsonNode row : notifications.path("records")) {
            if ("驿站暂停服务提醒".equals(row.path("title").asText())) {
                notified = true;
            }
        }
        assertTrue(notified, "暂停服务应通知窗口内已预约的用户");

        JsonNode blocked = call("post", "/api/appointment", userToken, Map.of(
                "stationId", stationId, "serviceName", "暂停期间预约", "date", day, "slot", "09:00-11:00"));
        assertEquals(400, httpStatus(blocked));
        assertTrue(messageOf(blocked).contains("暂停服务"), messageOf(blocked));

        assertEquals(200, httpStatus(call("delete", "/api/station/" + stationId + "/suspend", adminToken, null)));
        JsonNode allowed = call("post", "/api/appointment", userToken, Map.of(
                "stationId", stationId, "serviceName", "恢复后预约", "date", day, "slot", "09:00-11:00"));
        assertEquals(200, httpStatus(allowed), messageOf(allowed));
    }

    @Test
    void stationChangeListIsScopedToManagedStations() throws Exception {
        String stationToken = login("station_admin_01");
        String adminToken = login("platform_admin");

        // 变更审批只给平台管理员：驿站端只能提交/查看自己的申请
        assertEquals(403, httpStatus(call("get", "/api/station/change/pending", stationToken, null)));

        JsonNode own = call("post", "/api/station/change", stationToken, Map.of(
                "stationId", 1, "type", "PHONE", "newValue", "0571-66665555", "reason", "范围测试"));
        assertEquals(200, httpStatus(own), messageOf(own));
        long ownId = dataOf(own).path("id").asLong();

        JsonNode adminNotes = dataOf(call("get", "/api/notification/page?current=1&size=50", adminToken, null));
        boolean reviewerNotified = false;
        for (JsonNode row : adminNotes.path("records")) {
            if ("新的驿站变更申请".equals(row.path("title").asText())) {
                reviewerNotified = true;
            }
        }
        assertTrue(reviewerNotified, "新变更申请应通知平台管理员");

        JsonNode mine = dataOf(call("get", "/api/station/change/mine", stationToken, null));
        assertTrue(mine.isArray());
        boolean sawOwn = false;
        for (JsonNode row : mine) {
            long stationId = row.path("stationId").asLong();
            assertTrue(stationId >= 1 && stationId <= 4, "驿站管理员只应看到自己负责驿站的申请：" + stationId);
            if (row.path("id").asLong() == ownId) {
                sawOwn = true;
            }
        }
        assertTrue(sawOwn, "我的申请应包含刚提交的记录");

        // 不能查看/代提别家驿站的申请
        assertEquals(403, httpStatus(call("get", "/api/station/change/list?stationId=5", stationToken, null)));
        assertEquals(400, httpStatus(call("post", "/api/station/change", stationToken, Map.of(
                "stationId", 5, "type", "PHONE", "newValue", "0571-00000000", "reason", "越权"))));

        // 平台管理员能看到全部待审批
        JsonNode pending = dataOf(call("get", "/api/station/change/pending", adminToken, null));
        assertTrue(pending.isArray());
        assertEquals(200, httpStatus(call("put", "/api/station/change/" + ownId + "/cancel", stationToken, null)));
        // 撤销后不能再被审批
        assertEquals(400, httpStatus(call("put", "/api/station/change/" + ownId + "/approve", adminToken, Map.of())));
    }

    @Test
    void securityGuardsRejectCrossTenantAndInvalidState() throws Exception {
        String rider = login("rider_xiaowang");
        String station = login("station_admin_01");
        String admin = login("platform_admin");

        // 1) 驿站管理员不能审核不属于自己负责驿站的预约
        JsonNode appointment = call("post", "/api/appointment", rider, Map.of(
                "stationId", 5, "serviceName", "越权审核测试",
                "date", java.time.LocalDate.now().plusDays(5).toString(), "slot", "09:00-11:00"));
        assertEquals(200, httpStatus(appointment), messageOf(appointment));
        long appointmentId = dataOf(appointment).path("id").asLong();
        JsonNode crossAudit = call("put", "/api/appointment/audit", station,
                Map.of("id", appointmentId, "status", "CONFIRMED"));
        assertEquals(403, httpStatus(crossAudit));

        // 2) 匿名诉求在详情里也必须隐藏提交人（管理端列表与详情同口径）
        String area = dataOf(call("get", "/api/station/1", null, null)).path("area").asText();
        JsonNode anonymous = call("post", "/api/appeal", rider, Map.of(
                "title", "匿名详情测试", "content", "内容", "anonymous", true,
                "area", area, "category", "REQUEST", "stationId", 1, "receiverTarget", "STATION"));
        long appealId = dataOf(anonymous).path("id").asLong();
        JsonNode detail = dataOf(call("get", "/api/appeal/" + appealId, station, null));
        assertEquals("匿名用户", detail.path("userName").asText());
        assertEquals(0, detail.path("userId").asLong());
        // 定向驿站按 station_id 路由，不再依赖用户手填区域
        assertEquals(1, detail.path("stationId").asLong());

        // 3) 活动取消后不再接收报名
        JsonNode activity = call("post", "/api/activity/admin", admin, Map.of(
                "title", "取消后报名测试", "stationId", firstStationId(),
                "activityTime", "2026-12-09 09:00:00", "maxCount", 5));
        long activityId = dataOf(activity).path("id").asLong();
        assertEquals(200, httpStatus(call("put", "/api/activity/admin/" + activityId, admin,
                Map.of("status", "CANCELLED"))));
        assertEquals(400, httpStatus(call("post", "/api/activity/signup", rider, Map.of(
                "activityId", activityId, "contactName", "测试", "phone", "13800000009"))));

        // 4) 变更审批通过后不能再驳回
        JsonNode change = call("post", "/api/station/change", station, Map.of(
                "stationId", 1, "type", "PHONE", "newValue", "0571-88887777", "reason", "回归测试"));
        long changeId = dataOf(change).path("id").asLong();
        // 驿站管理员不能审批自己提交的申请
        assertEquals(403, httpStatus(call("put", "/api/station/change/" + changeId + "/approve", station, Map.of())));
        assertEquals(200, httpStatus(call("put", "/api/station/change/" + changeId + "/approve", admin, Map.of())));
        JsonNode changeNotes = dataOf(call("get", "/api/notification/page?current=1&size=50", station, null));
        boolean changeNotified = false;
        for (JsonNode row : changeNotes.path("records")) {
            if ("变更申请已通过".equals(row.path("title").asText())) {
                changeNotified = true;
            }
        }
        assertTrue(changeNotified, "变更审批结果应通知申请人");
        assertEquals(400, httpStatus(call("put", "/api/station/change/" + changeId + "/reject", admin,
                Map.of("reviewNote", "尝试回退"))));

        // 申请人可以撤回仍待审批的申请；撤回后审批端不能再通过
        JsonNode withdraw = call("post", "/api/station/change", station, Map.of(
                "stationId", 2, "type", "PHONE", "newValue", "0571-77776666", "reason", "撤回测试"));
        long withdrawId = dataOf(withdraw).path("id").asLong();
        assertEquals(200, httpStatus(call("put", "/api/station/change/" + withdrawId + "/cancel", station, null)));
        assertEquals(400, httpStatus(call("put", "/api/station/change/" + withdrawId + "/approve", admin, Map.of())));

        // 5) 角色降级后旧令牌立即失效（随后恢复，避免影响其它用例）
        JsonNode users = dataOf(call("get", "/api/admin/users?keyword=station_admin_01", admin, null));
        long stationAdminId = users.path("records").get(0).path("id").asLong();
        assertEquals(200, httpStatus(call("put", "/api/admin/users/" + stationAdminId + "/roles", admin,
                Map.of("roleCodes", new String[]{"USER"}))));
        assertEquals(401, httpStatus(call("get", "/api/appointment/admin/page?current=1&size=5", station, null)));
        assertEquals(200, httpStatus(call("put", "/api/admin/users/" + stationAdminId + "/roles", admin,
                Map.of("roleCodes", new String[]{"STATION_ADMIN"}))));
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

    /** 找一个当前用户还没评价过的驿站；都被评价过时退化为第一个驿站（测试会因 400 明确失败）。 */
    private long firstUnratedStationId(String userToken) throws Exception {
        Set<Long> rated = new HashSet<>();
        for (JsonNode item : dataOf(call("get", "/api/evaluation/my", userToken, null))) {
            rated.add(item.path("stationId").asLong());
        }
        JsonNode page = dataOf(call("get", "/api/station/page?current=1&size=50", null, null));
        for (JsonNode item : page.path("records")) {
            long id = item.path("id").asLong();
            if (id > 0 && !rated.contains(id)) {
                return id;
            }
        }
        return firstStationId();
    }
}
