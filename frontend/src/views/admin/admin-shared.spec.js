import { describe, expect, it } from "vitest";
import {
  NEWS_CATEGORIES,
  NOTICE_TARGETS,
  NOTICE_TYPES,
  PLATFORM_ROLES,
  ROLE_OPTIONS,
  ROLE_SCOPE,
  appealStatusLabel,
  appealStatusTag,
  appointmentStatusLabel,
  appointmentStatusTag,
  contentStatusLabel,
  contentStatusTag,
  numberText,
  stationStatusLabel,
  stationStatusTag,
  textOf,
  userStatusLabel,
  userStatusTag
} from "./admin-shared";

describe("管理端公共常量", () => {
  it("平台角色只包含管理员（标签可见性已改由权限接口驱动，此处仅作兜底）", () => {
    expect(PLATFORM_ROLES).toEqual(["PLATFORM_ADMIN"]);
  });

  it("内置角色选项与后端 RoleCatalog 的三类角色一致", () => {
    expect(ROLE_OPTIONS.map((item) => item.value)).toEqual([
      "USER",
      "STATION_ADMIN",
      "PLATFORM_ADMIN"
    ]);
    // 角色收敛为三个身份：原 SUPER_ADMIN 只是定义了角色行、从未分配给任何账号，已删除
    expect(Object.keys(ROLE_SCOPE)).toHaveLength(3);
  });

  it("资讯与公告选项非空", () => {
    expect(NEWS_CATEGORIES.length).toBeGreaterThan(0);
    expect(NOTICE_TYPES.length).toBeGreaterThan(0);
    expect(NOTICE_TARGETS.some((item) => item.value === "ALL")).toBe(true);
  });
});

describe("展示工具", () => {
  it("空值统一显示为占位符，不做推算", () => {
    expect(textOf(null)).toBe("—");
    expect(textOf("  ")).toBe("—");
    expect(textOf("西湖区")).toBe("西湖区");
    expect(numberText(null)).toBe("—");
    expect(numberText(0)).toBe("0");
    expect(numberText("12")).toBe("12");
  });

  it("预约状态优先使用后端 statusLabel", () => {
    expect(appointmentStatusLabel({ status: "CONFIRMED", statusLabel: "已确认" })).toBe("已确认");
    expect(appointmentStatusLabel({ status: "REJECTED" })).toBe("已拒绝");
    expect(appointmentStatusLabel(null)).toBe("未知");
    expect(appointmentStatusTag("CONFIRMED")).toBe("success");
    expect(appointmentStatusTag("REJECTED")).toBe("danger");
    expect(appointmentStatusTag("PENDING_STATION_CONFIRMATION")).toBe("warning");
  });

  it("诉求状态优先使用后端 statusLabel", () => {
    expect(appealStatusLabel({ status: "RESOLVED", statusLabel: "已办结" })).toBe("已办结");
    expect(appealStatusLabel({ status: "ASSIGNED" })).toBe("已分派");
    expect(appealStatusTag("RESOLVED")).toBe("success");
    expect(appealStatusTag("ASSIGNED")).toBe("warning");
  });

  it("驿站与内容状态映射正确", () => {
    expect(stationStatusLabel("OPEN")).toBe("营业中");
    expect(stationStatusLabel("CLOSED")).toBe("已停用");
    expect(stationStatusLabel("DELETED")).toBe("已删除");
    expect(stationStatusTag("OPEN")).toBe("success");
    expect(stationStatusTag("DELETED")).toBe("danger");
    expect(contentStatusLabel({ status: "PUBLISHED" })).toBe("已发布");
    expect(contentStatusLabel({ status: "DRAFT" })).toBe("草稿");
    expect(contentStatusTag("PUBLISHED")).toBe("success");
    expect(contentStatusTag("DRAFT")).toBe("info");
  });

  it("账号状态按后端的 1/0 判断", () => {
    expect(userStatusTag(1)).toBe("success");
    expect(userStatusTag(0)).toBe("danger");
    expect(userStatusLabel({ status: 1, statusLabel: "正常" })).toBe("正常");
    expect(userStatusLabel({ status: 0 })).toBe("停用");
    expect(userStatusLabel(null)).toBe("未知");
  });
});
