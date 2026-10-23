<template>
  <section class="admin-panel">
    <div class="admin-toolbar">
      <el-select v-model="query.status" class="admin-toolbar-select" clearable placeholder="全部状态" @change="search">
        <el-option label="待审核" value="PENDING" />
        <el-option label="已通过" value="APPROVED" />
        <el-option label="未通过" value="REJECTED" />
        <el-option label="已撤回" value="CANCELLED" />
      </el-select>
      <el-input
        v-model="query.keyword"
        class="admin-toolbar-search"
        clearable
        placeholder="搜索驿站、联系人、电话或地址"
        @keyup.enter="search"
        @clear="search"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
      <el-button @click="reset"><el-icon><RefreshLeft /></el-icon>重置</el-button>
      <span class="admin-toolbar-total">共 {{ total }} 条申请</span>
    </div>

    <el-table :data="rows" v-loading="loading" stripe empty-text="暂无入驻申请">
      <el-table-column prop="id" label="编号" width="80" />
      <el-table-column prop="stationName" label="驿站名称" min-width="180" show-overflow-tooltip />
      <el-table-column label="申请人" width="110">
        <template #default="{ row }">{{ textOf(row.applicantName) || "—" }}</template>
      </el-table-column>
      <el-table-column label="区域/地址" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.area }} · {{ row.address }}</template>
      </el-table-column>
      <el-table-column label="联系电话" width="140">
        <template #default="{ row }">{{ textOf(row.contactPhone) || "—" }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" effect="plain">{{ row.statusLabel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="提交时间" width="170" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openDetail(row)">查看</el-button>
          <el-button
            v-if="row.status === 'PENDING'"
            size="small"
            type="primary"
            @click="openReview(row, 'approve')"
          >审核</el-button>
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

    <!-- 入驻资料 -->
    <el-dialog v-model="detailVisible" title="驿站入驻资料" width="640px">
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="驿站名称" :span="2">{{ detail.stationName }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ textOf(detail.applicantName) || "—" }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detail.statusLabel }}</el-descriptions-item>
        <el-descriptions-item label="区域">{{ detail.area }}</el-descriptions-item>
        <el-descriptions-item label="驿站类型">{{ textOf(detail.stationType) || "—" }}</el-descriptions-item>
        <el-descriptions-item label="详细地址" :span="2">{{ detail.address }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ textOf(detail.contactName) || "—" }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ textOf(detail.contactPhone) || "—" }}</el-descriptions-item>
        <el-descriptions-item label="开放时间">{{ textOf(detail.openTime) || "—" }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ detail.createTime }}</el-descriptions-item>
        <el-descriptions-item label="补充说明" :span="2">{{ textOf(detail.description) || "—" }}</el-descriptions-item>
        <el-descriptions-item label="资质文件" :span="2">
          <a v-if="detail.licenseUrl" :href="detail.licenseUrl" target="_blank" rel="noreferrer">查看资质文件</a>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="站点照片" :span="2">
          <a v-if="detail.photoUrls" :href="detail.photoUrls" target="_blank" rel="noreferrer">查看站点照片</a>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.reviewNote" label="审核说明" :span="2">{{ detail.reviewNote }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="detail?.status === 'PENDING'"
          type="primary"
          @click="openReview(detail, 'approve')"
        >审核</el-button>
      </template>
    </el-dialog>

    <!-- 审核操作 -->
    <el-dialog v-model="reviewVisible" title="审核入驻申请" width="520px">
      <el-descriptions v-if="reviewTarget" :column="1" border size="small" class="admin-dialog-meta">
        <el-descriptions-item label="驿站">{{ reviewTarget.stationName }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ textOf(reviewTarget.applicantName) || "—" }}</el-descriptions-item>
        <el-descriptions-item label="区域/地址">{{ reviewTarget.area }} · {{ reviewTarget.address }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-position="top">
        <el-form-item label="审核结果" required>
          <el-radio-group v-model="reviewForm.action">
            <el-radio-button value="approve">通过并创建驿站</el-radio-button>
            <el-radio-button value="reject">不通过</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="reviewForm.action === 'reject' ? '未通过原因' : '审核说明'" :required="reviewForm.action === 'reject'">
          <el-input
            v-model="reviewForm.reviewNote"
            type="textarea"
            :rows="3"
            maxlength="300"
            show-word-limit
            :placeholder="reviewForm.action === 'reject'
              ? '请说明原因，申请人会在消息中心看到'
              : '可选，例如：资料已核对，同意入驻'"
          />
        </el-form-item>
        <el-alert
          v-if="reviewForm.action === 'approve'"
          type="warning"
          :closable="false"
          show-icon
          title="通过后会立即创建驿站，并把申请人升级为驿站管理员；申请人需要重新登录。"
        />
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReview">提交审核</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import {
  approveStationApplication,
  getStationApplicationPage,
  rejectStationApplication
} from "../../api/station-apply";
import { errorMessage } from "../../api/http";
import { textOf } from "./admin-shared";

const props = defineProps({
  focusApplicationId: { type: Number, default: 0 }
});
const emit = defineEmits(["reviewed"]);

const loading = ref(false);
const submitting = ref(false);
const rows = ref([]);
const total = ref(0);
const query = reactive({ current: 1, size: 10, status: "", keyword: "" });

const detailVisible = ref(false);
const detail = ref(null);
const reviewVisible = ref(false);
const reviewTarget = ref(null);
const reviewForm = reactive({ action: "approve", reviewNote: "" });

async function load() {
  loading.value = true;
  try {
    const data = await getStationApplicationPage(query);
    rows.value = data?.records || [];
    total.value = Number(data?.total) || 0;
  } catch (error) {
    ElMessage.error(errorMessage(error, "入驻申请加载失败"));
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
  query.current = 1;
  load();
}

function statusTag(status) {
  if (status === "APPROVED") return "success";
  if (status === "REJECTED") return "danger";
  if (status === "CANCELLED") return "info";
  return "warning";
}

function openDetail(row) {
  detail.value = row;
  detailVisible.value = true;
}

function openReview(row, action = "approve") {
  if (!row || row.status !== "PENDING") return;
  reviewTarget.value = row;
  reviewForm.action = action;
  reviewForm.reviewNote = "";
  reviewVisible.value = true;
}

async function submitReview() {
  if (!reviewTarget.value) return;
  if (reviewForm.action === "reject" && !reviewForm.reviewNote.trim()) {
    ElMessage.warning("请填写未通过原因，申请人需要知道原因");
    return;
  }
  submitting.value = true;
  try {
    if (reviewForm.action === "approve") {
      await approveStationApplication(reviewTarget.value.id, reviewForm.reviewNote.trim());
      ElMessage.success("已通过并创建驿站，申请人需重新登录");
    } else {
      await rejectStationApplication(reviewTarget.value.id, reviewForm.reviewNote.trim());
      ElMessage.success("已驳回，申请人会收到通知");
    }
    reviewVisible.value = false;
    detailVisible.value = false;
    await load();
    emit("reviewed");
  } catch (error) {
    ElMessage.error(errorMessage(error, "审核失败"));
  } finally {
    submitting.value = false;
  }
}

async function focusApplication(id) {
  if (!id) return;
  await load();
  const row = rows.value.find((item) => Number(item.id) === Number(id));
  if (row) {
    openDetail(row);
    if (row.status === "PENDING") {
      openReview(row, "approve");
    }
  }
}

watch(() => props.focusApplicationId, focusApplication, { immediate: true });
onMounted(load);
</script>

<style scoped>
.admin-panel {
  padding: 16px 18px 18px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 6px;
}
.admin-toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.admin-toolbar-search { width: 300px; }
.admin-toolbar-select { width: 160px; }
.admin-toolbar-total { margin-left: auto; color: var(--muted); font-size: 13px; }
.admin-pager { display: flex; justify-content: flex-end; margin-top: 14px; }
.admin-dialog-meta { margin-bottom: 16px; }
</style>
