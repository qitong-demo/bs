<template>
  <section class="admin-panel" v-loading="loading">
    <div class="sub-heading">
      <div>
        <h2>运营概览</h2>
        <p class="admin-note">
          数据来源：统计接口与工作流概览实时聚合；口径：{{ stat.dataScope || "—" }}；状态：{{ stat.dataStatus || "—" }}
        </p>
      </div>
      <el-button size="small" @click="loadAll"><el-icon><Refresh /></el-icon>刷新</el-button>
    </div>

    <div class="admin-stat-grid">
      <article v-for="card in cards" :key="card.label" class="admin-stat-card">
        <span class="admin-stat-label">{{ card.label }}</span>
        <strong class="admin-stat-value">{{ card.value }}</strong>
        <small class="admin-stat-hint">{{ card.hint }}</small>
      </article>
    </div>

    <div class="admin-split">
      <section class="admin-panel admin-panel--inner">
        <div class="sub-heading">
          <h2>驿站联系申请排行</h2>
          <span>按累计预约单量排序，最多展示前 10 项</span>
        </div>
        <el-table :data="ranking" v-loading="loading" stripe empty-text="暂无预约数据，无法生成排行">
          <el-table-column label="排名" type="index" width="72" />
          <el-table-column prop="name" label="驿站" min-width="160" />
          <el-table-column label="预约单量" width="110">
            <template #default="{ row }">{{ numberText(row.count) }}</template>
          </el-table-column>
          <el-table-column label="相对占比" min-width="200">
            <template #default="{ row }">
              <el-progress :percentage="Number(row.percent) || 0" :stroke-width="10" :show-text="false" />
              <span class="admin-percent">{{ numberText(row.percent) }}%</span>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="admin-panel admin-panel--inner">
        <div class="sub-heading">
          <h2>工作流概览</h2>
          <span>来自管理工作台概览接口</span>
        </div>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="预约记录总数">{{ numberText(workflow.appointments) }}</el-descriptions-item>
          <el-descriptions-item label="待审核预约">{{ numberText(workflow.pendingAppointments) }}</el-descriptions-item>
          <el-descriptions-item label="诉求记录总数">{{ numberText(workflow.appeals) }}</el-descriptions-item>
          <el-descriptions-item label="待处理诉求">{{ numberText(workflow.pendingAppeals) }}</el-descriptions-item>
          <el-descriptions-item label="已办结诉求">{{ numberText(workflow.resolvedAppeals) }}</el-descriptions-item>
          <el-descriptions-item label="活动报名数">{{ numberText(workflow.signups) }}</el-descriptions-item>
          <el-descriptions-item label="诉求评价数">{{ numberText(workflow.appealEvaluations) }}</el-descriptions-item>
          <el-descriptions-item label="概览数据状态">{{ textOf(workflow.dataStatus) }}</el-descriptions-item>
          <el-descriptions-item label="统计生成时间">{{ textOf(stat.generatedAt) }}</el-descriptions-item>
          <el-descriptions-item label="当前操作角色">{{ textOf(workflow.operatorRole) }}</el-descriptions-item>
        </el-descriptions>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { getAdminOverview } from "../../api/admin";
import { getServiceRanking, getStatOverview } from "../../api/stat";
import { errorMessage } from "../../api/http";
import { numberText, textOf } from "./admin-shared";

const loading = ref(false);
const stat = ref({});
const workflow = ref({});
const ranking = ref([]);

const cards = computed(() => [
  { label: "驿站总数", value: numberText(stat.value.stationCount), hint: `营业 ${numberText(stat.value.openStationCount)} · 停用 ${numberText(stat.value.closedStationCount)}` },
  { label: "预约总数", value: numberText(stat.value.appointmentCount), hint: `近 30 天 ${numberText(stat.value.monthlyAppointmentCount)} · 待审核 ${numberText(stat.value.pendingAppointmentCount)}` },
  { label: "诉求总数", value: numberText(stat.value.appealCount), hint: `待处理 ${numberText(stat.value.pendingAppealCount)} · 已办结 ${numberText(stat.value.resolvedAppealCount)}` },
  { label: "诉求办结率", value: `${numberText(stat.value.finishRate)}%`, hint: "已办结诉求占全部诉求的比例" },
  { label: "诉求处理记录", value: numberText(stat.value.appealRecordCount), hint: "分派、处理、办结等流转记录条数" },
  { label: "活动总数", value: numberText(stat.value.activityCount), hint: `已发布 ${numberText(stat.value.publishedActivityCount)}` },
  { label: "活动报名", value: numberText(stat.value.signupCount), hint: "全部活动累计报名人次" },
  { label: "服务评价", value: numberText(stat.value.evaluationCount), hint: `平均评分 ${numberText(stat.value.averageScore)}` }
]);

async function loadAll() {
  loading.value = true;
  try {
    const [statData, workflowData, rankingData] = await Promise.all([
      getStatOverview(),
      getAdminOverview(),
      getServiceRanking()
    ]);
    stat.value = statData || {};
    workflow.value = workflowData || {};
    ranking.value = Array.isArray(rankingData) ? rankingData : [];
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    loading.value = false;
  }
}

onMounted(loadAll);
</script>

<style scoped>
.admin-panel {
  padding: 16px 18px 18px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 6px;
}

.admin-panel--inner {
  padding: 14px 16px 16px;
  box-shadow: none;
}

.admin-note {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 12px;
}

.admin-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.admin-stat-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 92px;
  padding: 14px 16px;
  background: #fbfdfd;
  border: 1px solid var(--line);
  border-radius: 6px;
}

.admin-stat-label {
  color: #5e6877;
  font-size: 13px;
}

.admin-stat-value {
  color: #101828;
  font-size: 26px;
  line-height: 1.1;
}

.admin-stat-hint {
  color: var(--muted);
  font-size: 12px;
}

.admin-split {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(320px, 0.75fr);
  gap: 16px;
}

.admin-percent {
  margin-left: 8px;
  color: var(--muted);
  font-size: 12px;
}

@media (max-width: 1280px) {
  .admin-stat-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .admin-split { grid-template-columns: 1fr; }
}
</style>
