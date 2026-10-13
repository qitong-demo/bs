<template>
  <section class="admin-panel">
    <div class="admin-toolbar">
      <el-input
        v-model="query.keyword"
        class="admin-toolbar-search"
        clearable
        placeholder="搜索诉求标题、类型或区域"
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
      <span class="admin-toolbar-total">共 {{ total }} 条诉求</span>
    </div>

    <el-table :data="rows" v-loading="loading" stripe empty-text="暂无诉求记录">
      <el-table-column prop="id" label="编号" width="80" />
      <el-table-column label="诉求标题" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ textOf(row.title) }}</template>
      </el-table-column>
      <el-table-column label="类型" width="110">
        <template #default="{ row }">{{ textOf(row.type) }}</template>
      </el-table-column>
      <el-table-column label="区域" width="110">
        <template #default="{ row }">{{ textOf(row.area) }}</template>
      </el-table-column>
      <el-table-column label="提交人" width="120">
        <template #default="{ row }">
          <span>{{ textOf(row.userName) }}</span>
          <el-tag v-if="row.anonymous" size="small" type="info" effect="plain">匿名</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="受理边界" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ textOf(row.handlingBoundary) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="appealStatusTag(row.status)">{{ appealStatusLabel(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="处理人" width="120">
        <template #default="{ row }">{{ textOf(row.handlerName) }}</template>
      </el-table-column>
      <el-table-column label="受理方" width="130">
        <template #default="{ row }">{{ textOf(row.receiverName) }}</template>
      </el-table-column>
      <el-table-column label="提交时间" width="170">
        <template #default="{ row }">{{ textOf(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button size="small" :disabled="!canHandle(row)" @click="openAssign(row)">分派</el-button>
          <el-button size="small" type="primary" :disabled="!canHandle(row)" @click="openHandle(row, 'PROCESSING')">处理中</el-button>
          <el-button size="small" type="success" :disabled="!canHandle(row)" @click="openHandle(row, 'RESOLVED')">办结</el-button>
          <el-button size="small" type="info" :disabled="!canHandle(row)" @click="openHandle(row, 'CLOSED')">关闭</el-button>
          <el-button size="small" @click="openTimeline(row)">记录</el-button>
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

    <el-dialog v-model="assignVisible" title="分派诉求" width="520px">
      <el-descriptions v-if="current" :column="1" border size="small" class="admin-dialog-meta">
        <el-descriptions-item label="诉求标题">{{ textOf(current.title) }}</el-descriptions-item>
        <el-descriptions-item label="当前状态">{{ appealStatusLabel(current) }}</el-descriptions-item>
        <el-descriptions-item label="受理方">{{ textOf(current.receiverName) }}</el-descriptions-item>
        <el-descriptions-item label="受理边界">{{ textOf(current.handlingBoundary) }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-position="top">
        <el-form-item label="分派说明">
          <el-input v-model="assignContent" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="填写处理人、办理要求等（可留空，后端默认记录为已分派处理人）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAssign">提交分派</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="handleVisible" :title="handleTitle" width="520px">
      <el-descriptions v-if="current" :column="1" border size="small" class="admin-dialog-meta">
        <el-descriptions-item label="诉求标题">{{ textOf(current.title) }}</el-descriptions-item>
        <el-descriptions-item label="当前状态">{{ appealStatusLabel(current) }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-position="top">
        <el-form-item :label="handleContentLabel" :required="handleStatus === 'RESOLVED'">
          <el-input
            v-model="handleContent"
            type="textarea"
            :rows="4"
            maxlength="300"
            show-word-limit
            :placeholder="handleStatus === 'RESOLVED' ? '这段话会直接展示给用户，请用你自己的话写。例如：举手之劳，不必客气，欢迎常来。' : '填写当前进展，用户能看到'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitHandle">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="timelineVisible" title="处理记录" width="620px">
      <div v-loading="timelineLoading">
        <el-descriptions v-if="current" :column="2" border size="small" class="admin-dialog-meta">
          <el-descriptions-item label="诉求标题">{{ textOf(current.title) }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">{{ appealStatusLabel(current) }}</el-descriptions-item>
          <el-descriptions-item label="提交人">{{ textOf(current.userName) }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ textOf(current.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="办结时间">{{ textOf(current.finishTime) }}</el-descriptions-item>
          <el-descriptions-item label="受理方">{{ textOf(current.receiverName) }}</el-descriptions-item>
          <el-descriptions-item label="诉求内容" :span="2">{{ textOf(current.content) }}</el-descriptions-item>
        </el-descriptions>

        <el-timeline v-if="timeline.length" class="admin-timeline">
          <el-timeline-item
            v-for="(item, index) in timeline"
            :key="`${textOf(item.createTime)}-${index}`"
            :timestamp="textOf(item.createTime)"
            placement="top"
          >
            <strong>{{ textOf(item.actionLabel || item.action) }}</strong>
            <p>{{ textOf(item.content) }}</p>
            <p class="admin-timeline-handler">操作人：{{ textOf(item.handlerName) }}</p>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else-if="!timelineLoading" description="暂无处理记录" />
      </div>
      <template #footer>
        <el-button @click="timelineVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { assignAppeal, getAdminAppeals, getAppealDetail, handleAppeal } from "../../api/workflow";
import { errorMessage } from "../../api/http";
import { appealStatusLabel, appealStatusTag, textOf } from "./admin-shared";

const statusOptions = [
  { value: "PENDING_ACCEPTANCE", label: "待受理" },
  { value: "ASSIGNED", label: "已分派" },
  { value: "PROCESSING", label: "处理中" },
  { value: "RESOLVED", label: "已办结" },
  { value: "CLOSED", label: "已关闭" }
];

const HANDLE_TITLES = { PROCESSING: "给用户回复进展", RESOLVED: "回复并结束", CLOSED: "回复并关闭" };
// 这些字段是**要展示给用户看的话**，不是内部处理说明。
// 驿站想感谢骑手的反馈，就得能自己打出想说的话，而不是从结论选项里挑一个。
const HANDLE_LABELS = { PROCESSING: "给用户的进展回复", RESOLVED: "给用户的回复（必填）", CLOSED: "给用户的说明" };

const loading = ref(false);
const submitting = ref(false);
const rows = ref([]);
const total = ref(0);
const query = reactive({ current: 1, size: 10, status: "", keyword: "" });

const current = ref(null);
const assignVisible = ref(false);
const assignContent = ref("");
const handleVisible = ref(false);
const handleStatus = ref("PROCESSING");
const handleContent = ref("");
const timelineVisible = ref(false);
const timelineLoading = ref(false);
const timeline = ref([]);

const handleTitle = computed(() => HANDLE_TITLES[handleStatus.value] || "处理诉求");
const handleContentLabel = computed(() => HANDLE_LABELS[handleStatus.value] || "给用户的回复");

function canHandle(row) {
  return ["PENDING_ACCEPTANCE", "ASSIGNED", "PROCESSING"].includes(row?.status);
}

async function load() {
  loading.value = true;
  try {
    const page = await getAdminAppeals({
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

function openAssign(row) {
  current.value = row;
  assignContent.value = "";
  assignVisible.value = true;
}

async function submitAssign() {
  if (!current.value) return;
  submitting.value = true;
  try {
    await assignAppeal({ id: current.value.id, content: assignContent.value.trim() || undefined });
    ElMessage.success("诉求已分派");
    assignVisible.value = false;
    await load();
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    submitting.value = false;
  }
}

function openHandle(row, status) {
  current.value = row;
  handleStatus.value = status;
  handleContent.value = "";
  handleVisible.value = true;
}

async function submitHandle() {
  if (!current.value) return;
  const content = handleContent.value.trim();
  if (handleStatus.value === "RESOLVED" && !content) {
    ElMessage.warning("办结诉求必须填写处理说明");
    return;
  }
  submitting.value = true;
  try {
    await handleAppeal({ id: current.value.id, status: handleStatus.value, content: content || undefined });
    ElMessage.success(`诉求状态已更新为${HANDLE_TITLES[handleStatus.value] || "已处理"}`);
    handleVisible.value = false;
    await load();
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    submitting.value = false;
  }
}

async function openTimeline(row) {
  current.value = row;
  timeline.value = [];
  timelineVisible.value = true;
  timelineLoading.value = true;
  try {
    const detail = await getAppealDetail(row.id);
    current.value = detail || row;
    timeline.value = Array.isArray(detail?.records) ? detail.records : [];
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    timelineLoading.value = false;
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

.admin-dialog-meta { margin-bottom: 16px; }

.admin-timeline { padding-left: 4px; }

.admin-timeline strong { color: #253042; font-size: 14px; }

.admin-timeline p {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.6;
}

.admin-timeline-handler { color: #87909f; }
</style>
