import { beforeEach, describe, expect, it, vi } from "vitest";
import { errorMessage, http, request, safeRequest } from "./http";

beforeEach(() => {
  vi.restoreAllMocks();
});

describe("request 统一解包", () => {
  it("业务码为 200 时返回 data", async () => {
    vi.spyOn(http, "request").mockResolvedValue({ data: { code: 200, message: "success", data: { id: 1 } } });
    await expect(request({ url: "/x" })).resolves.toEqual({ id: 1 });
  });

  it("业务码非 200 时抛出后端中文消息", async () => {
    vi.spyOn(http, "request").mockResolvedValue({ data: { code: 400, message: "驿站不存在", data: null } });
    await expect(request({ url: "/x" })).rejects.toThrow("驿站不存在");
  });

  it("响应不是 ApiResponse 结构时原样返回", async () => {
    vi.spyOn(http, "request").mockResolvedValue({ data: [1, 2, 3] });
    await expect(request({ url: "/x" })).resolves.toEqual([1, 2, 3]);
  });
});

describe("safeRequest 容错", () => {
  it("失败时返回兜底值而不抛出", async () => {
    vi.spyOn(http, "request").mockRejectedValue(new Error("boom"));
    await expect(safeRequest({ url: "/x" }, [])).resolves.toEqual([]);
  });

  it("成功时正常返回数据", async () => {
    vi.spyOn(http, "request").mockResolvedValue({ data: { code: 200, data: { ok: true } } });
    await expect(safeRequest({ url: "/x" }, null)).resolves.toEqual({ ok: true });
  });
});

describe("errorMessage 文案优先级", () => {
  it("优先使用 userMessage", () => {
    expect(errorMessage({ userMessage: "自定义提示" }, "兜底")).toBe("自定义提示");
  });

  it("回退到后端响应消息", () => {
    expect(errorMessage({ response: { data: { message: "后端消息" } } }, "兜底")).toBe("后端消息");
  });

  it("再回退到兜底文案", () => {
    expect(errorMessage({}, "兜底")).toBe("兜底");
  });
});
