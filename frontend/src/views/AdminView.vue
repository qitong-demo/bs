<template>
  <main class="admin-page">
    <header class="admin-header">
      <div>
        <p class="admin-eyebrow">FRIENDLY STATION · OPERATIONS</p>
        <h1>管理工作台</h1>
        <p class="admin-subtitle">预约审核、诉求流转、驿站运营与账号权限</p>
      </div>
      <div class="admin-header-actions">
        <el-tag :type="isPlatformAdmin ? 'success' : 'warning'" effect="plain">{{ roleLabel }}</el-tag>
        <el-tag type="info" effect="plain">数据均来自后端接口</el-tag>
        <el-button @click="router.push('/')"><el-icon><HomeFilled /></el-icon>返回首页</el-button>
      </div>
    </header>

    <el-alert class="admin-notice" type="info" :closable="false" show-icon>
      统计与列表数据由后端接口实时返回：mysql 配置下写入数据库并长期保留，默认演示配置下保存在服务端内存、重启后清空。
      标签页按角色显示，后端接口另有权限校验；驿站管理员只看到自己所负责驿站的预约，以及负责驿站所在区域的诉求。
    </el-alert>

    <section class="admin-metrics" aria-label="运营概览" v-loading="loading">
      <article v-for="metric in metrics" :key="metric.label" class="admin-metric">
        <span>{{ metric.label }}</span>
        <strong>{{ metric.value }}</strong>
        <small>{{ metric.hint }}</small>
      </article>
    </section>

    <el-tabs v-model="activeTab" class="admin-tabs">
      <el-tab-pane v-if="canSee('overview')" label="运营概览" name="overview">
        <AdminOverview />
      </el-tab-pane>
      <el-tab-pane v-if="canSee('appointments')" label="预约审核" name="appointments">
        <AdminAppointments />
      </el-tab-pane>
      <el-tab-pane v-if="canSee('appeals')" label="诉求处理" name="appeals">
        <AdminAppeals />
      </el-tab-pane>
      <el-tab-pane v-if="canSee('changeRequests')" label="变更审批" name="changeRequests">
        <AdminChangeRequests />
      </el-tab-pane>
      <el-tab-pane v-if="canSee('stationOps')" label="我的驿站" name="stationOps">
        <AdminStationOps />
      </el-tab-pane>
      <el-tab-pane v-if="canSee('stationApplications')" name="stationApplications">
        <template #label>
          <span class="signup-tab-label">
            入驻审核
            <el-badge v-if="pendingStationApplications" :value="pendingStationApplications" :max="99" class="signup-tab-badge" />
          </span>
        </template>
        <AdminStationApplications
          :focus-application-id="focusApplicationId"
          @reviewed="loadPendingStationApplications"
        />
      </el-tab-pane>
      <el-tab-pane v-if="canSee('stations')" label="驿站管理" name="stations">
        <AdminStations />
      </el-tab-pane>
      <el-tab-pane v-if="canSee('services')" label="服务项目管理" name="services">
        <AdminServices />
      </el-tab-pane>
      <el-tab-pane v-if="canSee('contents')" label="资讯公告管理" name="contents">
        <AdminContents />
      </el-tab-pane>
      <el-tab-pane v-if="canSee('activities')" label="活动管理" name="activities">
        <AdminActivities />
      </el-tab-pane>
      <el-tab-pane v-if="canSee('signups')" name="signups">
        <template #label>
          <span class="signup-tab-label">
            报名审核
            <el-badge v-if="pendingSignups" :value="pendingSignups" :max="99" class="signup-tab-badge" />
          </span>
        </template>
        <AdminActivities
          :section="'signups'"
          :focus-signup-id="focusSignupId"
          @reviewed="loadPendingSignups"
        />
      </el-tab-pane>
      <el-tab-pane v-if="canSee('users')" label="账号管理" name="users">
        <AdminUsers />
      </el-tab-pane>
      <el-tab-pane v-if="canSee('roles')" label="角色权限" name="roles">
        <AdminRoles />
      </el-tab-pane>
      <el-tab-pane v-if="canSee('reports')" label="报表导出" name="reports">
        <AdminReports />
      </el-tab-pane>
    </el-tabs>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { getAdminOverview } from "../api/admin";
import { getStatOverview } from "../api/stat";
import { errorMessage } from "../api/http";
import { useUserStore } from "../stores/user";
import { useMessageStore } from "../stores/message";
import { PLATFORM_ROLES, numberText } from "./admin/admin-shared";
import { getMyPermissions } from "../api/admin";
import { getSignupPendingCount } from "../api/workflow";
import { getStationApplicationPendingCount } from "../api/station-apply";
import AdminOverview from "./admin/AdminOverview.vue";
import AdminAppointments from "./admin/AdminAppointments.vue";
import AdminAppeals from "./admin/AdminAppeals.vue";
import AdminChangeRequests from "./admin/AdminChangeRequests.vue";
import AdminStations from "./admin/AdminStations.vue";
import AdminStationApplications from "./admin/AdminStationApplications.vue";
import AdminStationOps from "./admin/AdminStationOps.vue";
import AdminServices from "./admin/AdminServices.vue";
import AdminContents from "./admin/AdminContents.vue";
import AdminActivities from "./admin/AdminActivities.vue";
import AdminUsers from "./admin/AdminUsers.vue";
import AdminRoles from "./admin/AdminRoles.vue";
import AdminReports from "./admin/AdminReports.vue";

/**
 * 标签页定义：每个标签声明它需要的**功能权限**。
 *
 * 原先是前端硬编码 platformOnly，问题是子账号角色同样是管理员，
 * 于是会看到自己无权使用的标签、点进去只拿到 403。
 * 现在改为由后端返回的生效权限驱动，界面与后端的判断保持一致。
 * 不声明 permission 的标签（运营概览）对所有管理角色可见。
 */
const tabs = [
  { name: "overview", label: "运营概览" },
  { name: "stationOps", label: "我的驿站", roles: ["STATION_ADMIN"] },
  { name: "appointments", label: "预约审核", permission: "APPOINTMENT_AUDIT" },
  { name: "appeals", label: "诉求处理", permission: "APPEAL_HANDLE" },
  { name: "changeRequests", label: "变更审批", permission: "CHANGE_APPROVE" },
  { name: "stationApplications", label: "入驻审核", roles: ["PLATFORM_ADMIN"], permission: "STATION_MANAGE" },
  { name: "stations", label: "驿站管理", permission: "STATION_MANAGE" },
  { name: "services", label: "服务项目管理", permission: "SERVICE_MANAGE" },
  { name: "contents", label: "资讯公告管理", permission: "NEWS_MANAGE" },
  { name: "activities", label: "活动管理", permission: "ACTIVITY_MANAGE" },
  { name: "signups", label: "报名记录", permission: "SIGNUP_MANAGE" },
  { name: "users", label: "账号管理", permission: "ACCOUNT_MANAGE" },
  { name: "roles", label: "角色权限", permission: "ACCOUNT_MANAGE" },
  { name: "reports", label: "报表导出", permission: "REPORT_EXPORT" }
];

/** 当前账号的生效权限；null 表示获取失败、退回按角色判断。 */
const myPermissions = ref(null);

async function loadMyPermissions() {
  try {
    const data = await getMyPermissions();
    myPermissions.value = data?.permissions || [];
  } catch {
    // 取不到权限时退回"按角色判断"，至少不让管理端整页不可用。
    // 后端仍会独立校验，界面判断错不会造成越权。
    myPermissions.value = null;
  }
}

const userStore = useUserStore();
const messageStore = useMessageStore();
const router = useRouter();
const route = useRoute();
const activeTab = ref("overview");
const pendingSignups = ref(0);
const pendingStationApplications = ref(0);
const focusSignupId = ref(Number(route.query.signupId) || 0);
const focusApplicationId = ref(Number(route.query.applicationId) || 0);
const loading = ref(false);
const stat = ref({});
let pendingSignupTimer = null;
const workflow = ref({});

const isPlatformAdmin = computed(() => PLATFORM_ROLES.includes(userStore.roleCode));
const roleLabel = computed(() => userStore.role || "未知角色");
const visibleTabs = computed(() => tabs.filter((tab) => {
  // 角色限定标签（例如“我的驿站”只给驿站管理员）
  if (tab.roles && !tab.roles.includes(userStore.roleCode)) {
    return false;
  }
  if (!tab.permission) {
    return true;
  }
  // 权限获取失败时退回按角色判断（仅管理员角色可见）
  if (myPermissions.value === null) {
    return isPlatformAdmin.value;
  }
  return myPermissions.value.includes(tab.permission);
}));

const metrics = computed(() => [
  {
    label: "驿站数量",
    value: numberText(stat.value.stationCount),
    hint: `营业 ${numberText(stat.value.openStationCount)} · 停用 ${numberText(stat.value.closedStationCount)}`
  },
  {
    label: "预约记录",
    value: numberText(workflow.value.appointments),
    hint: `待审核 ${numberText(workflow.value.pendingAppointments)} · 全部 ${numberText(stat.value.appointmentCount)}`
  },
  {
    label: "诉求记录",
    value: numberText(workflow.value.appeals),
    hint: `待处理 ${numberText(workflow.value.pendingAppeals)} · 已办结 ${numberText(workflow.value.resolvedAppeals)}`
  },
  {
    label: "活动报名",
    value: numberText(workflow.value.signups),
    hint: `活动 ${numberText(stat.value.activityCount)} 场 · 已发布 ${numberText(stat.value.publishedActivityCount)}`
  },
  {
    label: "服务评价",
    value: numberText(stat.value.evaluationCount),
    hint: `平均评分 ${numberText(stat.value.averageScore)}`
  }
]);

function canSee(name) {
  return visibleTabs.value.some((tab) => tab.name === name);
}

watch(
  [activeTab, visibleTabs],
  () => {
    if (!canSee(activeTab.value)) {
      activeTab.value = "overview";
    }
  },
  { immediate: true }
);

async function loadSummary() {
  loading.value = true;
  try {
    const [statData, workflowData] = await Promise.all([getStatOverview(), getAdminOverview()]);
    stat.value = statData || {};
    workflow.value = workflowData || {};
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    loading.value = false;
  }
}

async function loadPendingSignups() {
  try {
    const data = await getSignupPendingCount();
    pendingSignups.value = Number(data?.count) || 0;
  } catch {
    pendingSignups.value = 0;
  }
}

async function loadPendingStationApplications() {
  try {
    const data = await getStationApplicationPendingCount();
    pendingStationApplications.value = Number(data?.count) || 0;
  } catch {
    pendingStationApplications.value = 0;
  }
}

watch(
  () => [route.query.tab, route.query.signupId, route.query.applicationId],
  ([tab, signupId, applicationId]) => {
    if (tab === "signups" || signupId) {
      activeTab.value = "signups";
    }
    if (tab === "stationApplications" || applicationId) {
      activeTab.value = "stationApplications";
    }
    focusSignupId.value = Number(signupId) || 0;
    focusApplicationId.value = Number(applicationId) || 0;
  }
);

onMounted(() => {
  messageStore.startPolling();
  messageStore.refreshQuietly();
  loadMyPermissions();
  loadSummary();
  loadPendingSignups();
  loadPendingStationApplications();
  pendingSignupTimer = setInterval(() => {
    loadPendingSignups();
    loadPendingStationApplications();
  }, 60000);
  // 允许从首页的待办卡片直接落到对应标签页（/admin?tab=appointments）
  const tab = String(route.query.tab || "");
  if (tab && canSee(tab)) {
    activeTab.value = tab;
  }
  if (route.query.signupId) {
    activeTab.value = "signups";
  }
  if (route.query.applicationId) {
    activeTab.value = "stationApplications";
  }
});

onBeforeUnmount(() => {
  messageStore.stopPolling();
  if (pendingSignupTimer) {
    clearInterval(pendingSignupTimer);
    pendingSignupTimer = null;
  }
});
</script>

<style scoped>
.admin-page { min-height: 100vh; padding: 32px clamp(20px, 4vw, 56px); background: #f5f7fa; color: #202a35; }
.admin-header { display: flex; justify-content: space-between; align-items: center; gap: 24px; margin: 0 auto 24px; max-width: 1440px; }
.admin-eyebrow { margin: 0 0 8px; color: #14836d; font-size: 12px; font-weight: 700; }
h1 { margin: 0; font-size: 28px; }
.admin-subtitle { margin: 8px 0 0; color: #687483; }
.admin-header-actions { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.admin-notice, .admin-metrics, .admin-tabs { max-width: 1440px; margin: 0 auto 18px; }
.admin-metrics { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; }
.admin-metric { min-height: 92px; padding: 16px 20px; background: #fff; border: 1px solid #e5eaf0; border-radius: 6px; display: flex; flex-direction: column; justify-content: center; gap: 6px; }
.admin-metric span { color: #687483; font-size: 13px; }
.admin-metric strong { font-size: 26px; color: #14836d; line-height: 1.1; }
.admin-metric small { color: #87909f; font-size: 12px; }
.signup-tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.signup-tab-label :deep(.el-badge__content) {
  position: static;
  transform: none;
}
.admin-tabs { padding: 0 20px 20px; background: #fff; border: 1px solid #e5eaf0; border-radius: 6px; }
.admin-tabs :deep(.el-tabs__header) { margin-bottom: 10px; }
/* 平台管理员有 12 个标签，窄屏时给左右滚动箭头留出空间，避免标签被裁切 */
.admin-tabs :deep(.el-tabs__nav-wrap.is-scrollable) { padding: 0 30px; }
.admin-tabs :deep(.el-tabs__item) { padding: 0 14px; }
@media (max-width: 1280px) {
  .admin-tabs :deep(.el-tabs__item) { padding: 0 10px; font-size: 13px; }
}
@media (max-width: 1280px) { .admin-metrics { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 680px) { .admin-header { align-items: flex-start; flex-direction: column; } .admin-header-actions { flex-wrap: wrap; } .admin-metrics { grid-template-columns: 1fr; } }
</style>
