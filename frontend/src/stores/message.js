import { defineStore } from "pinia";

/**
 * 消息中心与顶栏未读角标的**唯一数据源**。
 *
 * 背景（修复的缺陷）：消息列表和已读状态原先只存在于 ModuleView 内部，
 * 而顶栏角标在 DashboardView 里按「待处理预约 + 待处理诉求」另算一套，
 * 两者毫无关联——所以用户点了「全部已读」，角标数字依然挂着。
 * 现在两处都读这个 store，已读状态持久化到 localStorage。
 *
 * 消息由预约、诉求、报名记录派生（系统没有独立的消息表），
 * 因此任何一处拿到新数据后调用 `sync()` 刷新即可。
 */

const READ_KEY = "friendly_station_read_messages";

/** 已读键只增不减，读取失败时按「全部未读」处理，避免误标为已读。 */
function loadReadKeys() {
  try {
    const raw = JSON.parse(localStorage.getItem(READ_KEY) || "[]");
    return Array.isArray(raw) ? raw.filter((item) => typeof item === "string") : [];
  } catch {
    return [];
  }
}

export const useMessageStore = defineStore("message", {
  state: () => ({
    /** 派生出的消息列表，由页面调用 sync() 写入。 */
    items: [],
    /** 已读消息的 key 列表（持久化）。 */
    readKeys: loadReadKeys(),
    /** 是否已经同步过数据，用于区分「没有消息」与「还没加载」。 */
    synced: false
  }),
  getters: {
    /** 未读条数——顶栏角标与消息中心共用。 */
    unreadCount(state) {
      return state.items.filter((item) => !state.readKeys.includes(item.key)).length;
    },
    /** 消息是否已读。 */
    isRead(state) {
      return (key) => state.readKeys.includes(key);
    }
  },
  actions: {
    /**
     * 用当前登录用户的预约、诉求、报名记录重建消息列表。
     * 各页面拿到数据后调用即可，未传的项按空数组处理。
     */
    sync({ appointments = [], appeals = [], signupIds = [] } = {}) {
      const items = [];
      (appointments || []).slice(0, 4).forEach((item) => {
        items.push({
          key: `appointment-${item.id}-${item.status}`,
          title: `预约${item.statusLabel || "已提交"}：${item.serviceName || "服务预约"}`,
          detail: `${item.stationName || "驿站"} · ${item.appointDate || ""} ${item.appointTime || ""}`.trim(),
          time: item.createTime || item.appointDate || "",
          type: item.status === "CONFIRMED" ? "success" : item.status === "REJECTED" ? "warning" : "primary"
        });
      });
      (appeals || []).slice(0, 4).forEach((item) => {
        items.push({
          key: `appeal-${item.id}-${item.status}`,
          title: `诉求${item.statusLabel || "已提交"}：${item.title || ""}`,
          detail: item.handlerName ? `处理人：${item.handlerName}` : (item.receivingBoundary || "等待受理"),
          time: item.createTime || "",
          type: item.status === "RESOLVED" ? "success" : "info"
        });
      });
      (signupIds || []).slice(0, 4).forEach((id) => {
        items.push({
          key: `signup-${id}`,
          title: "活动报名已提交",
          detail: "报名成功后可领取活动物资",
          time: "最近",
          type: "primary"
        });
      });
      this.items = items;
      this.synced = true;
      this.pruneReadKeys();
    },
    /** 清理已不在列表中的已读键，避免 localStorage 无限增长。 */
    pruneReadKeys() {
      const alive = new Set(this.items.map((item) => item.key));
      const next = this.readKeys.filter((key) => alive.has(key));
      if (next.length !== this.readKeys.length) {
        this.readKeys = next;
        this.persist();
      }
    },
    markRead(key) {
      if (!key || this.readKeys.includes(key)) return;
      this.readKeys = [...this.readKeys, key];
      this.persist();
    },
    markAllRead() {
      const merged = new Set([...this.readKeys, ...this.items.map((item) => item.key)]);
      this.readKeys = [...merged];
      this.persist();
    },
    persist() {
      try {
        localStorage.setItem(READ_KEY, JSON.stringify(this.readKeys));
      } catch {
        // 隐私模式下 localStorage 可能不可写；此时仅内存生效，不阻断界面。
      }
    }
  }
});
