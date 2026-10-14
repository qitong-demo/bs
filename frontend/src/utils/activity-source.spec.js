import { describe, expect, it } from "vitest";
import {
  activitySignupHint,
  activitySourceLabel,
  externalUrlText,
  isExternalActivity,
  canSignupInSite
} from "./activity-source";

/** 采集来的第三方活动：有原文链接、不接受站内报名。 */
const external = {
  organizerType: "EXTERNAL",
  organizerLabel: "第三方机构",
  sourceLevelLabel: "政府群团",
  externalUrl: "https://www.hzgh.org/newsview107120.htm"
};

/** 驿站自办活动：站内报名，没有原文链接与主办机构。 */
const station = {
  organizerType: "STATION",
  organizerLabel: "驿站",
  sourceLevelLabel: "驿站自办",
  externalUrl: ""
};

describe("activity-source 活动来源口径", () => {
  it("只有 EXTERNAL 算采集活动，缺失字段按站内活动处理", () => {
    expect(isExternalActivity(external)).toBe(true);
    expect(isExternalActivity(station)).toBe(false);
    expect(isExternalActivity({ organizerType: " external " })).toBe(true);
    expect(isExternalActivity({ organizerType: "PLATFORM" })).toBe(false);
    expect(isExternalActivity({})).toBe(false);
    expect(isExternalActivity(null)).toBe(false);
  });

  it("来源标签取后端 label，兜底为驿站/第三方机构", () => {
    expect(activitySourceLabel(external)).toBe("第三方机构");
    expect(activitySourceLabel(station)).toBe("驿站");
    expect(activitySourceLabel({ organizerType: "EXTERNAL" })).toBe("第三方机构");
    expect(activitySourceLabel({ organizerType: "STATION" })).toBe("驿站");
  });

  it("原文链接去空白，缺失时为空串", () => {
    expect(externalUrlText(external)).toBe("https://www.hzgh.org/newsview107120.htm");
    expect(externalUrlText({ externalUrl: "  https://example.org/a  " })).toBe("https://example.org/a");
    expect(externalUrlText({ externalUrl: "" })).toBe("");
    expect(externalUrlText({})).toBe("");
  });

  it("采集活动的报名提示说清主办方负责，缺链接时说明无法跳转", () => {
    expect(activitySignupHint(external)).toBe("报名与审核由主办方负责，本站仅提供信息");
    expect(activitySignupHint({ organizerType: "EXTERNAL", externalUrl: "" })).toBe(
      "该活动未提供原文链接，暂无法跳转，可联系平台管理员核对"
    );
    // 站内活动不需要这句提示：报名就在本站完成
    expect(activitySignupHint(station)).toBe("");
  });
});

describe("canSignupInSite", () => {
  it("后端给出 acceptsSignup=false 时不允许站内报名", () => {
    expect(canSignupInSite({ acceptsSignup: false, organizerType: "EXTERNAL" })).toBe(false);
  });

  it("后端给出 acceptsSignup=true 时允许站内报名", () => {
    expect(canSignupInSite({ acceptsSignup: true, organizerType: "STATION" })).toBe(true);
  });

  it("字段缺失时回退到 organizerType：第三方活动不允许报名", () => {
    // 这个兜底不是多余的——该字段曾经只有 mysql 实现输出、内存实现漏了，
    // 前端若直接依赖它，会在演示 profile 下静默藏掉所有报名按钮。
    expect(canSignupInSite({ organizerType: "EXTERNAL" })).toBe(false);
    expect(canSignupInSite({ organizerType: "STATION" })).toBe(true);
  });

  it("以后端字段为准，即使它与 organizerType 不一致", () => {
    // 将来若出现「平台自办但不收报名」或「第三方但允许站内报名」，
    // 应以服务端强制校验所依据的 acceptsSignup 为准，而不是分类标签。
    expect(canSignupInSite({ acceptsSignup: false, organizerType: "STATION" })).toBe(false);
    expect(canSignupInSite({ acceptsSignup: true, organizerType: "EXTERNAL" })).toBe(true);
  });

  it("空值不抛异常", () => {
    expect(canSignupInSite(null)).toBe(true);
    expect(canSignupInSite(undefined)).toBe(true);
  });
});
