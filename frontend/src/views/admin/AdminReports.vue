<template>
  <section class="admin-panel">
    <div class="sub-heading">
      <div>
        <h2>报表导出</h2>
        <p class="admin-note">导出后端实时聚合的 CSV 文件，导出内容与列表口径一致，最多 5000 条。</p>
      </div>
      <el-button size="small" :loading="loading" @click="loadOverview"><el-icon><Refresh /></el-icon>刷新口径</el-button>
    </div>

    <div class="admin-export-grid">
      <article v-for="item in exports" :key="item.type" class="admin-export-card">
        <div class="admin-export-head">
          <el-icon class="admin-export-icon"><Document /></el-icon>
          <div>
            <strong>{{ item.title }}</strong>
            <small>{{ item.fileName }}</small>
          </div>
        </div>
        <p class="admin-export-desc">{{ item.desc }}</p>
        <p class="admin-export-columns">字段：{{ item.columns }}</p>
        <el-button
          type="primary"
          plain
          size="small"
          :loading="exporting === item.type"
          @click="doExport(item)"
        >
          <el-icon><Download /></el-icon>导出 CSV
        </el-button>
      </article>
    </div>

    <div class="sub-heading admin-scope-heading">
      <div>
        <h2>数据口径说明</h2>
        <p class="admin-note">指标全部由后端在查询时实时聚合，前端不再编造统计数据。</p>
      </div>
    </div>

    <el-descriptions :column="1" border size="small" v-loading="loading">
      <el-descriptions-item label="数据范围 dataScope">{{ textOf(overview.dataScope) }}</el-descriptions-item>
      <el-descriptions-item label="数据状态 dataStatus">{{ textOf(overview.dataStatus) }}</el-descriptions-item>
      <el-descriptions-item label="生成时间 generatedAt">{{ textOf(overview.generatedAt) }}</el-descriptions-item>
    </el-descriptions>

    <el-table :data="metricRows" class="admin-metric-table" stripe empty-text="暂无统计数据">
      <el-table-column prop="label" label="指标" min-width="200" />
      <el-table-column prop="value" label="当前取值" width="180" />
      <el-table-column prop="note" label="口径说明" min-width="320" />
    </el-table>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { exportReport, getStatOverview } from "../../api/stat";
import { errorMessage } from "../../api/http";
import { numberText, textOf } from "./admin-shared";

const exports = [
  {
    type: "appeals",
    title: "诉求台账",
    fileName: "appeals.csv",
    desc: "全部诉求的提交、受理方、处理人与办结情况。",
    columns: "编号 / 诉求标题 / 诉求类型 / 所属区域 / 提交人 / 匿名 / 当前状态 / 受理方 / 处理人 / 提交时间 / 办结时间"
  },
  {
    type: "appointments",
    title: "预约台账",
    fileName: "appointments.csv",
    desc: "全部服务预约的驿站、项目、时段与审核结果。",
    columns: "编号 / 服务驿站 / 沟通事项 / 预约日期 / 预约时段 / 当前状态 / 预约人 / 审核说明 / 提交时间"
  },
  {
    type: "stations",
    title: "驿站台账",
    fileName: "stations.csv",
    desc: "驿站基础信息与开放状态，已逻辑删除的驿站不导出。",
    columns: "编号 / 驿站名称 / 所属区域 / 详细地址 / 联系电话 / 开放时间 / 状态 / 服务项目 / 创建时间"
  },
  {
    type: "signups",
    title: "活动报名台账",
    fileName: "activity-signups.csv",
    desc: "活动报名名单，含报名人与联系方式。",
    columns: "编号 / 活动名称 / 报名人 / 联系电话 / 报名状态 / 报名时间"
  },
  {
    type: "evaluations",
    title: "服务评价台账",
    fileName: "station-evaluations.csv",
    desc: "驿站服务评价明细与各项评分。",
    columns: "编号 / 评价驿站 / 评价人 / 综合评分 / 服务评分 / 环境评分 / 设施评分 / 评价内容 / 评价时间"
  }
];

const loading = ref(false);
const exporting = ref("");
const overview = ref({});

const metricRows = computed(() => [
  { label: "驿站总数", value: numberText(overview.value.stationCount), note: "stationCount：营业与停用驿站合计（不含已删除）。" },
  { label: "预约总数", value: numberText(overview.value.appointmentCount), note: "appointmentCount：全部服务预约记录数。" },
  { label: "近 30 天预约", value: numberText(overview.value.monthlyAppointmentCount), note: "monthlyAppointmentCount：按创建时间统计最近 30 天。" },
  { label: "待审核预约", value: numberText(overview.value.pendingAppointmentCount), note: "pendingAppointmentCount：状态为待驿站确认。" },
  { label: "诉求总数", value: numberText(overview.value.appealCount), note: "appealCount：全部诉求记录数。" },
  { label: "待处理诉求", value: numberText(overview.value.pendingAppealCount), note: "pendingAppealCount：未办结且未关闭的诉求。" },
  { label: "已办结诉求", value: numberText(overview.value.resolvedAppealCount), note: "resolvedAppealCount：状态为已办结的诉求。" },
  { label: "诉求办结率", value: `${numberText(overview.value.finishRate)}%`, note: "finishRate：已办结诉求 ÷ 诉求总数，保留 1 位小数。" },
  { label: "诉求处理记录", value: numberText(overview.value.appealRecordCount), note: "appealRecordCount：全部诉求的流转记录条数合计。" },
  { label: "活动总数", value: numberText(overview.value.activityCount), note: "activityCount：全部活动（含草稿）。" },
  { label: "已发布活动", value: numberText(overview.value.publishedActivityCount), note: "publishedActivityCount：状态为已发布的活动。" },
  { label: "活动报名", value: numberText(overview.value.signupCount), note: "signupCount：全部活动报名记录数。" },
  { label: "服务评价与均分", value: `${numberText(overview.value.evaluationCount)} / ${numberText(overview.value.averageScore)}`, note: "evaluationCount 与 averageScore：评价条数与平均得分。" }
]);

async function loadOverview() {
  loading.value = true;
  try {
    const data = await getStatOverview();
    overview.value = data || {};
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    loading.value = false;
  }
}

async function doExport(item) {
  exporting.value = item.type;
  try {
    await exportReport(item.type);
    ElMessage.success(`${item.title}已开始下载（${item.fileName}）`);
  } catch (error) {
    const status = error?.response?.status;
    ElMessage.error(status ? `导出失败（HTTP ${status}），请确认已登录且具备导出权限` : errorMessage(error));
  } finally {
    exporting.value = "";
  }
}

onMounted(loadOverview);
</script>

<style scoped>
.admin-panel {
  padding: 16px 18px 18px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 6px;
}

.admin-note {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 12px;
}

.admin-export-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.admin-export-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  background: #fbfdfd;
  border: 1px solid var(--line);
  border-radius: 6px;
}

.admin-export-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.admin-export-icon {
  font-size: 22px;
  color: var(--primary);
}

.admin-export-head strong {
  display: block;
  color: #253042;
  font-size: 15px;
}

.admin-export-head small {
  display: block;
  margin-top: 2px;
  color: var(--muted);
  font-size: 12px;
}

.admin-export-desc {
  margin: 0;
  color: #5e6877;
  font-size: 13px;
  line-height: 1.6;
}

.admin-export-columns {
  margin: 0 0 4px;
  color: #87909f;
  font-size: 12px;
  line-height: 1.6;
}

.admin-export-card .el-button {
  align-self: flex-start;
}

.admin-scope-heading { margin-top: 24px; }

.admin-metric-table { margin-top: 16px; }

@media (max-width: 1280px) {
  .admin-export-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
