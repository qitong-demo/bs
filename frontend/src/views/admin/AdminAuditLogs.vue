<template>
  <section class="admin-panel">
    <div class="admin-toolbar">
      <el-select v-model="action" class="admin-toolbar-select" clearable placeholder="全部操作" @change="load">
        <el-option label="平台干预预约" value="APPOINTMENT_INTERVENE" />
        <el-option label="诉求分派" value="APPEAL_ASSIGN" />
        <el-option label="诉求处理" value="APPEAL_HANDLE" />
        <el-option label="下发整改" value="RECTIFICATION_CREATE" />
        <el-option label="整改复核" value="RECTIFICATION_REVIEW" />
      </el-select>
      <span class="admin-toolbar-total">共 {{ total }} 条操作记录</span>
    </div>
    <el-table :data="rows" v-loading="loading" stripe empty-text="暂无操作记录">
      <el-table-column prop="id" label="编号" width="80" />
      <el-table-column prop="actorName" label="操作人" width="130" />
      <el-table-column prop="actorRole" label="角色" width="140" />
      <el-table-column prop="action" label="操作" width="190" />
      <el-table-column label="对象" width="150">
        <template #default="{ row }">{{ row.targetType }} #{{ row.targetId }}</template>
      </el-table-column>
      <el-table-column prop="reason" label="原因/说明" min-width="220" show-overflow-tooltip />
      <el-table-column prop="detail" label="详情" min-width="180" show-overflow-tooltip />
      <el-table-column prop="createTime" label="时间" width="170" />
    </el-table>
    <div class="admin-pager">
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :page-sizes="[20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="load"
        @size-change="load"
      />
    </div>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { getAuditLogPage } from "../../api/audit";
import { errorMessage } from "../../api/http";

const loading = ref(false);
const rows = ref([]);
const total = ref(0);
const action = ref("");
const query = reactive({ current: 1, size: 20 });

async function load() {
  loading.value = true;
  try {
    const data = await getAuditLogPage({ ...query, action: action.value });
    rows.value = data?.records || [];
    total.value = Number(data?.total) || 0;
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.admin-panel { padding: 16px 18px 18px; background: #fff; border: 1px solid var(--line); border-radius: 6px; }
.admin-toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.admin-toolbar-select { width: 180px; }
.admin-toolbar-total { margin-left: auto; color: var(--muted); font-size: 13px; }
.admin-pager { display: flex; justify-content: flex-end; margin-top: 14px; }
</style>
