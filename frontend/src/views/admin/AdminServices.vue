<template>
  <section class="admin-panel">
    <div class="admin-toolbar">
      <span class="admin-toolbar-label">所属驿站</span>
      <el-select
        v-model="stationId"
        class="admin-toolbar-station"
        filterable
        clearable
        placeholder="选择驿站查看服务项目"
        @change="search"
      >
        <el-option
          v-for="item in stations"
          :key="item.id"
          :label="`${item.stationName}（${item.area || '未填区域'}）`"
          :value="item.id"
        />
      </el-select>
      <el-input
        v-model="keyword"
        class="admin-toolbar-search"
        clearable
        placeholder="搜索服务名称"
        @keyup.enter="search"
        @clear="search"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
      <el-button type="success" class="admin-toolbar-action" :disabled="!stationId" @click="openCreate">
        <el-icon><Plus /></el-icon>新增服务项目
      </el-button>
      <span class="admin-toolbar-total">共 {{ total }} 个服务项目</span>
    </div>

    <el-alert
      v-if="!stationId"
      class="admin-hint"
      type="info"
      :closable="false"
      show-icon
      title="请先选择驿站，再查看和维护该驿站的服务项目。"
    />

    <el-table :data="rows" v-loading="loading" stripe empty-text="该驿站暂无服务项目">
      <el-table-column prop="id" label="编号" width="80" />
      <el-table-column label="所属驿站" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ textOf(row.stationName) }}</template>
      </el-table-column>
      <el-table-column label="服务名称" min-width="150">
        <template #default="{ row }">{{ textOf(row.serviceName) }}</template>
      </el-table-column>
      <el-table-column label="服务类型" width="130">
        <template #default="{ row }">{{ textOf(row.serviceType) }}</template>
      </el-table-column>
      <el-table-column label="是否需要预约" width="130">
        <template #default="{ row }">
          <el-tag :type="row.needAppoint ? 'warning' : 'info'" effect="plain">{{ row.needAppoint ? "需预约" : "无需预约" }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="Number(row.status) === 1 ? 'success' : 'info'">{{ Number(row.status) === 1 ? "启用" : "停用" }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ textOf(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button
            size="small"
            :type="Number(row.status) === 1 ? 'warning' : 'success'"
            @click="toggleStatus(row)"
          >
            {{ Number(row.status) === 1 ? "停用" : "启用" }}
          </el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="admin-pager">
      <el-pagination
        v-model:current-page="current"
        v-model:page-size="size"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="load"
        @size-change="search"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑服务项目' : '新增服务项目'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="104px">
        <el-form-item label="所属驿站" prop="stationId">
          <el-select v-model="form.stationId" filterable class="admin-full" placeholder="选择驿站">
            <el-option
              v-for="item in stations"
              :key="item.id"
              :label="`${item.stationName}（${item.area || '未填区域'}）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="服务名称" prop="serviceName">
          <el-input v-model="form.serviceName" maxlength="40" show-word-limit placeholder="例如：免费充电" />
        </el-form-item>
        <el-form-item label="服务类型" prop="serviceType">
          <el-select v-model="form.serviceType" class="admin-full" filterable allow-create placeholder="可选择或输入服务类型">
            <el-option v-for="item in serviceTypes" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="需要预约" prop="needAppoint">
          <el-switch v-model="form.needAppoint" active-text="需预约" inactive-text="无需预约" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  createStationService,
  deleteStationService,
  getStationPage,
  getStationServicePage,
  updateStationService,
  updateStationServiceStatus
} from "../../api/station";
import { errorMessage } from "../../api/http";
import { textOf } from "./admin-shared";

const serviceTypes = ["充电", "热饭", "饮水", "休息", "法律咨询", "车辆检修", "医疗急救", "政策咨询"];

const loading = ref(false);
const submitting = ref(false);
const stations = ref([]);
const stationId = ref(null);
const keyword = ref("");
const current = ref(1);
const size = ref(10);
const total = ref(0);
const rows = ref([]);

const dialogVisible = ref(false);
const editingId = ref(null);
const formRef = ref(null);
const form = reactive({ stationId: null, serviceName: "", serviceType: "", needAppoint: false, status: 1 });
const rules = {
  stationId: [{ required: true, message: "请选择所属驿站", trigger: "change" }],
  serviceName: [{ required: true, message: "请填写服务名称", trigger: "blur" }]
};

async function loadStations() {
  try {
    const page = await getStationPage({ current: 1, size: 100 });
    stations.value = page?.records || [];
    if (!stationId.value && stations.value.length) {
      stationId.value = stations.value[0].id;
    }
  } catch (error) {
    ElMessage.error(errorMessage(error));
  }
}

async function load() {
  if (!stationId.value) {
    rows.value = [];
    total.value = 0;
    return;
  }
  loading.value = true;
  try {
    const page = await getStationServicePage({
      stationId: stationId.value,
      current: current.value,
      size: size.value,
      keyword: keyword.value || undefined
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
  current.value = 1;
  load();
}

function openCreate() {
  editingId.value = null;
  Object.assign(form, {
    stationId: stationId.value,
    serviceName: "",
    serviceType: "",
    needAppoint: false,
    status: 1
  });
  formRef.value?.clearValidate();
  dialogVisible.value = true;
}

function openEdit(row) {
  editingId.value = row.id;
  Object.assign(form, {
    stationId: row.stationId,
    serviceName: row.serviceName || "",
    serviceType: row.serviceType || "",
    needAppoint: Boolean(row.needAppoint),
    status: Number(row.status) === 0 ? 0 : 1
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
    const payload = {
      stationId: form.stationId,
      serviceName: form.serviceName,
      serviceType: form.serviceType,
      needAppoint: form.needAppoint,
      status: form.status
    };
    if (editingId.value) {
      await updateStationService(editingId.value, payload);
      ElMessage.success("服务项目已更新");
    } else {
      await createStationService(payload);
      ElMessage.success("服务项目已新增");
    }
    dialogVisible.value = false;
    await search();
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    submitting.value = false;
  }
}

async function toggleStatus(row) {
  const next = Number(row.status) === 1 ? 0 : 1;
  loading.value = true;
  try {
    await updateStationServiceStatus(row.id, next);
    ElMessage.success(next === 1 ? "服务项目已启用" : "服务项目已停用");
    await load();
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    loading.value = false;
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除服务项目「${textOf(row.serviceName)}」吗？删除后该服务不再对外展示。`,
      "删除服务项目",
      { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" }
    );
  } catch {
    return;
  }
  loading.value = true;
  try {
    await deleteStationService(row.id);
    ElMessage.success("服务项目已删除");
    await load();
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  await loadStations();
  if (stationId.value) await load();
});
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
  flex-wrap: wrap;
}

.admin-toolbar-label { color: #4b5563; font-size: 13px; }
.admin-toolbar-station { width: 260px; }
.admin-toolbar-search { width: 220px; }
.admin-toolbar-action { margin-left: auto; }
.admin-toolbar-total { color: var(--muted); font-size: 13px; }

.admin-hint { margin-bottom: 14px; }

.admin-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.admin-full { width: 100%; }
</style>
