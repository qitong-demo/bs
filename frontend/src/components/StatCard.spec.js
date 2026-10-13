import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import StatCard from "./StatCard.vue";

function mountCard(item) {
  return mount(StatCard, {
    props: { item },
    global: { stubs: { "el-icon": true, OfficeBuilding: true } }
  });
}

describe("StatCard", () => {
  it("渲染指标名称、数值、单位与说明", () => {
    const wrapper = mountCard({
      label: "驿站点位（在库）",
      value: "5",
      unit: "个",
      note: "营业中 4 个",
      icon: "OfficeBuilding",
      tone: "green"
    });

    expect(wrapper.text()).toContain("驿站点位（在库）");
    expect(wrapper.text()).toContain("5");
    expect(wrapper.text()).toContain("个");
    expect(wrapper.text()).toContain("营业中 4 个");
  });

  it("按 tone 生成对应的图标配色类名", () => {
    const wrapper = mountCard({ label: "预约", value: "2", unit: "条", note: "", icon: "Calendar", tone: "blue" });
    expect(wrapper.find(".stat-icon--blue").exists()).toBe(true);
    expect(wrapper.find(".stat-card").exists()).toBe(true);
  });

  it("值为占位符时也能正常渲染", () => {
    const wrapper = mountCard({ label: "诉求", value: "—", unit: "件", note: "", icon: "Document", tone: "orange" });
    expect(wrapper.text()).toContain("—");
  });
});
