import { describe, expect, it } from "vitest";
import { isAllDay, openState, openStateMeta, parseBusinessHours } from "./business-hours";

/** 构造一个当天指定时刻的 Date，避免测试依赖真实运行时间。 */
function at(hour, minute = 0) {
  const date = new Date(2026, 9, 14, hour, minute, 0, 0);
  return date;
}

describe("business-hours 营业时间解析", () => {
  it("解析单个区间并兼容全角冒号、中文破折号", () => {
    expect(parseBusinessHours("每日 8:30-22:00")).toEqual([{ start: 510, end: 1320 }]);
    expect(parseBusinessHours("8：30—17：30")).toEqual([{ start: 510, end: 1050 }]);
    expect(parseBusinessHours("09:00~18:00")).toEqual([{ start: 540, end: 1080 }]);
  });

  it("解析多个区间（午休分开）", () => {
    expect(parseBusinessHours("09:00-12:00 14:00-18:00")).toEqual([
      { start: 540, end: 720 },
      { start: 840, end: 1080 }
    ]);
  });

  it("识别全天开放", () => {
    expect(isAllDay("24小时营业")).toBe(true);
    expect(isAllDay("24 小时开放")).toBe(true);
    expect(isAllDay("全天")).toBe(true);
    expect(isAllDay("09:00-18:00")).toBe(false);
  });

  it("无法解析时返回空数组", () => {
    expect(parseBusinessHours("开放时间待核验")).toEqual([]);
    expect(parseBusinessHours("")).toEqual([]);
    expect(parseBusinessHours(null)).toEqual([]);
    expect(parseBusinessHours("08:30——17:30")).toEqual([]);
  });
});

describe("business-hours 实时营业状态", () => {
  it("营业时间内为 OPEN", () => {
    expect(openState("10:00-22:00", at(14, 0))).toBe("OPEN");
    expect(openState("08:30-17:30", at(8, 30))).toBe("OPEN");
  });

  it("时间已过为 CLOSED（修复『已打烊仍显示营业中』）", () => {
    expect(openState("10:00-22:00", at(23, 0))).toBe("CLOSED");
    expect(openState("08:30-17:30", at(20, 0))).toBe("CLOSED");
    expect(openState("08:30-17:30", at(7, 0))).toBe("CLOSED");
  });

  it("午休时段为 CLOSED", () => {
    expect(openState("09:00-12:00 14:00-18:00", at(13, 0))).toBe("CLOSED");
    expect(openState("09:00-12:00 14:00-18:00", at(15, 0))).toBe("OPEN");
  });

  it("全天开放任意时刻都是 OPEN", () => {
    expect(openState("24小时营业", at(3, 0))).toBe("OPEN");
    expect(openState("24小时开放", at(23, 59))).toBe("OPEN");
  });

  it("营业时间缺失或无法解析时为 UNKNOWN，而不是谎称营业中", () => {
    expect(openState("", at(14, 0))).toBe("UNKNOWN");
    expect(openState(null, at(14, 0))).toBe("UNKNOWN");
    expect(openState("开放时间待核验", at(14, 0))).toBe("UNKNOWN");
    expect(openState("08:30——17:30", at(14, 0))).toBe("UNKNOWN");
  });
});

describe("business-hours 状态文案", () => {
  it("按状态给出中文文案与标签色", () => {
    expect(openStateMeta("10:00-22:00", at(14, 0))).toMatchObject({ label: "营业中", tag: "success" });
    expect(openStateMeta("10:00-22:00", at(23, 0))).toMatchObject({ label: "已打烊", tag: "warning" });
    expect(openStateMeta("", at(14, 0))).toMatchObject({ label: "营业时间待核验", tag: "info" });
  });

  it("驿站被停用时优先显示已停用", () => {
    expect(openStateMeta("10:00-22:00", at(14, 0), "CLOSED")).toMatchObject({ label: "已停用", tag: "info" });
  });
});
