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
      <el-tab-pane v-if="canSee('signups')" label="报名记录" name="signups">
        <AdminActivities :section="'signups'" />
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
import { computed, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { getAdminOverview } from "../api/admin";
import { getStatOverview } from "../api/stat";
import { errorMessage } from "../api/http";
import { useUserStore } from "../stores/user";
import { PLATFORM_ROLES, numberText } from "./admin/admin-shared";
import AdminOverview from "./admin/AdminOverview.vue";
import AdminAppointments from "./admin/AdminAppointments.vue";
import AdminAppeals from "./admin/AdminAppeals.vue";
import AdminStations from "./admin/AdminStations.vue";
import AdminServices from "./admin/AdminServices.vue";
import AdminContents from "./admin/AdminContents.vue";
import AdminActivities from "./admin/AdminActivities.vue";
import AdminUsers from "./admin/AdminUsers.vue";
import AdminRoles from "./admin/AdminRoles.vue";
import AdminReports from "./admin/AdminReports.vue";

/** 标签页定义：platformOnly 为 true 时仅平台管理员与超级管理员可见。 */
const tabs = [
  { name: "overview", label: "运营概览" },
  { name: "appointments", label: "预约审核" },
  { name: "appeals", label: "诉求处理" },
  { name: "stations", label: "驿站管理", platformOnly: true },
  { name: "services", label: "服务项目管理", platformOnly: true },
  { name: "contents", label: "资讯公告管理", platformOnly: true },
  { name: "activities", label: "活动管理" },
  { name: "signups", label: "报名记录" },
  { name: "users", label: "账号管理", platformOnly: true },
  { name: "roles", label: "角色权限", platformOnly: true },
  { name: "reports", label: "报表导出" }
];

const userStore = useUserStore();
const router = useRouter();
const activeTab = ref("overview");
const loading = ref(false);
const stat = ref({});
const workflow = ref({});

const isPlatformAdmin = computed(() => PLATFORM_ROLES.includes(userStore.roleCode));
const roleLabel = computed(() => userStore.role || "未知角色");
const visibleTabs = computed(() => tabs.filter((tab) => !tab.platformOnly || isPlatformAdmin.value));

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

onMounted(loadSummary);
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
.admin-tabs { padding: 0 20px 20px; background: #fff; border: 1px solid #e5eaf0; border-radius: 6px; }
@media (max-width: 1280px) { .admin-metrics { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 680px) { .admin-header { align-items: flex-start; flex-direction: column; } .admin-header-actions { flex-wrap: wrap; } .admin-metrics { grid-template-columns: 1fr; } }
</style>
