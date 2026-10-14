<script setup>
/**
 * 变更审批：驿站提交的信息变更申请，管理员在此核准或驳回。
 *
 * 位置变更必须走这里——现有坐标是高德 POI 派生并经三重校验的，
 * 驿站自改会让来源标注失真。核准后后端会写 geocode_source = MANUAL_APPROVED
 * 并在 data_note 追加说明。
 */
import { onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { errorMessage } from "../../api/http";
import { approveChange, getPendingChanges, rejectChange } from "../../api/station-change";
import { textOf } from "./admin-shared";

const rows = ref([]);
const loading = ref(false);

async function load() {
  loading.value = true;
  try {
    rows.value = (await getPendingChanges()) || [];
  } catch (error) {
    ElMessage.error(errorMessage(error, "变更申请加载失败"));
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

async function approve(row) {
  try {
    const { value } = await ElMessageBox.prompt(
      `确认通过「${textOf(row.stationName)}」的${textOf(row.typeLabel)}变更？`,
      "变更审批",
      {
        inputPlaceholder: "审批说明（可留空）",
        inputValue: "",
        confirmButtonText: "通过",
        cancelButtonText: "取消"
      }
    );
    await approveChange(row.id, value || "");
    ElMessage.success("已通过，驿站信息已更新");
    await load();
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(errorMessage(error, "审批失败"));
    }
  }
}

async function reject(row) {
  try {
    const { value } = await ElMessageBox.prompt(
      `驳回「${textOf(row.stationName)}」的${textOf(row.typeLabel)}变更`,
      "驳回变更",
      {
        inputPlaceholder: "驳回原因（必填，驿站会看到）",
        inputValue: "",
        // 后端也要求必填，这里先拦一次，避免白跑一趟
        inputValidator: (text) => (text && text.trim() ? true : "请填写驳回原因"),
        confirmButtonText: "驳回",
        cancelButtonText: "取消"
      }
    );
    await rejectChange(row.id, value || "");
    ElMessage.success("已驳回，驿站可以看到原因");
    await load();
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error(errorMessage(error, "驳回失败"));
    }
  }
}

onMounted(load);
</script>

<template>
  <section class="admin-section">
    <div class="admin-toolbar">
      <span class="admin-toolbar-label">驿站提交的信息变更申请，核准后写入驿站信息并留痕</span>
      <el-button class="admin-toolbar-action" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button>
      <span class="admin-toolbar-total">待审批 {{ rows.length }} 条</span>
    </div>

    <el-table :data="rows" v-loading="loading" stripe empty-text="暂无待审批的变更申请">
      <el-table-column prop="id" label="编号" width="70" />
      <el-table-column label="驿站" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ textOf(row.stationName) }}</template>
      </el-table-column>
      <el-table-column label="变更项" width="100">
        <template #default="{ row }">
          <el-tag effect="plain">{{ textOf(row.typeLabel) }}</el-tag>
        </template>
      </el-table-column>
      <!-- 新旧值并排，便于一眼对比 -->
      <el-table-column label="变更前" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span :class="{ muted: !row.oldValue }">{{ textOf(row.oldValue) || "（原值为空）" }}</span>
        </template>
      </el-table-column>
      <el-table-column label="变更后" min-width="160" show-overflow-tooltip>
        <template #default="{ row }"><strong>{{ textOf(row.newValue) }}</strong></template>
      </el-table-column>
      <el-table-column label="申请理由" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ textOf(row.reason) }}</template>
      </el-table-column>
      <el-table-column label="提交时间" width="170">
        <template #default="{ row }">{{ textOf(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="approve(row)">通过</el-button>
          <el-button size="small" type="danger" @click="reject(row)">驳回</el-button>
        </template>
      </el-table-column>
    </el-table>

    <p class="form-hint">
      位置变更核准后会写入坐标，并把来源标注为 MANUAL_APPROVED、在数据说明里追加变更记录——
      人工改动必须与高德派生区分开，否则来源可核对性就失真了。
    </p>
  </section>
</template>
