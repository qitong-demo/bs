import { defineStore } from "pinia";
import {
  getNotifications,
  getUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead
} from "../api/notification";

/**
 * 消息中心与顶栏未读角标的唯一数据源。
 *
 * 优先使用后端 `notification` 表（预约审核、报名审核、诉求处理、驿站暂停），
 * 接口不可用时退回按业务记录派生的本地消息，保证演示环境不会整块空白。
 */

const READ_KEY = "friendly_station_read_messages";

/** 轮询计时器放在 store 外，避免被 Pinia 做成响应式对象。 */
let pollTimer = null;

function loadReadKeys() {
  try {
    const raw = JSON.parse(localStorage.getItem(READ_KEY) || "[]");
    return Array.isArray(raw) ? raw.filter((item) => typeof item === "string") : [];
  } catch {
    return [];
  }
}

const TYPE_TAG = {
  APPOINTMENT: "primary",
  SIGNUP: "warning",
  APPEAL: "info",
  SYSTEM: "danger"
};

function mapNotification(item) {
  return {
    key: `notification-${item.id}`,
    serverId: item.id,
    title: item.title || "系统消息",
    detail: item.content || "",
    time: item.createTime || "",
    type: TYPE_TAG[item.type] || "info",
    read: Boolean(item.read),
    link: item.link || ""
  };
}

export const useMessageStore = defineStore("message", {
  state: () => ({
    items: [],
    /** 旧版派生消息的已读 key；服务端消息用 item.read。 */
    readKeys: loadReadKeys(),
    synced: false,
    /** 是否已成功从后端取到通知。 */
    serverMode: false,
    /** 服务端未读总数（可能大于当前已加载条数）。 */
    unreadTotal: 0,
    /** 服务端通知分页状态。 */
    pageSize: 20,
    currentPage: 1,
    total: 0,
    hasMore: false,
    loadingMore: false
  }),
  getters: {
    unreadCount(state) {
      if (state.serverMode) return state.unreadTotal;
      return state.items.filter((item) => !state.readKeys.includes(item.key)).length;
    },
    isRead(state) {
      return (key) => {
        const item = state.items.find((row) => row.key === key);
        if (item && typeof item.read === "boolean") return item.read;
        return state.readKeys.includes(key);
      };
    }
  },
  actions: {
    /**
     * 页面拿到业务数据后调用：先尝试后端通知，失败再退回派生消息。
     * 调用方不需要 await，store 会响应式更新。
     */
    async sync(payload = {}) {
      try {
        await this.refresh();
        return;
      } catch {
        this.buildDerived(payload);
      }
    },
    async refresh() {
      const [page, count] = await Promise.all([
        getNotifications({ current: 1, size: this.pageSize }),
        getUnreadNotificationCount()
      ]);
      const records = page?.records || [];
      this.items = records.map(mapNotification);
      this.total = Number(page?.total) || 0;
      this.currentPage = 1;
      this.hasMore = this.items.length < this.total;
      this.unreadTotal = Number(count?.count) || 0;
      this.serverMode = true;
      this.synced = true;
    },
    /** 出错时保持现有消息不动，用于业务动作后的静默刷新。 */
    async refreshQuietly() {
      try {
        await this.refresh();
      } catch {
        // 保持已有列表，避免一次网络抖动把消息清空。
      }
    },
    /**
     * 定时轮询未读数。只在第一页时顺带刷新列表，避免把用户已加载的分页重置掉。
     */
    async pollUnread() {
      if (!this.serverMode) return;
      try {
        const count = await getUnreadNotificationCount();
        const next = Number(count?.count) || 0;
        if (next === this.unreadTotal) return;
        const increased = next > this.unreadTotal;
        this.unreadTotal = next;
        if (increased && this.currentPage === 1) {
          const page = await getNotifications({ current: 1, size: this.pageSize });
          this.items = (page?.records || []).map(mapNotification);
          this.total = Number(page?.total) || 0;
          this.hasMore = this.items.length < this.total;
        }
      } catch {
        // 轮询失败不影响当前界面，下一轮继续。
      }
    },
    /** 进入工作台后启动；重复调用不会产生多个计时器。 */
    startPolling(intervalMs = 60000) {
      if (pollTimer) return;
      pollTimer = setInterval(() => {
        this.pollUnread();
      }, Math.max(15000, Number(intervalMs) || 60000));
    },
    stopPolling() {
      if (pollTimer) {
        clearInterval(pollTimer);
        pollTimer = null;
      }
    },
    /** 加载下一页；只追加尚未加载的通知。 */
    async loadMore() {
      if (!this.serverMode || !this.hasMore || this.loadingMore) {
        return;
      }
      this.loadingMore = true;
      try {
        const next = this.currentPage + 1;
        const page = await getNotifications({ current: next, size: this.pageSize });
        const existing = new Set(this.items.map((item) => item.key));
        const appended = (page?.records || [])
          .map(mapNotification)
          .filter((item) => !existing.has(item.key));
        this.items.push(...appended);
        this.currentPage = next;
        this.total = Number(page?.total) || this.total;
        this.hasMore = this.items.length < this.total;
      } catch {
        // 保持当前页，用户可重试。
      } finally {
        this.loadingMore = false;
      }
    },
    /** 兼容旧入口：后端不可用时按预约/诉求/报名派生消息。 */
    buildDerived({ appointments = [], appeals = [], signupIds = [] } = {}) {
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
          detail: item.handlerName ? `处理人：${item.handlerName}` : "等待受理",
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
      this.serverMode = false;
      this.synced = true;
      this.total = items.length;
      this.currentPage = 1;
      this.hasMore = false;
      this.pruneReadKeys();
    },
    pruneReadKeys() {
      const alive = new Set(this.items.map((item) => item.key));
      const next = this.readKeys.filter((key) => alive.has(key));
      if (next.length !== this.readKeys.length) {
        this.readKeys = next;
        this.persist();
      }
    },
    async markRead(key) {
      const item = this.items.find((row) => row.key === key);
      if (item?.serverId) {
        if (item.read) return;
        try {
          await markNotificationRead(item.serverId);
          item.read = true;
          this.unreadTotal = Math.max(0, this.unreadTotal - 1);
        } catch {
          // 标记失败时保持未读，下一次刷新会重新同步。
        }
        return;
      }
      if (!key || this.readKeys.includes(key)) return;
      this.readKeys = [...this.readKeys, key];
      this.persist();
    },
    async markAllRead() {
      if (this.serverMode) {
        try {
          await markAllNotificationsRead();
          this.items.forEach((item) => { item.read = true; });
          this.unreadTotal = 0;
        } catch {
          // 保持原状态，避免界面显示已读但服务端仍是未读。
        }
        return;
      }
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
