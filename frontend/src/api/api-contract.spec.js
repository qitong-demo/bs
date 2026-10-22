import { beforeEach, describe, expect, it, vi } from "vitest";

const { request, downloadCsv, httpStub } = vi.hoisted(() => ({
  request: vi.fn(),
  downloadCsv: vi.fn(),
  httpStub: { get: vi.fn(), post: vi.fn() }
}));

vi.mock("./http", () => ({
  request,
  downloadCsv,
  http: httpStub,
  errorMessage: (error, fallback) => error?.userMessage || fallback
}));

import * as authApi from "./auth";
import * as cityApi from "./city";
import * as stationApi from "./station";
import * as workflowApi from "./workflow";
import * as contentApi from "./content";
import * as evaluationApi from "./evaluation";
import * as adminApi from "./admin";
import * as statApi from "./stat";

beforeEach(() => {
  request.mockReset();
  downloadCsv.mockReset();
  request.mockResolvedValue({ ok: true });
});

describe("认证接口契约", () => {
  it("登录命中 POST /auth/login 并返回会话数据", async () => {
    request.mockResolvedValue({ token: "t", role: "USER" });
    const session = await authApi.login({ username: "a", password: "b" });
    expect(request).toHaveBeenCalledWith({ url: "/auth/login", method: "post", data: { username: "a", password: "b" } });
    expect(session.token).toBe("t");
  });

  it("登录未返回 token 时给出可读错误", async () => {
    request.mockResolvedValue({ role: "USER" });
    await expect(authApi.login({ username: "a", password: "b" })).rejects.toThrow("登录接口未返回有效凭证");
  });

  it("个人资料读写命中 /user/info", async () => {
    await authApi.currentUser();
    expect(request).toHaveBeenCalledWith({ url: "/user/info" });
    await authApi.updateProfile({ realName: "小王" });
    expect(request).toHaveBeenCalledWith({ url: "/user/info", method: "put", data: { realName: "小王" } });
  });
});

describe("驿站接口契约", () => {
  it("公开分页默认 current=1 size=20", async () => {
    await stationApi.getStationPage();
    expect(request).toHaveBeenCalledWith({ url: "/station/page", params: { current: 1, size: 20 } });
  });

  it("公开分页可覆盖查询参数", async () => {
    await stationApi.getStationPage({ current: 2, size: 5, area: "西湖区", keyword: "充电" });
    expect(request).toHaveBeenCalledWith({
      url: "/station/page",
      params: { current: 2, size: 5, area: "西湖区", keyword: "充电" }
    });
  });

  it("公开分页支持按城市过滤", async () => {
    await stationApi.getStationPage({ current: 1, size: 50, cityCode: "110100" });
    expect(request).toHaveBeenCalledWith({
      url: "/station/page",
      params: { current: 1, size: 50, cityCode: "110100" }
    });
  });

  it("区域分布支持按城市统计", async () => {
    await stationApi.getStationAreas({ cityCode: "110100" });
    expect(request).toHaveBeenCalledWith({ url: "/station/areas", params: { cityCode: "110100" } });
  });

  it("管理端分页默认 size=10", async () => {
    await stationApi.getAdminStationPage();
    expect(request).toHaveBeenCalledWith({ url: "/station/admin/page", params: { current: 1, size: 10 } });
  });

  it("详情与增删改命中正确的方法与路径", async () => {
    await stationApi.getStationDetail(7);
    expect(request).toHaveBeenLastCalledWith({ url: "/station/7" });

    await stationApi.createStation({ stationName: "驿站" });
    expect(request).toHaveBeenLastCalledWith({ url: "/station", method: "post", data: { stationName: "驿站" } });

    await stationApi.updateStation(7, { stationName: "新" });
    expect(request).toHaveBeenLastCalledWith({ url: "/station/7", method: "put", data: { stationName: "新" } });

    await stationApi.updateStationStatus(7, "CLOSED");
    expect(request).toHaveBeenLastCalledWith({ url: "/station/7/status", method: "put", params: { status: "CLOSED" } });

    await stationApi.deleteStation(7);
    expect(request).toHaveBeenLastCalledWith({ url: "/station/7", method: "delete" });
  });

  it("服务项目接口路径正确", async () => {
    await stationApi.getStationServicePage({ stationId: 3 });
    expect(request).toHaveBeenLastCalledWith({
      url: "/station/service/admin/page",
      params: { current: 1, size: 10, stationId: 3 }
    });

    await stationApi.listStationServices(3);
    expect(request).toHaveBeenLastCalledWith({ url: "/station/service/list", params: { stationId: 3 } });

    await stationApi.createStationService({ stationId: 3, serviceName: "充电" });
    expect(request).toHaveBeenLastCalledWith({
      url: "/station/service",
      method: "post",
      data: { stationId: 3, serviceName: "充电" }
    });

    await stationApi.updateStationService(9, { serviceName: "热饭" });
    expect(request).toHaveBeenLastCalledWith({ url: "/station/service/9", method: "put", data: { serviceName: "热饭" } });

    await stationApi.updateStationServiceStatus(9, 0);
    expect(request).toHaveBeenLastCalledWith({ url: "/station/service/9/status", method: "put", params: { status: 0 } });

    await stationApi.deleteStationService(9);
    expect(request).toHaveBeenLastCalledWith({ url: "/station/service/9", method: "delete" });
  });
});

describe("预约与诉求接口契约", () => {
  it("预约相关路径正确", async () => {
    // 预约事项已由「选服务项目」改为用户自填 serviceName，并新增可选 contactPhone
    await workflowApi.createAppointment({ stationId: 1, serviceName: "想借用场地开小组会", contactPhone: "13800002088" });
    expect(request).toHaveBeenLastCalledWith({
      url: "/appointment",
      method: "post",
      data: { stationId: 1, serviceName: "想借用场地开小组会", contactPhone: "13800002088" }
    });

    await workflowApi.getMyAppointments();
    expect(request).toHaveBeenLastCalledWith({ url: "/appointment/my" });

    await workflowApi.cancelAppointment(11);
    expect(request).toHaveBeenLastCalledWith({ url: "/appointment/11/cancel", method: "put" });

    await workflowApi.getAdminAppointments({ status: "CONFIRMED" });
    expect(request).toHaveBeenLastCalledWith({
      url: "/appointment/admin/page",
      params: { current: 1, size: 10, status: "CONFIRMED" }
    });

    await workflowApi.auditAppointment({ id: 11, status: "REJECTED", reason: "名额冲突" });
    expect(request).toHaveBeenLastCalledWith({
      url: "/appointment/audit",
      method: "put",
      data: { id: 11, status: "REJECTED", reason: "名额冲突" }
    });
  });

  it("诉求相关路径正确", async () => {
    await workflowApi.createAppeal({ title: "问题" });
    expect(request).toHaveBeenLastCalledWith({ url: "/appeal", method: "post", data: { title: "问题" } });

    await workflowApi.getMyAppeals();
    expect(request).toHaveBeenLastCalledWith({ url: "/appeal/my" });

    await workflowApi.getAppealDetail(5);
    expect(request).toHaveBeenLastCalledWith({ url: "/appeal/5" });

    await workflowApi.getAppealRecords(5);
    expect(request).toHaveBeenLastCalledWith({ url: "/appeal/5/records" });

    await workflowApi.assignAppeal({ id: 5 });
    expect(request).toHaveBeenLastCalledWith({ url: "/appeal/assign", method: "put", data: { id: 5 } });

    await workflowApi.handleAppeal({ id: 5, status: "RESOLVED", content: "已处理" });
    expect(request).toHaveBeenLastCalledWith({
      url: "/appeal/handle",
      method: "put",
      data: { id: 5, status: "RESOLVED", content: "已处理" }
    });

    await workflowApi.evaluateAppeal({ appealId: 5, score: 5 });
    expect(request).toHaveBeenLastCalledWith({ url: "/appeal/evaluate", method: "post", data: { appealId: 5, score: 5 } });
  });

  it("活动报名路径正确", async () => {
    // 报名信息必填姓名与电话，承办方要靠这些联系报名人；备注选填
    await workflowApi.signupActivity({ activityId: 2, contactName: "王骑手", phone: "13800002088", remark: "周六上午方便" });
    expect(request).toHaveBeenLastCalledWith({
      url: "/activity/signup",
      method: "post",
      data: { activityId: 2, contactName: "王骑手", phone: "13800002088", remark: "周六上午方便" }
    });

    await workflowApi.getMySignups();
    expect(request).toHaveBeenLastCalledWith({ url: "/activity/my" });

    await workflowApi.getAdminSignups();
    expect(request).toHaveBeenLastCalledWith({ url: "/activity/admin/signups" });
  });

  it("承办方处理报名命中 /activity/signup/review", async () => {
    await workflowApi.reviewSignup({ id: 5, status: "APPROVED", reviewNote: "已电话确认" });
    expect(request).toHaveBeenLastCalledWith({
      url: "/activity/signup/review",
      method: "put",
      data: { id: 5, status: "APPROVED", reviewNote: "已电话确认" }
    });
  });
});

describe("诉求分类与反馈对象契约", () => {
  it("提交诉求带 category 与 receiverTarget", async () => {
    await workflowApi.createAppeal({
      category: "PRAISE",
      receiverTarget: "STATION",
      title: "感谢湖滨驿站帮我处理伤口",
      type: "热心帮助"
    });
    expect(request).toHaveBeenLastCalledWith({
      url: "/appeal",
      method: "post",
      data: {
        category: "PRAISE",
        receiverTarget: "STATION",
        title: "感谢湖滨驿站帮我处理伤口",
        type: "热心帮助"
      }
    });
  });
});

describe("活动承办方契约", () => {
  it("创建活动带 organizerType，第三方活动带 externalUrl", async () => {
    await contentApi.createActivity({ title: "驿站理发志愿服务", organizerType: "STATION", stationId: 1 });
    expect(request).toHaveBeenLastCalledWith({
      url: "/activity/admin",
      method: "post",
      data: { title: "驿站理发志愿服务", organizerType: "STATION", stationId: 1 }
    });

    await contentApi.createActivity({
      title: "市总工会技能大赛",
      organizerType: "EXTERNAL",
      externalUrl: "https://example.org/race"
    });
    expect(request).toHaveBeenLastCalledWith({
      url: "/activity/admin",
      method: "post",
      data: { title: "市总工会技能大赛", organizerType: "EXTERNAL", externalUrl: "https://example.org/race" }
    });
  });
});

describe("资讯、公告与活动接口契约", () => {
  it("资讯前台默认 size=10，后台带 status", async () => {
    await contentApi.getNewsPage();
    expect(request).toHaveBeenLastCalledWith({ url: "/news/page", params: { current: 1, size: 10 } });

    // 分城市与来源筛选：cityCode 决定「本地优先 + 全国可见」，sourceType 区分官方/平台
    await contentApi.getNewsPage({ cityCode: "330100", sourceType: "OFFICIAL", platform: "美团" });
    expect(request).toHaveBeenLastCalledWith({
      url: "/news/page",
      params: { current: 1, size: 10, cityCode: "330100", sourceType: "OFFICIAL", platform: "美团" }
    });

    await contentApi.getAdminNewsPage({ status: "DRAFT" });
    expect(request).toHaveBeenLastCalledWith({ url: "/news/admin/page", params: { current: 1, size: 10, status: "DRAFT" } });

    await contentApi.updateNews(4, { title: "改" });
    expect(request).toHaveBeenLastCalledWith({ url: "/news/4", method: "put", data: { title: "改" } });

    await contentApi.deleteNews(4);
    expect(request).toHaveBeenLastCalledWith({ url: "/news/4", method: "delete" });
  });

  it("公告使用 noticeType 查询参数", async () => {
    await contentApi.getNoticePage({ noticeType: "通知" });
    expect(request).toHaveBeenLastCalledWith({ url: "/notice/page", params: { current: 1, size: 10, noticeType: "通知" } });

    await contentApi.createNotice({ title: "公告" });
    expect(request).toHaveBeenLastCalledWith({ url: "/notice", method: "post", data: { title: "公告" } });
  });

  it("活动前台默认 size=12，管理端在 /activity/admin", async () => {
    await contentApi.getActivityPage();
    expect(request).toHaveBeenLastCalledWith({ url: "/activity/page", params: { current: 1, size: 12 } });

    await contentApi.getAdminActivityPage();
    expect(request).toHaveBeenLastCalledWith({ url: "/activity/admin/page", params: { current: 1, size: 10 } });

    await contentApi.createActivity({ title: "活动" });
    expect(request).toHaveBeenLastCalledWith({ url: "/activity/admin", method: "post", data: { title: "活动" } });

    await contentApi.updateActivity(6, { title: "改" });
    expect(request).toHaveBeenLastCalledWith({ url: "/activity/admin/6", method: "put", data: { title: "改" } });

    await contentApi.deleteActivity(6);
    expect(request).toHaveBeenLastCalledWith({ url: "/activity/admin/6", method: "delete" });
  });
});

describe("评价与账号接口契约", () => {
  it("评价接口路径正确", async () => {
    await evaluationApi.createStationEvaluation({ stationId: 1, score: 5 });
    expect(request).toHaveBeenLastCalledWith({
      url: "/evaluation/station",
      method: "post",
      data: { stationId: 1, score: 5 }
    });

    await evaluationApi.getStationEvaluationPage({ stationId: 1 });
    expect(request).toHaveBeenLastCalledWith({
      url: "/evaluation/station/page",
      params: { current: 1, size: 10, stationId: 1 }
    });

    await evaluationApi.getStationEvaluationSummary(1);
    expect(request).toHaveBeenLastCalledWith({ url: "/evaluation/station/summary", params: { stationId: 1 } });

    await evaluationApi.getMyEvaluations();
    expect(request).toHaveBeenLastCalledWith({ url: "/evaluation/my" });
  });

  it("账号与角色接口路径正确", async () => {
    await adminApi.getAdminOverview();
    expect(request).toHaveBeenLastCalledWith({ url: "/admin/overview" });

    await adminApi.getAdminUsers({ role: "USER" });
    expect(request).toHaveBeenLastCalledWith({ url: "/admin/users", params: { current: 1, size: 10, role: "USER" } });

    await adminApi.updateAdminUserStatus(2, "停用");
    expect(request).toHaveBeenLastCalledWith({
      url: "/admin/users/2/status",
      method: "put",
      data: { status: "停用" }
    });

    await adminApi.updateAdminUserRoles(2, ["STATION_ADMIN"]);
    expect(request).toHaveBeenLastCalledWith({
      url: "/admin/users/2/roles",
      method: "put",
      data: { roleCodes: ["STATION_ADMIN"] }
    });

    await adminApi.getRoles();
    expect(request).toHaveBeenLastCalledWith({ url: "/admin/roles" });
  });
});

describe("统计与导出接口契约", () => {  it("统计接口路径正确", async () => {
    await statApi.getStatOverview();
    expect(request).toHaveBeenLastCalledWith({ url: "/stat/overview" });

    await statApi.getMonthlyTrend();
    expect(request).toHaveBeenLastCalledWith({ url: "/stat/appeal/month" });

    await statApi.getServiceRanking();
    expect(request).toHaveBeenLastCalledWith({ url: "/stat/service/ranking" });
  });

  it("导出报表委托给下载工具并带上类型与文件名", async () => {
    await statApi.exportReport("appeals");
    expect(downloadCsv).toHaveBeenCalledWith("appeals", "appeals.csv");
  });
});

describe("城市接口契约（多城市平台）", () => {
  it("城市列表命中 /city/list", async () => {
    await cityApi.getCityList();
    expect(request).toHaveBeenCalledWith({ url: "/city/list" });
  });

  it("按坐标定位命中 /city/nearest 并带上 lng/lat", async () => {
    await cityApi.getNearestCity(116.4074, 39.9042);
    expect(request).toHaveBeenCalledWith({ url: "/city/nearest", params: { lng: 116.4074, lat: 39.9042 } });
  });

  it("定位用户所在城市命中 /city/locate 并带上 lng/lat", async () => {
    await cityApi.locateCity(120.1551, 30.2741);
    expect(request).toHaveBeenCalledWith({ url: "/city/locate", params: { lng: 120.1551, lat: 30.2741 } });
  });
});
