<template>
  <section class="admin-panel">
    <el-radio-group v-if="showSectionSwitch" v-model="activeSection" class="admin-section-switch" @change="onSectionChange">
      <el-radio-button value="activities">活动列表</el-radio-button>
      <el-radio-button value="signups">报名记录</el-radio-button>
    </el-radio-group>

    <template v-if="activeSection === 'activities'">
      <div class="admin-toolbar">
        <el-input
          v-model="query.keyword"
          class="admin-toolbar-search"
          clearable
          placeholder="搜索活动名称或地址"
          @keyup.enter="search"
          @clear="search"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="query.status" class="admin-toolbar-select" clearable placeholder="全部状态" @change="search">
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="草稿" value="DRAFT" />
        </el-select>
        <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
        <el-button @click="reset"><el-icon><RefreshLeft /></el-icon>重置</el-button>
        <el-button type="success" class="admin-toolbar-action" @click="openCreate"><el-icon><Plus /></el-icon>新增活动</el-button>
        <span class="admin-toolbar-total">共 {{ total }} 场活动</span>
      </div>

      <el-table :data="rows" v-loading="loading" stripe empty-text="暂无活动数据">
        <el-table-column prop="id" label="编号" width="80" />
        <el-table-column label="活动名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ textOf(row.title) }}</template>
        </el-table-column>
        <el-table-column label="举办驿站" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ textOf(row.stationName) }}</template>
        </el-table-column>
        <el-table-column label="活动时间" width="170">
          <template #default="{ row }">{{ textOf(row.activityTime) }}</template>
        </el-table-column>
        <el-table-column label="活动地点" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ textOf(row.address) }}</template>
        </el-table-column>
        <el-table-column label="名额" width="130">
          <template #default="{ row }">
            <span>{{ numberText(row.signedCount) }} / {{ numberText(row.maxCount) }}</span>
            <el-tag v-if="Number(row.remaining) === 0" size="small" type="danger" effect="plain">已满</el-tag>
            <span v-else class="admin-muted">余 {{ numberText(row.remaining) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="contentStatusTag(row.status)">{{ contentStatusLabel(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ textOf(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openPreview(row)">查看</el-button>
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
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
    </template>

    <template v-else>
      <div class="admin-toolbar">
        <span class="admin-toolbar-label">报名记录来自活动报名工作流</span>
        <el-button class="admin-toolbar-action" @click="loadSignups"><el-icon><Refresh /></el-icon>刷新</el-button>
        <span class="admin-toolbar-total">共 {{ signups.length }} 条报名</span>
      </div>
      <el-table :data="signups" v-loading="signupLoading" stripe empty-text="暂无报名记录">
        <el-table-column prop="id" label="编号" width="80" />
        <el-table-column label="活动名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ textOf(row.activityTitle) }}</template>
        </el-table-column>
        <el-table-column label="报名人" width="140">
          <template #default="{ row }">{{ textOf(row.userName) }}</template>
        </el-table-column>
        <el-table-column label="联系电话" width="150">
          <template #default="{ row }">{{ textOf(row.phone) }}</template>
        </el-table-column>
        <el-table-column label="报名状态" width="140">
          <template #default="{ row }">
            <el-tag type="success" effect="plain">{{ textOf(row.statusLabel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="报名时间" width="180">
          <template #default="{ row }">{{ textOf(row.createTime) }}</template>
        </el-table-column>
      </el-table>
    </template>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑活动' : '新增活动'" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="94px">
        <el-form-item label="活动名称" prop="title">
          <el-input v-model="form.title" maxlength="80" show-word-limit placeholder="请输入活动名称" />
        </el-form-item>
        <el-form-item label="举办驿站" prop="stationId">
          <el-select v-model="form.stationId" filterable class="admin-full" placeholder="选择举办驿站">
            <el-option
              v-for="item in stations"
              :key="item.id"
              :label="`${item.stationName}（${item.area || '未填区域'}）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="活动时间" prop="activityTime">
          <el-date-picker
            v-model="form.activityTime"
            type="datetime"
            class="admin-full"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择活动开始时间"
          />
        </el-form-item>
        <el-form-item label="活动地点" prop="address">
          <el-input v-model="form.address" maxlength="120" show-overflow-tooltip placeholder="例如：驿站一楼活动室" />
        </el-form-item>
        <el-form-item label="名额上限" prop="maxCount">
          <el-input-number v-model="form.maxCount" :min="1" :max="9999" />
        </el-form-item>
        <el-form-item label="活动内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="5" maxlength="1000" show-word-limit placeholder="填写活动内容、参与方式等" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="PUBLISHED">已发布</el-radio>
            <el-radio value="DRAFT">草稿</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewVisible" title="活动详情" width="620px">
      <el-descriptions v-if="preview" :column="2" border size="small">
        <el-descriptions-item label="活动名称" :span="2">{{ textOf(preview.title) }}</el-descriptions-item>
        <el-descriptions-item label="举办驿站">{{ textOf(preview.stationName) }}</el-descriptions-item>
        <el-descriptions-item label="活动时间">{{ textOf(preview.activityTime) }}</el-descriptions-item>
        <el-descriptions-item label="活动地点">{{ textOf(preview.address) }}</el-descriptions-item>
        <el-descriptions-item label="名额情况">
          {{ numberText(preview.signedCount) }} / {{ numberText(preview.maxCount) }}（余 {{ numberText(preview.remaining) }}）
        </el-descriptions-item>
        <el-descriptions-item label="状态">{{ contentStatusLabel(preview) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ textOf(preview.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="活动内容" :span="2">{{ textOf(preview.content) }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  createActivity,
  deleteActivity,
  getAdminActivityPage,
  updateActivity
} from "../../api/content";
import { getAdminSignups } from "../../api/workflow";
import { getStationPage } from "../../api/station";
import { errorMessage } from "../../api/http";
import { contentStatusLabel, contentStatusTag, numberText, textOf } from "./admin-shared";

const props = defineProps({
  /** 初始展示的区块：activities（活动列表）或 signups（报名记录）。 */
  section: { type: String, default: "activities" }
});

const activeSection = ref(props.section === "signups" ? "signups" : "activities");
const showSectionSwitch = computed(() => props.section !== "signups");

const loading = ref(false);
const submitting = ref(false);
const rows = ref([]);
const total = ref(0);
const query = reactive({ current: 1, size: 10, status: "", keyword: "" });

const signupLoading = ref(false);
const signups = ref([]);

const stations = ref([]);
const dialogVisible = ref(false);
const editingId = ref(null);
const formRef = ref(null);
const previewVisible = ref(false);
const preview = ref(null);

const form = reactive({
  title: "",
  stationId: null,
  activityTime: "",
  address: "",
  maxCount: 30,
  content: "",
  status: "PUBLISHED"
});

const rules = {
  title: [{ required: true, message: "请填写活动名称", trigger: "blur" }],
  stationId: [{ required: true, message: "请选择举办驿站", trigger: "change" }],
  activityTime: [{ required: true, message: "请选择活动时间", trigger: "change" }],
  maxCount: [{ required: true, message: "请填写名额上限", trigger: "blur" }]
};

async function loadStations() {
  try {
    const page = await getStationPage({ current: 1, size: 100 });
    stations.value = page?.records || [];
  } catch (error) {
    ElMessage.error(errorMessage(error));
  }
}

async function load() {
  loading.value = true;
  try {
    const page = await getAdminActivityPage({
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

async function loadSignups() {
  signupLoading.value = true;
  try {
    const data = await getAdminSignups();
    signups.value = Array.isArray(data) ? data : [];
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    signupLoading.value = false;
  }
}

function onSectionChange(section) {
  if (section === "signups" && !signups.value.length) {
    loadSignups();
  }
}

function openCreate() {
  editingId.value = null;
  Object.assign(form, {
    title: "",
    stationId: stations.value.length ? stations.value[0].id : null,
    activityTime: "",
    address: "",
    maxCount: 30,
    content: "",
    status: "PUBLISHED"
  });
  formRef.value?.clearValidate();
  dialogVisible.value = true;
}

function openEdit(row) {
  editingId.value = row.id;
  Object.assign(form, {
    title: row.title || "",
    stationId: row.stationId || null,
    activityTime: row.activityTime || "",
    address: row.address || "",
    maxCount: Number(row.maxCount) > 0 ? Number(row.maxCount) : 1,
    content: row.content || "",
    status: row.status || "PUBLISHED"
  });
  formRef.value?.clearValidate();
  dialogVisible.value = true;
}

function openPreview(row) {
  preview.value = row;
  previewVisible.value = true;
}

async function submit() {
  try {
    await formRef.value.validate();
  } catch {
    return;
  }
  submitting.value = true;
  try {
    const payload = {
      title: form.title,
      stationId: form.stationId,
      activityTime: form.activityTime,
      address: form.address,
      maxCount: form.maxCount,
      content: form.content,
      status: form.status
    };
    if (editingId.value) {
      await updateActivity(editingId.value, payload);
      ElMessage.success("活动已更新");
    } else {
      await createActivity(payload);
      ElMessage.success("活动已新增");
    }
    dialogVisible.value = false;
    await search();
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    submitting.value = false;
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除活动「${textOf(row.title)}」吗？已产生的报名记录不会被删除。`,
      "删除活动",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" }
    );
  } catch {
    return;
  }
  loading.value = true;
  try {
    await deleteActivity(row.id);
    ElMessage.success("活动已删除");
    await load();
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  await Promise.all([loadStations(), load()]);
  if (activeSection.value === "signups") {
    await loadSignups();
  }
});
</script>

<style scoped>
.admin-panel {
  padding: 16px 18px 18px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 6px;
}

.admin-section-switch { margin-bottom: 14px; }

.admin-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.admin-toolbar-label { color: var(--muted); font-size: 13px; }
.admin-toolbar-search { width: 260px; }
.admin-toolbar-select { width: 150px; }
.admin-toolbar-action { margin-left: auto; }
.admin-toolbar-total { color: var(--muted); font-size: 13px; }

.admin-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.admin-full { width: 100%; }
.admin-muted { margin-left: 6px; color: var(--muted); font-size: 12px; }
</style>
