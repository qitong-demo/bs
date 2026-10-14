<template>
  <div class="app-shell">
    <aside class="sidebar" :class="{ collapsed }">
      <div class="brand">
        <div class="brand-mark">
          <el-icon><House /></el-icon>
        </div>
        <div class="brand-text">
          <strong>新就业群体友好驿站</strong>
          <span>服务与诉求响应平台</span>
        </div>
      </div>

      <nav class="nav-list">
        <button
          v-for="item in navItems"
          :key="item.label"
          class="nav-item"
          :class="{ active: activeNav === item.label }"
          @click="onNavClick(item.label)"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
          <el-badge v-if="item.badge" :value="item.badge" />
        </button>
      </nav>

      <button class="collapse-button" @click="collapsed = !collapsed">
        <el-icon><ArrowLeft /></el-icon>
        <span>收起菜单</span>
      </button>
    </aside>

    <!-- 手机端侧栏是浮层：展开时用遮罩承接点击，点一下收起 -->
    <div v-if="isMobile && !collapsed" class="sidebar-backdrop" @click="collapsed = true"></div>

    <main class="workspace">
      <header class="topbar">
        <button class="icon-button" @click="collapsed = !collapsed" aria-label="切换菜单">
          <el-icon><Fold /></el-icon>
        </button>

        <el-select
          v-model="cityCode"
          class="city-select"
          size="large"
          placeholder="选择城市"
          @change="onCityChange"
        >
          <el-option
            v-for="item in userStore.cities"
            :key="item.cityCode"
            :label="cityOptionLabel(item)"
            :value="item.cityCode"
          />
        </el-select>

        <!-- 定位到的城市平台未覆盖时给出明确提示，而不是静默把用户当成别的城市的人 -->
        <el-tooltip v-if="userStore.coverageHint" :content="userStore.coverageHint" placement="bottom">
          <el-tag class="coverage-hint" type="warning" effect="light" size="large">未覆盖</el-tag>
        </el-tooltip>

        <el-input
          v-model="keyword"
          class="search"
          size="large"
          clearable
          placeholder="搜索驿站、服务、政策、资讯等"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>

        <div class="topbar-actions">
          <el-badge :value="messageStore.unreadCount" :hidden="messageStore.unreadCount === 0" :max="99">
            <button class="icon-button" aria-label="消息通知" @click="openMessages">
              <el-icon><Bell /></el-icon>
            </button>
          </el-badge>
          <el-tag class="role-tag" type="success" effect="plain">{{ userStore.role }}</el-tag>
          <el-button v-if="isAdmin" type="primary" plain @click="router.push('/admin')">管理工作台</el-button>
          <el-dropdown>
            <button class="profile">
              <img src="/assets/rider-avatar.png" alt="用户头像" />
              <span>{{ userStore.user.name }}</span>
              <el-icon><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="activeNav = '个人中心'">个人中心</el-dropdown-item>
                <el-dropdown-item @click="loadAll">刷新数据</el-dropdown-item>
                <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <ModuleView
      v-if="activeNav !== '首页'"
      :view="activeNav"
      :prefill-station-id="pendingStationId"
      @back-home="activeNav = '首页'"
      @change-view="activeNav = $event"
      @prefill-consumed="pendingStationId = null"
    />
      <section v-else class="content">
        <div class="data-notice" role="note">
          <el-icon><InfoFilled /></el-icon>
          <span><strong>{{ overview.dataStatus || "数据加载中" }}</strong>：{{ dataNote }}</span>
        </div>
        <div class="metrics-grid" v-loading="loading">
          <StatCard v-for="item in metrics" :key="item.label" :item="item" />
        </div>

        <!-- 主区：地图占最大空间，右侧驿站列表直接可操作（定位 / 提前联系） -->
        <div class="map-layout">
          <section class="panel map-panel">
            <div class="panel-head">
              <div>
                <h2>友好驿站点位</h2>
                <span>{{ currentCity?.cityName || "当前城市" }} · 已标注 {{ mappedCount }} 个点位</span>
              </div>
              <el-button link type="primary" @click="activeNav = '驿站查询'">进入驿站查询</el-button>
            </div>
            <div class="map-canvas">
              <StationMap
                :city="currentCity"
                :stations="nearbyStations"
                height="100%"
                :focus-id="focusedStationId"
              />
            </div>
          </section>

          <section class="panel station-side">
            <div class="panel-head">
              <div>
                <h2>附近驿站</h2>
                <span>{{ locationHint }}</span>
              </div>
            </div>
            <el-input
              v-model="stationKeyword"
              class="station-side-search"
              placeholder="按名称或地址筛选"
              clearable
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <div class="station-side-list" v-loading="loading">
              <button
                v-for="item in nearbyStations"
                :key="item.id"
                class="station-side-item"
                :class="{ active: String(focusedStationId) === String(item.id) }"
                @click="focusStation(item)"
              >
                <span class="side-item-head">
                  <strong>{{ item.stationName }}</strong>
                  <em v-if="item.distanceKm !== null">{{ item.distanceKm }} km</em>
                </span>
                <span class="side-item-meta">
                  <el-tag :type="stationOpenState(item).tag" size="small" effect="light">
                    {{ stationOpenState(item).label }}
                  </el-tag>
                  <span class="side-item-hours">{{ item.openTime || "营业时间待核验" }}</span>
                </span>
                <span class="side-item-actions">
                  <span class="side-link" @click.stop="focusStation(item)">地图定位</span>
                  <span class="side-link primary" @click.stop="contactStation(item)">提前联系</span>
                </span>
              </button>
              <el-empty
                v-if="!loading && nearbyStations.length === 0"
                :image-size="64"
                description="没有符合条件的驿站"
              />
            </div>
          </section>
        </div>

        <!-- 次要区：用户看「我的」，管理员看「待办」——管理员不是用户，没有自己的预约与诉求 -->
        <div class="secondary-grid">
          <!-- 管理员：待办入口。点进去到管理工作台对应标签页，不在这里堆明细。 -->
          <section class="panel" v-if="isAdmin">
            <PanelHeader title="待审核预约" action="去处理" @click="goAdmin('appointments')" />
            <div class="admin-todo">
              <strong class="admin-todo-number">{{ pendingAppointments }}</strong>
              <span>条预约等待处理</span>
              <p class="admin-todo-hint">用户在服务预约里填了想提前沟通的事项，需要你确认是否可行并回复。</p>
            </div>
            <footer class="panel-footer right-only">
              <button @click="goAdmin('appointments')">前往预约审核 <el-icon><ArrowRight /></el-icon></button>
            </footer>
          </section>

          <section class="panel" v-if="isAdmin">
            <PanelHeader title="待处理诉求" action="去处理" @click="goAdmin('appeals')" />
            <div class="admin-todo">
              <strong class="admin-todo-number">{{ pendingAppeals }}</strong>
              <span>条诉求等待受理或回复</span>
              <p class="admin-todo-hint">用户提交的诉求与反馈，需要你联系核实后写下给用户的回复。</p>
            </div>
            <footer class="panel-footer right-only">
              <button @click="goAdmin('appeals')">前往诉求处理 <el-icon><ArrowRight /></el-icon></button>
            </footer>
          </section>

          <section class="panel" v-if="!isAdmin">
            <PanelHeader title="我的预约" action="更多" @click="activeNav = '服务预约'" />
            <div class="record-list compact" v-loading="loading">
              <div v-for="item in myAppointments.slice(0, 5)" :key="item.id" class="record-row">
                <div>
                  <strong>{{ item.serviceName }}</strong>
                  <span>{{ item.stationName }}</span>
                </div>
                <div class="record-meta">
                  <span>{{ item.appointDate }}</span>
                  <el-tag :type="appointmentTagType(item.status)" effect="plain" size="small">{{ item.statusLabel }}</el-tag>
                </div>
              </div>
              <el-empty v-if="!loading && myAppointments.length === 0" :image-size="56" description="暂无预约记录" />
            </div>
            <footer class="panel-footer right-only">
              <button @click="activeNav = '服务预约'">前往服务预约 <el-icon><ArrowRight /></el-icon></button>
            </footer>
          </section>

          <section class="panel" v-if="!isAdmin">
            <PanelHeader title="我的诉求进度" action="更多" @click="activeNav = '我的诉求'" />
            <div class="appeal-list compact">
              <div v-for="appeal in filteredAppeals.slice(0, 5)" :key="appeal.id" class="appeal-row">
                <span class="dot" :class="`dot--${appeal.status === 'RESOLVED' ? 'primary' : 'warning'}`"></span>
                <strong>{{ appeal.title }}</strong>
                <el-tag :type="appeal.status === 'RESOLVED' ? 'success' : 'warning'" effect="plain" size="small">
                  {{ appeal.statusLabel }}
                </el-tag>
              </div>
              <el-empty v-if="!loading && filteredAppeals.length === 0" :image-size="56" description="还没有提交过诉求" />
            </div>
            <footer class="panel-footer right-only">
              <button @click="activeNav = '我的诉求'">前往我的诉求 <el-icon><ArrowRight /></el-icon></button>
            </footer>
          </section>

          <section class="panel">
            <PanelHeader title="政策资讯" action="更多" @click="activeNav = '政策资讯'" />
            <div class="news-list compact">
              <button v-for="item in newsItems.slice(0, 5)" :key="item.source + item.id" class="news-row" @click="activeNav = '政策资讯'">
                <el-tag :type="newsType(item)" effect="light" size="small">{{ newsLabel(item) }}</el-tag>
                <strong>{{ item.title }}</strong>
                <span>{{ item.date }}</span>
              </button>
              <el-empty v-if="!loading && newsItems.length === 0" :image-size="56" description="暂无资讯" />
            </div>
            <footer class="panel-footer right-only">
              <button @click="activeNav = '政策资讯'">前往政策资讯 <el-icon><ArrowRight /></el-icon></button>
            </footer>
          </section>
        </div>

        <!-- 趋势图默认收起：它是回顾性数据，不需要一直占着首屏 -->
        <section class="panel chart-panel">
          <div class="panel-head">
            <div>
              <h2>服务数据趋势</h2>
              <span>近 6 个自然月 · 实时聚合</span>
            </div>
            <div class="panel-head-actions">
              <el-button link type="primary" @click="loadAll">刷新</el-button>
              <el-button link type="primary" @click="chartOpen = !chartOpen">
                {{ chartOpen ? "收起" : "展开" }}
              </el-button>
            </div>
          </div>
          <template v-if="chartOpen">
            <div v-show="hasTrendData" ref="chartRef" class="chart"></div>
            <!-- 数据全为 0 时坐标轴会退化成 0~0 而看起来空白，这里给出明确说明 -->
            <div v-if="!hasTrendData" class="chart-empty">
              <el-icon class="chart-empty-icon"><DataLine /></el-icon>
              <strong>还没有可统计的服务数据</strong>
              <p>平台尚无预约、诉求或报名记录。产生业务数据后，这里会按自然月自动聚合。</p>
            </div>
            <p class="chart-note">{{ trend.dataNote || "数据状态：后端实时聚合" }}</p>
          </template>
        </section>
      </section>
    </main>
    <!-- 预约表单只在服务预约页实现一处；首页的「提前联系」只负责带驿站切过去。 -->
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import StatCard from "../components/StatCard.vue";
import StationMap from "../components/StationMap.vue";
import ModuleView from "./ModuleView.vue";
import { errorMessage } from "../api/http";
import { getStationPage } from "../api/station";
import { getMyAppeals, getMyAppointments } from "../api/workflow";
import { getNewsPage, getNoticePage } from "../api/content";
import { getMonthlyTrend, getStatOverview } from "../api/stat";
import { getMySignups } from "../api/workflow";
import { getAdminOverview } from "../api/admin";
import { useUserStore } from "../stores/user";
import { useMessageStore } from "../stores/message";
import { openStateMeta } from "../utils/business-hours";

const PanelHeader = {
  props: { title: String, action: String },
  emits: ["click"],
  template: `
    <header class="panel-head">
      <h2>{{ title }}</h2>
      <button @click="$emit('click')">{{ action }} <el-icon><ArrowRight /></el-icon></button>
    </header>
  `
};

const userStore = useUserStore();
const router = useRouter();
const isAdmin = computed(() => ["STATION_ADMIN", "PLATFORM_ADMIN", "SUPER_ADMIN"].includes(userStore.roleCode));
const keyword = ref("");
const activeNav = ref("首页");
/** 消息与已读状态（顶栏角标与消息中心共用）。 */
const messageStore = useMessageStore();
const collapsed = ref(false);
/**
 * 是否处于窄屏（与 CSS 的 900px 断点保持一致）。
 * 窄屏下侧栏是浮层，必须默认收起，否则一进页面就被菜单挡住内容。
 */
const isMobile = ref(false);
let mobileQuery = null;

function syncViewport(matches) {
  isMobile.value = matches;
  // 首次进入窄屏或从桌面缩到窄屏时收起侧栏；回到桌面则展开。
  collapsed.value = matches;
}

function onViewportChange(event) {
  syncViewport(event.matches);
}

/** 手机端点击导航后自动收起侧栏，让内容立刻可见。 */
function onNavClick(label) {
  // 「管理工作台」不在本页切换视图，而是跳到独立的管理端路由。
  if (label === "管理工作台") {
    router.push("/admin");
    return;
  }
  // 管理员不应进入用户专属视图：后端按其数据范围过滤后只会返回空列表，
  // 进去也做不了什么，还会让人以为自己有用户身份。
  if (isAdmin.value && USER_ONLY_VIEWS.includes(label)) {
    ElMessage.info("该功能面向接受服务的一方，管理员请到管理工作台处理对应事项");
    router.push("/admin");
    return;
  }
  activeNav.value = label;
  if (isMobile.value) {
    collapsed.value = true;
  }
}

/** 管理员首页的两个待办数字，来自管理端概览。注意：该接口当前是全平台口径，未按角色过滤。 */
const pendingAppointments = ref(0);
const pendingAppeals = ref(0);

/** 跳到管理工作台的指定标签页，避免让管理员自己再找一遍。 */
function goAdmin(tab) {
  router.push({ path: "/admin", query: tab ? { tab } : {} });
}

/** 管理员登录后进入首页时，顺带取一次待办数字。 */
async function loadAdminTodo() {
  if (!isAdmin.value) return;
  try {
    const summary = await getAdminOverview();
    pendingAppointments.value = summary?.pendingAppointments ?? 0;
    pendingAppeals.value = summary?.pendingAppeals ?? 0;
  } catch {
    // 待办数字取不到不影响首页其他内容，静默降级为 0
    pendingAppointments.value = 0;
    pendingAppeals.value = 0;
  }
}
const loading = ref(false);

/** 顶栏城市选择器的绑定值，变化时写回 store 并重新加载首页数据。 */
const cityCode = ref("");

const overview = ref({});
const myAppointments = ref([]);
const myAppeals = ref([]);
const newsItems = ref([]);
const trend = ref({ months: [], appointments: [], appeals: [], signups: [], finishRate: [] });

/** 主区用的驿站列表：带坐标与距离，按距离排序（没有定位时按城市内顺序）。 */
const nearbyStations = ref([]);
/** 列表筛选关键字（名称/地址）。 */
const stationKeyword = ref("");
/** 当前在地图上聚焦的驿站 id，实现「列表 ↔ 地图」联动。 */
const focusedStationId = ref(null);
/** 趋势图默认收起。 */
const chartOpen = ref(false);
/** 从首页点「提前联系」时带过去、供服务预约页预选的驿站。 */
const pendingStationId = ref(null);

/**
 * 每分钟走一次：让主区列表里的「营业中 / 已打烊」随时间自行变化，
 * 而不是停在页面打开的那一刻。
 */
const now = ref(new Date());
let nowTimer = null;

const chartRef = ref(null);
const chartRange = ref("6");

/**
 * 侧栏导航。
 *
 * 驿站管理员与平台管理员**不是用户**，不应该看到用户专属功能：
 * 服务预约、我的诉求、活动报名、服务评价、我的收藏都是「接受服务的一方」才用的，
 * 管理员是提供服务与处理诉求的一方，让他们也能报名自己的活动、给自己驿站写评价，
 * 既没有意义，也会让权限边界变得含糊。
 *
 * 管理员用「管理工作台」进入审核与处理，而不是混在用户功能里。
 */
const USER_NAV = [
  { label: "首页", icon: "HomeFilled" },
  { label: "驿站查询", icon: "Location" },
  { label: "服务预约", icon: "Calendar" },
  { label: "诉求与反馈", icon: "MessageBox" },
  { label: "我的诉求", icon: "Document" },
  { label: "活动报名", icon: "Present" },
  { label: "政策资讯", icon: "Reading" },
  { label: "服务评价", icon: "Star" },
  { label: "消息中心", icon: "ChatDotRound" },
  { label: "数据统计", icon: "Histogram" },
  { label: "我的收藏", icon: "Collection" },
  { label: "个人中心", icon: "User" }
];

/** 管理员侧栏：去掉用户专属功能，换成管理入口。 */
const ADMIN_NAV = [
  { label: "首页", icon: "HomeFilled" },
  { label: "管理工作台", icon: "Operation" },
  { label: "驿站查询", icon: "Location" },
  { label: "政策资讯", icon: "Reading" },
  { label: "消息中心", icon: "ChatDotRound" },
  { label: "个人中心", icon: "User" }
];

/** 用户专属视图：管理员不应进入（后端也会按其数据范围过滤，进错页面只会看到空列表）。 */
const USER_ONLY_VIEWS = ["服务预约", "我的诉求", "活动报名", "服务评价", "我的收藏"];

const navItems = computed(() => (isAdmin.value ? ADMIN_NAV : USER_NAV));

/** 当前城市对象（含地图中心点），供 StationMap 与统计口径文案使用。 */
const currentCity = computed(() => {
  const hit = userStore.cities.find((item) => item.cityCode === userStore.cityCode);
  return hit || { cityName: userStore.cityName, centerLng: null, centerLat: null };
});

/** 当前城市名称文案，未确定时给出中性占位，避免出现空值。 */
const currentCityLabel = computed(() => userStore.cityName || "未选择城市");

const dataNote = computed(() => {
  if (!overview.value.generatedAt) return "正在读取后端统计数据。";
  // 当前只有「驿站查询」与地图按城市过滤；统计指标仍是全平台口径，必须如实标注，避免误读。
  const base = `当前城市：${currentCityLabel.value}（驿站查询与地图按该城市过滤）；统计口径：${overview.value.dataScope || "全平台已记录数据"}，为全平台汇总；生成时间 ${overview.value.generatedAt}。指标均为后端实时聚合结果。`;
  // 驿站管理员最容易误读：他的职责范围只有一个或几个驿站，
  // 但上面 6 张指标卡是全平台的。这里明确区分「看板是全平台」与「处理范围是自己的驿站」。
  if (userStore.roleCode === "STATION_ADMIN") {
    return `${base} 注意：以上指标为全平台汇总，不是你负责驿站的口径；你的处理范围（负责驿站的预约、所在区域的诉求）在「管理工作台」的对应列表中按范围过滤，但上面的待办数字是全平台的。`;
  }
  return base;
});

/**
 * 未读消息角标改由共享 store 提供（原先按「待处理预约 + 待处理诉求」另算一套，
 * 与消息中心的已读状态无关，导致点了「全部已读」角标依旧存在）。
 */
function openMessages() {
  activeNav.value = "消息中心";
}

const metrics = computed(() => {
  const data = overview.value || {};
  return [
    // 标签与说明都尽量短：6 列布局下单卡宽度有限，过长会被省略号截断。
    // 「全平台」这个限定词很重要（驿站数与营业数都不是当前城市口径），放进说明里保留。
    { label: "驿站点位", value: String(data.stationCount ?? "—"), unit: "个", note: `全平台营业中 ${data.openStationCount ?? 0} 个`, icon: "OfficeBuilding", tone: "green" },
    { label: "预约总量", value: String(data.appointmentCount ?? "—"), unit: "条", note: `近 30 天 ${data.monthlyAppointmentCount ?? 0} 条`, icon: "Calendar", tone: "blue" },
    { label: "诉求总量", value: String(data.appealCount ?? "—"), unit: "件", note: `待处理 ${data.pendingAppealCount ?? 0} 件`, icon: "Document", tone: "orange" },
    { label: "诉求办结率", value: String(data.finishRate ?? 0), unit: "%", note: `已办结 ${data.resolvedAppealCount ?? 0} 件`, icon: "CircleCheckFilled", tone: "green" },
    { label: "活动报名", value: String(data.signupCount ?? "—"), unit: "人次", note: `活动 ${data.activityCount ?? 0} 场`, icon: "Present", tone: "green" },
    { label: "服务评价均分", value: `${data.averageScore ?? 0} / 5`, unit: "", note: `评价 ${data.evaluationCount ?? 0} 条`, icon: "Star", tone: "blue" }
  ];
});

/**
 * 主区驿站列表：按关键字筛选后展示。
 * 数据已在加载阶段按「离我最近」排好序（有定位时）。
 */
const displayStations = computed(() => {
  const word = stationKeyword.value.trim();
  if (!word) return nearbyStations.value;
  return nearbyStations.value.filter((item) =>
    [item.stationName, item.address, item.area].filter(Boolean).join(" ").includes(word)
  );
});

/** 地图上实际能打点的数量（没有坐标的不参与）。 */
const mappedCount = computed(
  () => nearbyStations.value.filter((item) => item.longitude != null && item.latitude != null).length
);

/** 列表标题下的定位口径说明：有定位就说"按距离排序"，没有就说清原因。 */
const locationHint = computed(() => {
  if (hasUserLocation.value) return `共 ${displayStations.value.length} 个 · 按离你最近排序`;
  return `共 ${displayStations.value.length} 个 · 未获得定位，按平台顺序`;
});

/** 是否拿到了用户定位（拿到才能算距离、排序）。 */
const hasUserLocation = computed(
  () => Number.isFinite(userStore.latitude) && Number.isFinite(userStore.longitude)
);

/** 某驿站当前是否营业（按营业时间与当前时间实时判断）。 */
function stationOpenState(station) {
  return openStateMeta(station?.openTime, now.value, station?.status);
}

/** 点击列表项：地图飞过去并弹信息窗。 */
function focusStation(station) {
  if (!station?.id) return;
  focusedStationId.value = null;
  // 置空再赋值，保证连续点同一个驿站也能重新触发地图的 watch。
  nextTick(() => {
    focusedStationId.value = station.id;
  });
}

/** 「提前联系」：切到服务预约页并预选该驿站，表单只有一处实现。 */
function contactStation(station) {
  if (!station?.id) return;
  pendingStationId.value = station.id;
  activeNav.value = "服务预约";
}

const filteredAppeals = computed(() => {
  const word = keyword.value.trim();
  if (!word) return myAppeals.value;
  return myAppeals.value.filter((item) => item.title.includes(word) || item.statusLabel.includes(word));
});

function appointmentTagType(status) {
  return { CONFIRMED: "success", REJECTED: "danger", CANCELLED: "info", FINISHED: "success" }[status] || "primary";
}
function newsLabel(item) {
  return item.source === "notice" ? item.noticeType || "通知" : item.category || "政策";
}
function newsType(item) {
  const label = newsLabel(item);
  return { 政策: "success", 通知: "primary", 动态: "warning" }[label] || "info";
}

function logout() {
  userStore.clearSession();
  router.replace("/login");
}

/** 顶栏城市下拉文案：城市名 + 该城市已收录驿站数量。 */
function cityOptionLabel(item) {
  const count = Number(item?.stationCount);
  return Number.isFinite(count) && count > 0 ? `${item.cityName}（${count}）` : item?.cityName || "未命名城市";
}

/** 在顶栏手动切换城市：写入 localStorage 后重新加载首页（含地图）数据。 */
async function onCityChange(code) {
  userStore.setCity(code);
  focusedStationId.value = null;
  await loadAll();
  await loadNearbyStations();
}

/** 两点间大圆距离（公里），用于「离我最近」排序。 */
function distanceKm(lng1, lat1, lng2, lat2) {
  const toRad = (value) => (value * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

/**
 * 主区数据：按当前城市取前 50 条驿站。
 * 有用户定位时算出距离并按「离我最近」排序——新就业群体找服务点最先关心的就是远近；
 * 没有定位时保持平台顺序，并在标题下说明原因，不假装排过序。
 */
async function loadNearbyStations() {
  try {
    const page = await getStationPage({
      current: 1,
      size: 50,
      cityCode: userStore.cityCode || undefined
    });
    const hasLocation = Number.isFinite(userStore.latitude) && Number.isFinite(userStore.longitude);
    nearbyStations.value = (page?.records || [])
      .map((item) => {
        const lng = Number(item.longitude);
        const lat = Number(item.latitude);
        const located = Number.isFinite(lng) && Number.isFinite(lat);
        const distance =
          hasLocation && located
            ? distanceKm(userStore.longitude, userStore.latitude, lng, lat)
            : null;
        return {
          id: item.id,
          stationName: item.stationName,
          address: item.address,
          area: item.area,
          longitude: item.longitude,
          latitude: item.latitude,
          openTime: item.openTime,
          source: item.source,
          status: item.status,
          // 保留一位小数，骑行场景不需要更高精度
          distanceKm: distance === null ? null : Math.round(distance * 10) / 10
        };
      })
      .sort((left, right) => {
        if (left.distanceKm === null && right.distanceKm === null) return 0;
        if (left.distanceKm === null) return 1;
        if (right.distanceKm === null) return -1;
        return left.distanceKm - right.distanceKm;
      });
  } catch (error) {
    nearbyStations.value = [];
    ElMessage.error(errorMessage(error, "驿站数据加载失败"));
  }
}

/**
 * 进入页面时先确定用户城市：先拉城市列表，再按「定位优先、拒绝回退」的顺序解析，
 * 完成后城市相关的首页数据才有正确的 cityCode。
 */
async function initCity() {
  try {
    await userStore.loadCities();
  } catch (error) {
    userStore.cities = [];
    ElMessage.error(errorMessage(error, "城市列表加载失败"));
  }
  try {
    await userStore.resolveCity();
  } catch {
    // resolveCity 内部已静默兜底，这里仅防御未预期的异常，不阻塞首页渲染。
  }
  cityCode.value = userStore.cityCode;
}

async function loadAll() {
  loading.value = true;
  try {
    const [overviewData, stationPage, appointments, appeals, newsPage, noticePage, trendData, signups] = await Promise.all([
      getStatOverview(),
      getStationPage({ current: 1, size: 5, cityCode: userStore.cityCode || undefined }),
      getMyAppointments(),
      getMyAppeals(),
      getNewsPage({ current: 1, size: 4 }),
      getNoticePage({ current: 1, size: 3 }),
      getMonthlyTrend(),
      // 报名记录也要拉：消息里包含报名消息，少了它角标会比消息中心少几条。
      getMySignups().catch(() => [])
    ]);
    overview.value = overviewData || {};
    myAppointments.value = appointments || [];
    myAppeals.value = appeals || [];
    // 与消息中心共用同一份消息与已读状态，角标数字才不会与列表脱节。
    messageStore.sync({
      appointments: myAppointments.value,
      appeals: myAppeals.value,
      signupIds: (signups || []).map((item) => item.activityId)
    });
    const merged = [...(newsPage?.records || []), ...(noticePage?.records || [])];
    merged.sort((left, right) => String(right.date).localeCompare(String(left.date)));
    newsItems.value = merged.slice(0, 5);
    trend.value = trendData || trend.value;
    await nextTick();
    drawChart();
  } catch (error) {
    ElMessage.error(errorMessage(error, "首页数据加载失败"));
  } finally {
    loading.value = false;
  }
}

let chartInstance = null;
let echartsModule = null;
let chartObserver = null;

/**
 * 趋势数据是否全为 0。
 * 数据库刚初始化时所有月份都是 0，坐标轴会退化成 0~0，看起来就是"图表不显示"，
 * 因此这种情况改为展示明确的空状态说明。
 */
const hasTrendData = computed(() => {
  const data = trend.value || {};
  return ["appointments", "appeals", "signups", "finishRate"].some((key) =>
    (data[key] || []).some((value) => Number(value) > 0)
  );
});

async function drawChart() {
  if (!chartRef.value) return;
  if (!hasTrendData.value) {
    // 没有数据时不初始化图表，交给模板里的空状态渲染。
    if (chartInstance) {
      chartInstance.dispose();
      chartInstance = null;
    }
    return;
  }
  // echarts 只在首页趋势图使用，按需加载以避免进入首屏主包。
  if (!echartsModule) {
    echartsModule = await import("echarts");
  }
  if (!chartInstance) {
    chartInstance = echartsModule.init(chartRef.value);
    // 用 ResizeObserver 而不是只在 window resize 时重绘：
    // 侧栏折叠、模块切换都会改变容器宽度，窗口尺寸却没变。
    if (typeof ResizeObserver !== "undefined") {
      chartObserver = new ResizeObserver(() => chartInstance && chartInstance.resize());
      chartObserver.observe(chartRef.value);
    }
  }
  const data = trend.value;
  const appeals = data.appeals || [];
  // 某个月没有诉求时，办结率是"无数据"而不是 0%；用 null 断线，避免误读成 0% 办结。
  const finishRate = (data.finishRate || []).map((value, index) =>
    Number(appeals[index] || 0) > 0 ? value : null
  );
  chartInstance.setOption({
    color: ["#009b84", "#2f80ed", "#f59e0b"],
    tooltip: {
      trigger: "axis",
      backgroundColor: "rgba(32, 38, 49, 0.94)",
      borderWidth: 0,
      textStyle: { color: "#fff", fontSize: 12 },
      axisPointer: { type: "shadow", shadowStyle: { color: "rgba(0, 155, 132, 0.06)" } }
    },
    legend: {
      top: 0,
      left: 12,
      itemWidth: 12,
      itemHeight: 8,
      itemGap: 16,
      icon: "roundRect",
      textStyle: { color: "#586272", fontSize: 12 }
    },
    grid: { left: 40, right: 44, top: 46, bottom: 30 },
    xAxis: {
      type: "category",
      data: data.months || [],
      axisTick: { show: false },
      axisLine: { lineStyle: { color: "#e5ebf1" } },
      axisLabel: { color: "#7b8494", fontSize: 12 }
    },
    yAxis: [
      {
        type: "value",
        minInterval: 1,
        axisLabel: { color: "#7b8494", fontSize: 12 },
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { lineStyle: { color: "#eef3f7" } }
      },
      {
        type: "value",
        min: 0,
        max: 100,
        axisLabel: { formatter: "{value}%", color: "#7b8494", fontSize: 12 },
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: "预约量（条）",
        type: "bar",
        barWidth: 18,
        barGap: "20%",
        itemStyle: { borderRadius: [3, 3, 0, 0] },
        data: data.appointments || []
      },
      {
        name: "诉求量（件）",
        type: "bar",
        barWidth: 12,
        itemStyle: { borderRadius: [3, 3, 0, 0] },
        data: appeals
      },
      {
        name: "办结率（%）",
        type: "line",
        yAxisIndex: 1,
        smooth: true,
        symbol: "circle",
        symbolSize: 6,
        connectNulls: false,
        lineStyle: { width: 2 },
        data: finishRate
      }
    ]
  });
}

watch(chartRange, () => {
  ElMessage.info("趋势区间由后端按自然月聚合，当前固定展示近 6 个月。");
});

/**
 * 趋势图默认收起，收起时容器不在 DOM 里，loadAll 里的 drawChart 会因为
 * 拿不到 chartRef 而直接返回。所以展开时必须重新绘制一次。
 */
watch(chartOpen, async (open) => {
  if (!open) return;
  await nextTick();
  drawChart();
});

onMounted(async () => {
  loadAdminTodo();
  // 每分钟走一次，让主区列表的「营业中 / 已打烊」随时间自行变化
  nowTimer = setInterval(() => {
    now.value = new Date();
  }, 60000);
  // 窄屏下侧栏默认收起，避免浮层菜单挡住内容
  if (typeof window !== "undefined" && window.matchMedia) {
    mobileQuery = window.matchMedia("(max-width: 900px)");
    syncViewport(mobileQuery.matches);
    mobileQuery.addEventListener?.("change", onViewportChange);
  }
  await initCity();
  await Promise.all([loadAll(), loadNearbyStations()]);
});

onBeforeUnmount(() => {
  mobileQuery?.removeEventListener?.("change", onViewportChange);
  if (nowTimer) {
    clearInterval(nowTimer);
    nowTimer = null;
  }
  if (chartObserver) {
    chartObserver.disconnect();
    chartObserver = null;
  }
  if (chartInstance) {
    chartInstance.dispose();
    chartInstance = null;
  }
});
</script>
