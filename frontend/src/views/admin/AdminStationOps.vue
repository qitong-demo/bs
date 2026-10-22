<template>
  <section class="admin-panel">
    <div class="admin-toolbar">
      <el-select v-model="selectedStationId" class="admin-toolbar-search" placeholder="选择我负责的驿站">
        <el-option v-for="item in stations" :key="item.id" :label="item.stationName" :value="item.id" />
      </el-select>
      <el-tag v-if="selectedStation" :type="stationStatusTag(selectedStation.status)">
        {{ stationStatusLabel(selectedStation.status) }}
      </el-tag>
      <el-tag v-if="selectedStation?.suspendedFrom" type="warning">暂停服务中</el-tag>
      <el-button
        v-if="selectedStation && !selectedStation.suspendedFrom"
        type="warning"
        @click="openSuspend"
      >暂停服务</el-button>
      <el-button v-if="selectedStation?.suspendedFrom" type="success" @click="resume">恢复服务</el-button>
      <span class="admin-toolbar-total">负责 {{ stations.length }} 个驿站</span>
    </div>

    <el-alert
      v-if="selectedStation?.suspendedFrom"
      class="ops-alert"
      type="warning"
      :closable="false"
      show-icon
      :title="`暂停时间：${selectedStation.suspendedFrom} 至 ${selectedStation.suspendedTo}，原因：${selectedStation.suspendReason || '未填写'}`"
    />

    <div class="ops-grid">
      <article class="ops-card">
        <h3>提交变更申请</h3>
        <p class="ops-hint">位置变更必须走审批；营业时间、电话、地址提交后由管理员核准生效。</p>
        <el-form label-position="top">
          <el-form-item label="驿站">
            <el-select v-model="changeForm.stationId" class="full" placeholder="选择驿站">
              <el-option v-for="item in stations" :key="item.id" :label="item.stationName" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="变更项">
            <el-select v-model="changeForm.type" class="full">
              <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item :label="valueLabel">
            <el-input v-model="changeForm.newValue" :placeholder="valuePlaceholder" />
          </el-form-item>
          <el-form-item label="申请理由">
            <el-input v-model="changeForm.reason" type="textarea" :rows="3" placeholder="审批人需要据此判断" />
          </el-form-item>
          <el-button type="primary" :loading="submitting" @click="submitChange">提交申请</el-button>
        </el-form>
      </article>

      <article class="ops-card">
        <h3>我的申请</h3>
        <el-table :data="changes" v-loading="loading" size="small" empty-text="还没有提交过变更申请">
          <el-table-column prop="id" label="编号" width="70" />
          <el-table-column label="驿站" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ textOf(row.stationName) }}</template>
          </el-table-column>
          <el-table-column label="变更项" width="90">
            <template #default="{ row }">{{ textOf(row.typeLabel) }}</template>
          </el-table-column>
          <el-table-column label="新值" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ textOf(row.newValue) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="92">
            <template #default="{ row }">
              <el-tag :type="changeTagType(row.status)" size="small">{{ textOf(row.statusLabel) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="审批说明" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ textOf(row.reviewNote) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="86">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'PENDING' && Number(row.applicantId) === currentUserId"
                size="small"
                type="danger"
                plain
                @click="withdraw(row)"
              >撤回</el-button>
            </template>
          </el-table-column>
        </el-table>
      </article>
    </div>

    <el-dialog v-model="suspendVisible" title="暂停服务" width="480px">
      <el-form label-position="top">
        <el-form-item label="开始时间">
          <el-date-picker v-model="suspendForm.from" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" class="full" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="suspendForm.to" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" class="full" />
        </el-form-item>
        <el-form-item label="暂停原因">
          <el-input v-model="suspendForm.reason" type="textarea" :rows="3" placeholder="例如：设备检修、场地装修" />
        </el-form-item>
      </el-form>
      <p class="ops-hint">暂停期间的新预约会被拒绝；已提交的预约不会自动取消，请电话告知预约人。</p>
      <template #footer>
        <el-button @click="suspendVisible = false">取消</el-button>
        <el-button type="warning" :loading="submitting" @click="submitSuspend">确认暂停</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { currentUser } from "../../api/auth";
import { errorMessage } from "../../api/http";
import { getMyStations, resumeStation, suspendStation } from "../../api/station";
import { cancelChange, getMyChanges, submitChange as submitChangeApi } from "../../api/station-change";
import { stationStatusLabel, stationStatusTag, textOf } from "./admin-shared";

const loading = ref(false);
const submitting = ref(false);
const stations = ref([]);
const changes = ref([]);
const selectedStationId = ref(null);
const currentUserId = ref(0);
const suspendVisible = ref(false);

const selectedStation = computed(() => stations.value.find((item) => item.id === selectedStationId.value) || null);

const typeOptions = [
  { value: "PHONE", label: "联系电话" },
  { value: "BUSINESS_HOURS", label: "营业时间" },
  { value: "ADDRESS", label: "地址" },
  { value: "LOCATION", label: "位置（经度,纬度）" }
];

const changeForm = reactive({ stationId: null, type: "PHONE", newValue: "", reason: "" });
const suspendForm = reactive({ from: "", to: "", reason: "" });

const valueLabel = computed(() => typeOptions.find((item) => item.value === changeForm.type)?.label || "变更后的值");
const valuePlaceholder = computed(() => {
  if (changeForm.type === "LOCATION") return "例如：120.155100,30.274100";
  if (changeForm.type === "BUSINESS_HOURS") return "例如：08:00-20:00";
  if (changeForm.type === "PHONE") return "例如：0571-88886666";
  return "填写变更后的完整地址";
});

function changeTagType(status) {
  if (status === "APPROVED") return "success";
  if (status === "REJECTED") return "danger";
  if (status === "CANCELLED") return "info";
  return "warning";
}

function formatDateTime(date) {
  const pad = (value) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}:00`;
}

async function load() {
  loading.value = true;
  try {
    const [myStations, myChanges] = await Promise.all([getMyStations(), getMyChanges()]);
    stations.value = Array.isArray(myStations) ? myStations : [];
    changes.value = Array.isArray(myChanges) ? myChanges : [];
    if (!selectedStationId.value && stations.value.length) {
      selectedStationId.value = stations.value[0].id;
    }
    if (!changeForm.stationId && stations.value.length) {
      changeForm.stationId = stations.value[0].id;
    }
  } catch (error) {
    ElMessage.error(errorMessage(error, "驿站信息加载失败"));
  } finally {
    loading.value = false;
  }
}

async function submitChange() {
  if (!changeForm.stationId || !changeForm.newValue.trim() || !changeForm.reason.trim()) {
    ElMessage.warning("请选择驿站，并填写变更后的值与申请理由");
    return;
  }
  submitting.value = true;
  try {
    await submitChangeApi({
      stationId: changeForm.stationId,
      type: changeForm.type,
      newValue: changeForm.newValue.trim(),
      reason: changeForm.reason.trim()
    });
    ElMessage.success("变更申请已提交，等待管理员审批");
    changeForm.newValue = "";
    changeForm.reason = "";
    await load();
  } catch (error) {
    ElMessage.error(errorMessage(error, "提交失败"));
  } finally {
    submitting.value = false;
  }
}

// 接口函数已在 import 时别名为 submitChangeApi，避免与本地提交函数同名。

async function withdraw(row) {
  try {
    await ElMessageBox.confirm(`确认撤回「${textOf(row.typeLabel)}」变更申请吗？`, "撤回申请", { type: "warning" });
  } catch {
    return;
  }
  try {
    await cancelChange(row.id);
    ElMessage.success("申请已撤回");
    await load();
  } catch (error) {
    ElMessage.error(errorMessage(error, "撤回失败"));
  }
}

function openSuspend() {
  const now = new Date();
  const from = new Date(now.getTime() + 60 * 60 * 1000);
  const to = new Date(now.getTime() + 24 * 60 * 60 * 1000);
  suspendForm.from = formatDateTime(from);
  suspendForm.to = formatDateTime(to);
  suspendForm.reason = "";
  suspendVisible.value = true;
}

async function submitSuspend() {
  if (!selectedStationId.value) return;
  if (!suspendForm.from || !suspendForm.to) {
    ElMessage.warning("请选择暂停开始和结束时间");
    return;
  }
  if (suspendForm.from >= suspendForm.to) {
    ElMessage.warning("开始时间必须早于结束时间");
    return;
  }
  if (!suspendForm.reason.trim()) {
    ElMessage.warning("请填写暂停原因，用户需要知道为什么约不上");
    return;
  }
  submitting.value = true;
  try {
    await suspendStation(selectedStationId.value, {
      from: suspendForm.from,
      to: suspendForm.to,
      reason: suspendForm.reason.trim()
    });
    ElMessage.success("已设置暂停服务");
    suspendVisible.value = false;
    await load();
  } catch (error) {
    ElMessage.error(errorMessage(error, "设置失败"));
  } finally {
    submitting.value = false;
  }
}

async function resume() {
  if (!selectedStationId.value) return;
  try {
    await ElMessageBox.confirm("确认恢复服务？恢复后将重新接收新预约。", "恢复服务", { type: "warning" });
  } catch {
    return;
  }
  try {
    await resumeStation(selectedStationId.value);
    ElMessage.success("已恢复服务");
    await load();
  } catch (error) {
    ElMessage.error(errorMessage(error, "操作失败"));
  }
}

onMounted(async () => {
  await Promise.all([
    load(),
    currentUser().then((user) => {
      currentUserId.value = Number(user?.id) || 0;
    }).catch(() => {
      currentUserId.value = 0;
    })
  ]);
});
</script>

<style scoped>
.admin-panel { padding: 16px 18px 18px; background: #fff; border: 1px solid var(--line); border-radius: 6px; }
.admin-toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.admin-toolbar-search { width: 260px; }
.admin-toolbar-total { margin-left: auto; color: var(--muted); font-size: 13px; }
.ops-alert { margin-bottom: 14px; }
.ops-grid { display: grid; grid-template-columns: minmax(280px, 0.85fr) minmax(0, 1.4fr); gap: 16px; align-items: start; }
.ops-card { border: 1px solid var(--line); border-radius: 6px; padding: 14px 16px; min-width: 0; min-height: 260px; }
.ops-card h3 { margin: 0 0 6px; font-size: 15px; }
.ops-hint { margin: 0 0 12px; color: var(--muted); font-size: 12px; line-height: 1.6; }
@media (max-width: 1080px) { .ops-grid { grid-template-columns: 1fr; } }
</style>
