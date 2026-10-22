<template>
  <section class="admin-panel">
    <div class="admin-toolbar">
      <el-input
        v-model="query.keyword"
        class="admin-toolbar-search"
        clearable
        placeholder="搜索预约人、驿站或沟通事项"
        @keyup.enter="search"
        @clear="search"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="query.status" class="admin-toolbar-select" clearable placeholder="全部状态" @change="search">
        <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
      <el-button @click="reset"><el-icon><RefreshLeft /></el-icon>重置</el-button>
      <span class="admin-toolbar-total">共 {{ total }} 条预约</span>
    </div>

    <el-table :data="rows" v-loading="loading" stripe empty-text="暂无预约记录">
      <el-table-column prop="id" label="编号" width="80" />
      <el-table-column label="预约人" min-width="110">
        <template #default="{ row }">{{ textOf(row.userName) }}</template>
      </el-table-column>
      <el-table-column label="服务驿站" min-width="160">
        <template #default="{ row }">{{ textOf(row.stationName) }}</template>
      </el-table-column>
      <el-table-column label="沟通事项" min-width="180">
        <template #default="{ row }">{{ textOf(row.serviceName) }}</template>
      </el-table-column>
      <el-table-column label="预约日期" width="120">
        <template #default="{ row }">{{ textOf(row.appointDate) }}</template>
      </el-table-column>
      <el-table-column label="预约时段" width="130">
        <template #default="{ row }">{{ textOf(row.appointTime) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="appointmentStatusTag(row.status)">{{ appointmentStatusLabel(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="审核说明" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ textOf(row.auditReason) }}</template>
      </el-table-column>
      <el-table-column label="提交时间" width="170">
        <template #default="{ row }">{{ textOf(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="success" :disabled="row.status !== 'PENDING_STATION_CONFIRMATION'" @click="confirm(row)">确认</el-button>
          <el-button size="small" type="danger" :disabled="row.status !== 'PENDING_STATION_CONFIRMATION'" @click="reject(row)">拒绝</el-button>
          <el-button size="small" type="primary" :disabled="row.status !== 'CONFIRMED'" @click="finish(row)">完成</el-button>
          <el-button size="small" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="admin-pager">
      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="load"
        @size-change="search"
      />
    </div>

    <el-dialog v-model="detailVisible" title="预约详情" width="560px">
      <el-descriptions v-if="detail" :column="1" border size="small">
        <el-descriptions-item label="编号">{{ textOf(detail.id) }}</el-descriptions-item>
        <el-descriptions-item label="预约人">{{ textOf(detail.userName) }}</el-descriptions-item>
        <el-descriptions-item label="服务驿站">{{ textOf(detail.stationName) }}</el-descriptions-item>
        <el-descriptions-item label="沟通事项">{{ textOf(detail.serviceName) }}</el-descriptions-item>
        <el-descriptions-item label="预约日期">{{ textOf(detail.appointDate) }}</el-descriptions-item>
        <el-descriptions-item label="预约时段">{{ textOf(detail.appointTime) }}</el-descriptions-item>
        <el-descriptions-item label="当前状态">{{ appointmentStatusLabel(detail) }}</el-descriptions-item>
        <el-descriptions-item label="审核说明">{{ textOf(detail.auditReason) }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ textOf(detail.createTime) }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { auditAppointment, finishAppointment, getAdminAppointments } from "../../api/workflow";
import { errorMessage } from "../../api/http";
import { appointmentStatusLabel, appointmentStatusTag, textOf } from "./admin-shared";

const statusOptions = [
  { value: "PENDING_STATION_CONFIRMATION", label: "待驿站确认" },
  { value: "CONFIRMED", label: "已确认" },
  { value: "REJECTED", label: "已拒绝" },
  { value: "CANCELLED", label: "已取消" },
  { value: "FINISHED", label: "已完成" }
];

const loading = ref(false);
const rows = ref([]);
const total = ref(0);
const detailVisible = ref(false);
const detail = ref(null);
const query = reactive({ current: 1, size: 10, status: "", keyword: "" });

async function load() {
  loading.value = true;
  try {
    const page = await getAdminAppointments({
      current: query.current,
      size: query.size,
      status: query.status || undefined,
      keyword: query.keyword || undefined
    });
    rows.value = page?.records || [];
    total.value = Number(page?.total) || 0;
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    loading.value = false;
  }
}

function search() {
  query.current = 1;
  load();
}

function reset() {
  query.status = "";
  query.keyword = "";
  search();
}

function openDetail(row) {
  detail.value = row;
  detailVisible.value = true;
}

async function confirm(row) {
  try {
    await ElMessageBox.confirm(
      `确认预约「${textOf(row.serviceName)}」（${textOf(row.appointDate)} ${textOf(row.appointTime)}）吗？`,
      "确认预约",
      { type: "warning", confirmButtonText: "确认", cancelButtonText: "取消" }
    );
  } catch {
    return;
  }
  await audit(row, "CONFIRMED", "");
}

async function reject(row) {
  let input;
  try {
    input = await ElMessageBox.prompt("请填写拒绝原因，将记录到预约的审核说明中。", "拒绝预约", {
      inputPlaceholder: "例如：该时段已约满",
      inputValidator: (value) => (value && value.trim() ? true : "请填写拒绝原因"),
      confirmButtonText: "确认拒绝",
      cancelButtonText: "取消"
    });
  } catch {
    return;
  }
  await audit(row, "REJECTED", input.value.trim());
}

async function audit(row, status, reason) {
  loading.value = true;
  try {
    await auditAppointment({ id: row.id, status, reason });
    ElMessage.success(status === "CONFIRMED" ? "预约已确认" : "预约已拒绝");
    await load();
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    loading.value = false;
  }
}

/** 服务结束后把已确认的预约标记为完成，状态才真正进入终态。 */
async function finish(row) {
  try {
    await ElMessageBox.confirm(
      `确认「${textOf(row.userName)}」的预约（${textOf(row.appointDate)} ${textOf(row.appointTime)}）已完成吗？`,
      "完成预约",
      { type: "warning", confirmButtonText: "标记完成", cancelButtonText: "取消" }
    );
  } catch {
    return;
  }
  loading.value = true;
  try {
    await finishAppointment(row.id);
    ElMessage.success("预约已标记为已完成");
    await load();
  } catch (error) {
    ElMessage.error(errorMessage(error, "操作失败"));
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.admin-panel {
  padding: 16px 18px 18px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 6px;
}

.admin-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.admin-toolbar-search { width: 300px; }
.admin-toolbar-select { width: 160px; }
.admin-toolbar-total { margin-left: auto; color: var(--muted); font-size: 13px; }

.admin-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
