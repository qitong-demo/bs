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
          <template #default="{ row }">{{ Number(row.stationId) > 0 ? textOf(row.stationName) : "平台自办" }}</template>
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
        <el-table-column label="操作" width="270" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openPreview(row)">查看</el-button>
            <el-button
              v-if="isPlatformAdmin && row.status === 'PENDING_REVIEW'"
              size="small"
              type="primary"
              @click="openActivityReview(row)"
            >审核</el-button>
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
      <div class="admin-toolbar signup-toolbar">
        <el-radio-group v-model="signupStatus" size="small">
          <el-radio-button value="">全部 {{ signupCounts.all }}</el-radio-button>
          <el-radio-button value="PENDING">待处理 {{ signupCounts.pending }}</el-radio-button>
          <el-radio-button value="APPROVED">已通过 {{ signupCounts.approved }}</el-radio-button>
          <el-radio-button value="REJECTED">未通过 {{ signupCounts.rejected }}</el-radio-button>
          <el-radio-button value="CANCELLED">已取消 {{ signupCounts.cancelled }}</el-radio-button>
        </el-radio-group>
        <el-input
          v-model="signupKeyword"
          class="admin-toolbar-search"
          clearable
          placeholder="搜索活动、报名人或电话"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button class="admin-toolbar-action" @click="loadSignups"><el-icon><Refresh /></el-icon>刷新</el-button>
        <span class="admin-toolbar-total">共 {{ filteredSignups.length }} 条报名</span>
      </div>
      <el-table :data="pagedSignups" v-loading="signupLoading" stripe empty-text="暂无报名记录">
        <el-table-column prop="id" label="编号" width="70" />
        <el-table-column label="活动名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ textOf(row.activityTitle) }}</template>
        </el-table-column>
        <!-- 报名人姓名与电话是承办方联系报名人的依据，直接列出，不必再点开 -->
        <el-table-column label="报名人" width="110">
          <template #default="{ row }">{{ textOf(row.contactName || row.userName) }}</template>
        </el-table-column>
        <el-table-column label="联系电话" width="140">
          <template #default="{ row }">
            <a v-if="row.phone" :href="`tel:${row.phone}`" class="signup-phone">{{ row.phone }}</a>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ textOf(row.remark) || "—" }}</template>
        </el-table-column>
        <el-table-column label="报名状态" width="130">
          <template #default="{ row }">
            <el-tag :type="signupStatusTag(row.status)" effect="plain">{{ textOf(row.statusLabel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核反馈" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.reviewNote">{{ row.reviewNote }}</span>
            <span v-else class="muted">尚未反馈</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :disabled="['APPROVED', 'REJECTED', 'CANCELLED'].includes(row.status)" @click="openReview(row)">联系与审核</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="admin-pager">
        <el-pagination
          v-model:current-page="signupPage"
          v-model:page-size="signupPageSize"
          :page-sizes="[10, 20, 50]"
          :total="filteredSignups.length"
          layout="total, sizes, prev, pager, next, jumper"
          background
        />
      </div>
    </template>

    <!-- 承办方处理报名：联系、审核通过或未通过，并给出给报名人的反馈 -->
    <el-dialog v-model="reviewVisible" title="联系与审核报名" width="520px">
      <el-descriptions v-if="reviewTarget" :column="1" border size="small" class="admin-dialog-meta">
        <el-descriptions-item label="活动">{{ textOf(reviewTarget.activityTitle) }}</el-descriptions-item>
        <el-descriptions-item label="报名人">{{ textOf(reviewTarget.contactName || reviewTarget.userName) }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">
          <a v-if="reviewTarget.phone" :href="`tel:${reviewTarget.phone}`" class="signup-phone">{{ reviewTarget.phone }}</a>
          <span v-else>—</span>
        </el-descriptions-item>
        <el-descriptions-item label="备注">{{ textOf(reviewTarget.remark) || "—" }}</el-descriptions-item>
        <el-descriptions-item label="当前状态">{{ textOf(reviewTarget.statusLabel) }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-position="top">
        <el-form-item label="处理结果" required>
          <el-radio-group v-model="reviewForm.status">
            <el-radio-button value="CONTACTED">已联系</el-radio-button>
            <el-radio-button value="APPROVED">审核通过</el-radio-button>
            <el-radio-button value="REJECTED">审核未通过</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="给报名人的反馈" :required="reviewForm.status === 'REJECTED'">
          <el-input
            v-model="reviewForm.reviewNote"
            type="textarea"
            :rows="3"
            maxlength="300"
            show-word-limit
            :placeholder="reviewForm.status === 'REJECTED'
              ? '未通过必须说明原因，报名人会看到这段说明'
              : '例如：已电话确认，周六上午 9 点在驿站集合，记得带身份证'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="reviewSubmitting" @click="submitReview">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑活动' : '新增活动'" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="94px">
        <el-form-item label="活动名称" prop="title">
          <el-input v-model="form.title" maxlength="80" show-word-limit placeholder="请输入活动名称" />
        </el-form-item>
        <el-form-item label="举办驿站" prop="stationId">
          <el-select v-model="form.stationId" filterable class="admin-full" placeholder="选择举办驿站">
            <el-option v-if="isPlatformAdmin" :value="0" label="平台自办（不挂驿站）" />
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
        <el-form-item v-if="isPlatformAdmin" label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="PUBLISHED">已发布</el-radio>
            <el-radio value="DRAFT">草稿</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-alert
          v-else
          type="info"
          :closable="false"
          show-icon
          title="驿站活动保存后进入平台审核，审核通过才会在用户端发布。"
        />
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="activityReviewVisible" title="审核公益活动" width="520px">
      <el-descriptions v-if="activityReviewTarget" :column="1" border size="small" class="admin-dialog-meta">
        <el-descriptions-item label="活动">{{ activityReviewTarget.title }}</el-descriptions-item>
        <el-descriptions-item label="驿站">{{ textOf(activityReviewTarget.stationName) || "平台自办" }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ activityReviewTarget.activityTime }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-position="top">
        <el-form-item label="审核结果" required>
          <el-radio-group v-model="activityReviewForm.action">
            <el-radio-button value="approve">通过并发布</el-radio-button>
            <el-radio-button value="reject">驳回</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核说明" :required="activityReviewForm.action === 'reject'">
          <el-input
            v-model="activityReviewForm.reviewNote"
            type="textarea"
            :rows="3"
            maxlength="300"
            show-word-limit
            placeholder="驳回时必须说明原因，驿站会在消息中心看到"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="activityReviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="activityReviewSubmitting" @click="submitActivityReview">提交审核</el-button>
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
import { computed, onMounted, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  createActivity,
  deleteActivity,
  getAdminActivityPage,
  reviewActivity,
  updateActivity
} from "../../api/content";
import { getAdminSignups, reviewSignup } from "../../api/workflow";
import { getStationPage } from "../../api/station";
import { errorMessage } from "../../api/http";
import { useUserStore } from "../../stores/user";
import { contentStatusLabel, contentStatusTag, numberText, signupStatusTag, textOf } from "./admin-shared";

const props = defineProps({
  /** 初始展示的区块：activities（活动列表）或 signups（报名记录）。 */
  section: { type: String, default: "activities" },
  /** 从消息通知跳转时带过来的报名 id，加载后自动打开处理弹窗。 */
  focusSignupId: { type: Number, default: 0 },
  /** 从活动待审核通知跳转时带过来的活动 id。 */
  focusActivityId: { type: Number, default: 0 }
});
const emit = defineEmits(["reviewed"]);
const userStore = useUserStore();
const isPlatformAdmin = computed(() => userStore.roleCode === "PLATFORM_ADMIN");

const activeSection = ref(props.section === "signups" ? "signups" : "activities");
const showSectionSwitch = computed(() => props.section !== "signups");

const loading = ref(false);
const submitting = ref(false);
const rows = ref([]);
const total = ref(0);
const query = reactive({ current: 1, size: 10, status: "", keyword: "" });

const signupLoading = ref(false);

/** 处理报名的弹窗：联系、审核通过或未通过，并给出反馈。 */
const reviewVisible = ref(false);
const reviewSubmitting = ref(false);
const reviewTarget = ref(null);
const reviewForm = reactive({ status: "CONTACTED", reviewNote: "" });

function openReview(row) {
  if (!row || ["APPROVED", "REJECTED", "CANCELLED"].includes(row.status)) {
    return;
  }
  reviewTarget.value = row;
  // 已审核过的再次打开时沿用原状态，避免手滑把结论改掉
  reviewForm.status = ["CONTACTED", "APPROVED", "REJECTED"].includes(row.status) ? row.status : "CONTACTED";
  reviewForm.reviewNote = row.reviewNote || "";
  reviewVisible.value = true;
}

async function submitReview() {
  const target = reviewTarget.value;
  if (!target) return;
  if (reviewForm.status === "REJECTED" && !reviewForm.reviewNote.trim()) {
    ElMessage.warning("审核未通过时请填写原因，报名人会看到这段说明");
    return;
  }
  reviewSubmitting.value = true;
  try {
    await reviewSignup({
      id: target.id,
      status: reviewForm.status,
      reviewNote: reviewForm.reviewNote.trim()
    });
    reviewVisible.value = false;
    ElMessage.success("已提交处理结果，报名人可以看到反馈");
    await loadSignups();
    emit("reviewed");
  } catch (error) {
    ElMessage.error(errorMessage(error, "处理失败"));
  } finally {
    reviewSubmitting.value = false;
  }
}
const signups = ref([]);
const activityReviewVisible = ref(false);
const activityReviewSubmitting = ref(false);
const activityReviewTarget = ref(null);
const activityReviewForm = reactive({ action: "approve", reviewNote: "" });
const signupStatus = ref("");
const signupKeyword = ref("");
const signupPage = ref(1);
const signupPageSize = ref(10);

const signupCounts = computed(() => {
  const result = { all: signups.value.length, pending: 0, approved: 0, rejected: 0, cancelled: 0 };
  for (const row of signups.value) {
    if (["SIGNED_UP", "CONTACTED"].includes(row.status)) result.pending += 1;
    else if (row.status === "APPROVED") result.approved += 1;
    else if (row.status === "REJECTED") result.rejected += 1;
    else if (row.status === "CANCELLED") result.cancelled += 1;
  }
  return result;
});

const filteredSignups = computed(() => {
  const keyword = signupKeyword.value.trim().toLowerCase();
  return signups.value.filter((row) => {
    if (signupStatus.value === "PENDING" && !["SIGNED_UP", "CONTACTED"].includes(row.status)) return false;
    if (!["", "PENDING"].includes(signupStatus.value) && row.status !== signupStatus.value) return false;
    if (!keyword) return true;
    return [row.activityTitle, row.contactName, row.userName, row.phone]
      .some((value) => String(value || "").toLowerCase().includes(keyword));
  });
});

const pagedSignups = computed(() => {
  const start = (signupPage.value - 1) * signupPageSize.value;
  return filteredSignups.value.slice(start, start + signupPageSize.value);
});

watch([signupStatus, signupKeyword], () => {
  signupPage.value = 1;
});

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

function openActivityReview(row) {
  if (!row || row.status !== "PENDING_REVIEW") return;
  activityReviewTarget.value = row;
  activityReviewForm.action = "approve";
  activityReviewForm.reviewNote = "";
  activityReviewVisible.value = true;
}

async function submitActivityReview() {
  if (!activityReviewTarget.value) return;
  if (activityReviewForm.action === "reject" && !activityReviewForm.reviewNote.trim()) {
    ElMessage.warning("驳回时请填写原因，驿站会在消息中心看到");
    return;
  }
  activityReviewSubmitting.value = true;
  try {
    await reviewActivity(activityReviewTarget.value.id, {
      action: activityReviewForm.action,
      reviewNote: activityReviewForm.reviewNote.trim()
    });
    ElMessage.success(activityReviewForm.action === "approve" ? "已通过并发布" : "已驳回");
    activityReviewVisible.value = false;
    await load();
    emit("reviewed");
  } catch (error) {
    ElMessage.error(errorMessage(error, "审核失败"));
  } finally {
    activityReviewSubmitting.value = false;
  }
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

watch(
  () => props.focusActivityId,
  async (id) => {
    if (!id) return;
    await load();
    const row = rows.value.find((item) => Number(item.id) === Number(id));
    if (row) openActivityReview(row);
  },
  { immediate: true }
);

watch(
  () => props.focusSignupId,
  async (id) => {
    if (!id) return;
    if (!signups.value.length) {
      await loadSignups();
    }
    const row = signups.value.find((item) => Number(item.id) === Number(id));
    if (row) {
      openReview(row);
    }
  },
  { immediate: true }
);

function onSectionChange(section) {
  if (section === "signups" && !signups.value.length) {
    loadSignups();
  }
}

function openCreate() {
  editingId.value = null;
  Object.assign(form, {
    title: "",
    stationId: isPlatformAdmin.value ? 0 : (stations.value.length ? stations.value[0].id : null),
    activityTime: "",
    address: "",
    maxCount: 30,
    content: "",
    status: isPlatformAdmin.value ? "PUBLISHED" : "PENDING_REVIEW"
  });
  formRef.value?.clearValidate();
  dialogVisible.value = true;
}

function openEdit(row) {
  editingId.value = row.id;
  Object.assign(form, {
    title: row.title || "",
    stationId: Number(row.stationId) > 0 ? row.stationId : 0,
    activityTime: row.activityTime || "",
    address: row.address || "",
    maxCount: Number(row.maxCount) > 0 ? Number(row.maxCount) : 1,
    content: row.content || "",
    status: isPlatformAdmin.value ? (row.status || "PUBLISHED") : "PENDING_REVIEW"
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
      stationId: Number(form.stationId) > 0 ? form.stationId : null,
      activityTime: form.activityTime,
      address: form.address,
      maxCount: form.maxCount,
      content: form.content,
      status: form.status
    };
    if (editingId.value) {
      await updateActivity(editingId.value, payload);
      ElMessage.success(isPlatformAdmin.value ? "活动已更新" : "已更新并提交平台审核");
    } else {
      await createActivity(payload);
      ElMessage.success(isPlatformAdmin.value ? "活动已新增" : "已提交平台审核");
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

.signup-toolbar {
  flex-wrap: wrap;
}

.signup-toolbar .el-radio-group {
  flex-wrap: wrap;
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
