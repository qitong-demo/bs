<template>
  <section class="admin-panel">
    <div class="admin-toolbar">
      <el-input
        v-model="query.keyword"
        class="admin-toolbar-search"
        clearable
        placeholder="搜索驿站名称或地址"
        @keyup.enter="search"
        @clear="search"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-input v-model="query.area" class="admin-toolbar-select" clearable placeholder="所属区域" @keyup.enter="search" @clear="search" />
      <el-select v-model="query.status" class="admin-toolbar-select" clearable placeholder="全部状态" @change="search">
        <el-option label="营业中" value="OPEN" />
        <el-option label="已停用" value="CLOSED" />
        <el-option label="已删除" value="DELETED" />
      </el-select>
      <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
      <el-button @click="reset"><el-icon><RefreshLeft /></el-icon>重置</el-button>
      <el-button type="success" class="admin-toolbar-action" @click="openCreate"><el-icon><Plus /></el-icon>新增驿站</el-button>
      <span class="admin-toolbar-total">共 {{ total }} 个驿站</span>
    </div>

    <p class="station-source-note">
      驿站名录来自公开来源（政府公告与官方媒体发布的开放名单）。数据状态说明：
      <b>来源可核对</b>——名称、地址与所属区域可从来源逐条回溯；
      <b>批次级信息</b>——名称与地址来自来源名录，服务设施为来源对该批驿站的统一描述；
      <b>后台录入</b>——由管理员录入，暂无公开来源。
      来源发布时这些点位均在开放状态，是否持续开放请在使用前重新核实。当前筛选共 {{ total }} 条记录。
    </p>

    <el-table :data="rows" v-loading="loading" stripe empty-text="暂无驿站数据">
      <el-table-column prop="id" label="编号" width="80" />
      <el-table-column label="驿站名称" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ textOf(row.stationName) }}</template>
      </el-table-column>
      <el-table-column label="类型" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.stationType" effect="light" class="admin-tag">{{ row.stationType }}</el-tag>
          <span v-else class="admin-muted">未分类</span>
        </template>
      </el-table-column>
      <el-table-column label="所属区域" width="110">
        <template #default="{ row }">{{ textOf(row.area) }}</template>
      </el-table-column>
      <el-table-column label="详细地址" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ textOf(row.address) }}</template>
      </el-table-column>
      <el-table-column label="联系电话" width="140">
        <template #default="{ row }">{{ textOf(row.contactPhone) }}</template>
      </el-table-column>
      <el-table-column label="开放时间" width="140">
        <template #default="{ row }">{{ textOf(row.openTime) }}</template>
      </el-table-column>
      <el-table-column label="服务项目" min-width="180">
        <template #default="{ row }">
          <el-tag v-for="service in (row.services || [])" :key="service" size="small" effect="light" class="admin-tag">{{ service }}</el-tag>
          <span v-if="!(row.services || []).length" class="admin-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="stationStatusTag(row.status)">{{ stationStatusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="数据来源" min-width="230">
        <template #default="{ row }">
          <el-tag :type="sourceStatusTag(sourceOf(row))" effect="light" class="admin-tag">
            {{ sourceStatusLabel(sourceOf(row)) }}
          </el-tag>
          <div class="station-source-cell">
            <span class="admin-muted">{{ textOf(sourceOf(row).sourceName) }}</span>
            <a
              v-if="sourceOf(row).sourceUrl"
              class="station-source-link"
              :href="sourceOf(row).sourceUrl"
              target="_blank"
              rel="noopener"
            >{{ sourceOf(row).sourceUrl }}</a>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ textOf(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="380" fixed="right">
        <template #default="{ row }">
          <el-button size="small" :disabled="row.status === 'DELETED'" @click="openEdit(row)">编辑</el-button>
          <el-button
            size="small"
            :type="row.status === 'OPEN' ? 'warning' : 'success'"
            :disabled="row.status === 'DELETED'"
            @click="toggleStatus(row)"
          >
            {{ row.status === "OPEN" ? "停用" : "恢复营业" }}
          </el-button>
          <el-button
            v-if="!row.suspendedFrom"
            size="small"
            type="warning"
            plain
            :disabled="row.status === 'DELETED'"
            @click="openSuspend(row)"
          >暂停服务</el-button>
          <el-button v-else size="small" type="success" plain @click="resume(row)">恢复服务</el-button>
          <el-button size="small" type="danger" :disabled="row.status === 'DELETED'" @click="remove(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑驿站' : '新增驿站'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="94px">
        <el-form-item label="驿站名称" prop="stationName">
          <el-input v-model="form.stationName" maxlength="60" show-word-limit placeholder="例如：西湖区文新友好驿站" />
        </el-form-item>
        <el-form-item label="所属区域" prop="area">
          <el-input v-model="form.area" maxlength="30" placeholder="例如：西湖区" />
        </el-form-item>
        <el-form-item label="驿站类型" prop="stationType">
          <el-select
            v-model="form.stationType"
            class="full"
            filterable
            allow-create
            default-first-option
            clearable
            placeholder="可选择或直接输入自定义类型"
          >
            <el-option label="小哥驿站" value="小哥驿站" />
            <el-option label="城管驿站" value="城管驿站" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="详细地址" prop="address">
          <el-input v-model="form.address" type="textarea" :rows="2" maxlength="120" show-word-limit placeholder="街道、门牌号等" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="form.contactPhone" maxlength="20" placeholder="例如：0571-88888888" />
        </el-form-item>
        <el-form-item label="开放时间" prop="openTime">
          <el-input v-model="form.openTime" maxlength="40" placeholder="例如：08:00-20:00" />
        </el-form-item>
        <el-form-item label="营业状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="OPEN">营业中</el-radio>
            <el-radio value="CLOSED">已停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <div v-if="editingId" class="station-source-block">
        <div class="station-source-head">
          <span>数据来源</span>
          <el-tag :type="sourceStatusTag(editingSource)" effect="light">{{ sourceStatusLabel(editingSource) }}</el-tag>
        </div>
        <el-alert
          v-if="isManualSource(editingSource)"
          class="station-source-hint"
          type="info"
          :closable="false"
          show-icon
        >后台录入数据，无公开来源</el-alert>
        <dl class="station-source-list">
          <div><dt>来源名称</dt><dd>{{ textOf(editingSource.sourceName) }}</dd></div>
          <div>
            <dt>来源地址</dt>
            <dd>
              <a
                v-if="editingSource.sourceUrl"
                class="station-source-link"
                :href="editingSource.sourceUrl"
                target="_blank"
                rel="noopener"
              >{{ editingSource.sourceUrl }}</a>
              <span v-else>—</span>
            </dd>
          </div>
          <div><dt>发布日期</dt><dd>{{ textOf(editingSource.sourcePublishedAt) }}</dd></div>
          <div><dt>核对日期</dt><dd>{{ textOf(editingSource.verifiedAt) }}</dd></div>
          <div><dt>补充说明</dt><dd>{{ textOf(editingSource.dataNote) }}</dd></div>
        </dl>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="suspendVisible" title="暂停服务" width="460px">
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
      <p class="station-source-note">暂停期间的新预约会被拒绝；已提交的预约不会自动取消。</p>
      <template #footer>
        <el-button @click="suspendVisible = false">取消</el-button>
        <el-button type="warning" :loading="submitting" @click="submitSuspend">确认暂停</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  createStation,
  deleteStation,
  getAdminStationPage,
  resumeStation,
  suspendStation,
  updateStation,
  updateStationStatus
} from "../../api/station";
import { errorMessage } from "../../api/http";
import { stationStatusLabel, stationStatusTag, textOf } from "./admin-shared";

const loading = ref(false);
const submitting = ref(false);
const rows = ref([]);
const total = ref(0);
const query = reactive({ current: 1, size: 10, area: "", keyword: "", status: "" });

const dialogVisible = ref(false);
const editingId = ref(null);
const suspendVisible = ref(false);
const suspendTarget = ref(null);
const suspendForm = reactive({ from: "", to: "", reason: "" });
const formRef = ref(null);
const form = reactive({ stationName: "", area: "", stationType: "", address: "", contactPhone: "", openTime: "", status: "OPEN" });
/** 编辑时展示的数据来源信息，新增时为空对象（后台录入，无公开来源）。 */
const editingSource = ref({});
const rules = {
  stationName: [{ required: true, message: "请填写驿站名称", trigger: "blur" }],
  area: [{ required: true, message: "请填写所属区域", trigger: "blur" }],
  address: [{ required: true, message: "请填写详细地址", trigger: "blur" }]
};

/** 后端未返回 source 时统一按空对象处理。 */
function sourceOf(row) {
  return row?.source || {};
}

/** 数据来源状态文案：VERIFIED 可核对、PARTIAL 批次级、其余（含 MANUAL、UNKNOWN）为后台录入。 */
function sourceStatusLabel(source) {
  return { VERIFIED: "来源可核对", PARTIAL: "批次级信息" }[source?.dataStatus] || "后台录入";
}

/** 数据来源状态标签色：success / warning / info。 */
function sourceStatusTag(source) {
  return { VERIFIED: "success", PARTIAL: "warning" }[source?.dataStatus] || "info";
}

/** 是否为后台录入（无公开来源）：非 VERIFIED 且非 PARTIAL。 */
function isManualSource(source) {
  return source?.dataStatus !== "VERIFIED" && source?.dataStatus !== "PARTIAL";
}

async function load() {
  loading.value = true;
  try {
    const page = await getAdminStationPage({
      current: query.current,
      size: query.size,
      area: query.area || undefined,
      keyword: query.keyword || undefined,
      status: query.status || undefined
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
  query.area = "";
  query.keyword = "";
  query.status = "";
  search();
}

function openCreate() {
  editingId.value = null;
  editingSource.value = {};
  Object.assign(form, { stationName: "", area: "", stationType: "", address: "", contactPhone: "", openTime: "", status: "OPEN" });
  formRef.value?.clearValidate();
  dialogVisible.value = true;
}

function openEdit(row) {
  editingId.value = row.id;
  editingSource.value = sourceOf(row);
  Object.assign(form, {
    stationName: row.stationName || "",
    area: row.area || "",
    stationType: row.stationType || "",
    address: row.address || "",
    contactPhone: row.contactPhone || "",
    openTime: row.openTime || "",
    status: row.status === "CLOSED" ? "CLOSED" : "OPEN"
  });
  formRef.value?.clearValidate();
  dialogVisible.value = true;
}

async function submit() {
  try {
    await formRef.value.validate();
  } catch {
    return;
  }
  submitting.value = true;
  try {
    const payload = { ...form };
    if (editingId.value) {
      await updateStation(editingId.value, payload);
      ElMessage.success("驿站信息已更新");
    } else {
      await createStation(payload);
      ElMessage.success("驿站已新增");
    }
    dialogVisible.value = false;
    await load();
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    submitting.value = false;
  }
}

async function toggleStatus(row) {
  const nextStatus = row.status === "OPEN" ? "CLOSED" : "OPEN";
  const tip = nextStatus === "CLOSED"
    ? `停用后「${textOf(row.stationName)}」在前台将不再展示，确定停用吗？`
    : `确定将「${textOf(row.stationName)}」恢复为营业中吗？`;
  try {
    await ElMessageBox.confirm(tip, nextStatus === "CLOSED" ? "停用驿站" : "恢复营业", {
      type: "warning",
      confirmButtonText: "确定",
      cancelButtonText: "取消"
    });
  } catch {
    return;
  }
  loading.value = true;
  try {
    await updateStationStatus(row.id, nextStatus);
    ElMessage.success(nextStatus === "CLOSED" ? "驿站已停用" : "驿站已恢复营业");
    await load();
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    loading.value = false;
  }
}

function formatDateTime(date) {
  const pad = (value) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}:00`;
}

function openSuspend(row) {
  suspendTarget.value = row;
  const now = new Date();
  suspendForm.from = formatDateTime(new Date(now.getTime() + 60 * 60 * 1000));
  suspendForm.to = formatDateTime(new Date(now.getTime() + 24 * 60 * 60 * 1000));
  suspendForm.reason = "";
  suspendVisible.value = true;
}

async function submitSuspend() {
  if (!suspendTarget.value) return;
  if (!suspendForm.from || !suspendForm.to || suspendForm.from >= suspendForm.to) {
    ElMessage.warning("请选择正确的暂停开始与结束时间");
    return;
  }
  if (!suspendForm.reason.trim()) {
    ElMessage.warning("请填写暂停原因");
    return;
  }
  submitting.value = true;
  try {
    await suspendStation(suspendTarget.value.id, {
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

async function resume(row) {
  try {
    await ElMessageBox.confirm(`确认恢复「${textOf(row.stationName)}」的服务？`, "恢复服务", { type: "warning" });
  } catch {
    return;
  }
  loading.value = true;
  try {
    await resumeStation(row.id);
    ElMessage.success("已恢复服务");
    await load();
  } catch (error) {
    ElMessage.error(errorMessage(error, "操作失败"));
  } finally {
    loading.value = false;
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `删除「${textOf(row.stationName)}」为逻辑删除：逻辑删除后前台不再展示，管理端仍可查询到该记录。确定继续吗？`,
      "删除驿站",
      { type: "warning", confirmButtonText: "逻辑删除", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" }
    );
  } catch {
    return;
  }
  loading.value = true;
  try {
    await deleteStation(row.id);
    ElMessage.success("驿站已逻辑删除");
    await load();
  } catch (error) {
    ElMessage.error(errorMessage(error));
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

.admin-toolbar-search { width: 260px; }
.admin-toolbar-select { width: 150px; }
.admin-toolbar-action { margin-left: auto; }
.admin-toolbar-total { color: var(--muted); font-size: 13px; }

.admin-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.admin-tag { margin: 2px 4px 2px 0; }

.admin-muted { color: var(--muted); }

.station-source-note {
  margin: 0 0 14px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
}

.station-source-note b {
  color: #374151;
}

.station-source-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12px;
}

.station-source-link {
  color: var(--primary);
  word-break: break-all;
}

.station-source-block {
  margin-top: 6px;
  padding: 12px 14px;
  background: #f8fafc;
  border: 1px solid var(--line);
  border-radius: 6px;
}

.station-source-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #556172;
  font-size: 13px;
}

.station-source-hint {
  margin-top: 10px;
}

.station-source-list {
  margin: 10px 0 0;
}

.station-source-list div {
  display: grid;
  grid-template-columns: 76px 1fr;
  gap: 12px;
  padding: 6px 0;
  border-bottom: 1px solid #edf1f5;
  font-size: 13px;
}

.station-source-list div:last-child {
  border-bottom: 0;
}

.station-source-list dt {
  color: var(--muted);
}

.station-source-list dd {
  margin: 0;
  color: #374151;
  word-break: break-all;
}
</style>
