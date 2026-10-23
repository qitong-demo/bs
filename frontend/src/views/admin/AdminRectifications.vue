<template>
  <section class="admin-panel">
    <div class="admin-toolbar">
      <el-select v-model="status" class="admin-toolbar-select" clearable placeholder="全部状态" @change="search">
        <el-option label="待整改" value="PENDING" />
        <el-option label="待平台复核" value="REVIEW" />
        <el-option label="已关闭" value="CLOSED" />
      </el-select>
      <el-button v-if="platform" type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>下发整改
      </el-button>
      <span class="admin-toolbar-total">共 {{ total }} 条整改</span>
    </div>
    <el-table :data="rows" v-loading="loading" stripe empty-text="暂无整改记录">
      <el-table-column prop="id" label="编号" width="70" />
      <el-table-column v-if="platform" prop="stationName" label="站点" min-width="150" />
      <el-table-column prop="title" label="整改事项" min-width="180" />
      <el-table-column label="等级" width="90">
        <template #default="{ row }">
          <el-tag :type="row.level === 'HIGH' ? 'danger' : 'warning'" effect="plain">
            {{ row.level === "HIGH" ? "重点" : "一般" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="row.status === 'CLOSED' ? 'success' : row.status === 'REVIEW' ? 'primary' : 'warning'" effect="plain">
            {{ row.statusLabel }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="deadline" label="期限" width="110" />
      <el-table-column label="驿站反馈" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.stationReply || "—" }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button v-if="!platform && row.status !== 'CLOSED'" size="small" @click="openReply(row)">
            反馈整改
          </el-button>
          <el-button v-if="platform && row.status !== 'CLOSED'" size="small" type="primary" @click="openReview(row)">
            复核
          </el-button>
          <el-button v-if="platform" size="small" @click="openDetail(row)">查看</el-button>
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

    <el-dialog v-model="createVisible" title="下发服务质量整改" width="520px">
      <el-form label-position="top">
        <el-form-item label="站点" required>
          <el-select v-model="createForm.stationId" class="full" filterable placeholder="选择站点">
            <el-option
              v-for="item in stations"
              :key="item.id"
              :label="`${item.stationName}（${item.area || '未填区域'}）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="整改事项" required>
          <el-input v-model="createForm.title" maxlength="150" placeholder="例如：预约确认超时" />
        </el-form-item>
        <el-form-item label="整改要求" required>
          <el-input v-model="createForm.content" type="textarea" :rows="4" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-form-item label="等级">
          <el-radio-group v-model="createForm.level">
            <el-radio-button value="NORMAL">一般</el-radio-button>
            <el-radio-button value="HIGH">重点</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="整改期限">
          <el-date-picker v-model="createForm.deadline" type="date" value-format="YYYY-MM-DD" class="full" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">下发</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="replyVisible" title="反馈整改" width="480px">
      <el-input v-model="replyText" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="说明整改措施和结果" />
      <template #footer>
        <el-button @click="replyVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReply">提交反馈</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reviewVisible" title="整改复核" width="520px">
      <el-descriptions v-if="reviewTarget" :column="1" border size="small" class="admin-dialog-meta">
        <el-descriptions-item label="站点">{{ reviewTarget.stationName }}</el-descriptions-item>
        <el-descriptions-item label="事项">{{ reviewTarget.title }}</el-descriptions-item>
        <el-descriptions-item label="整改反馈">{{ reviewTarget.stationReply || "—" }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-position="top">
        <el-form-item label="复核结果">
          <el-radio-group v-model="reviewForm.action">
            <el-radio-button value="approve">通过并关闭</el-radio-button>
            <el-radio-button value="reject">退回继续整改</el-radio-button>
            <el-radio-button value="suspend">暂停服务 7 天</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="复核说明">
          <el-input v-model="reviewForm.note" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReview">提交复核</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="整改详情" width="560px">
      <el-descriptions v-if="detail" :column="1" border size="small">
        <el-descriptions-item label="站点">{{ detail.stationName }}</el-descriptions-item>
        <el-descriptions-item label="事项">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="要求">{{ detail.content }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detail.statusLabel }}</el-descriptions-item>
        <el-descriptions-item label="反馈">{{ detail.stationReply || "—" }}</el-descriptions-item>
        <el-descriptions-item label="复核说明">{{ detail.reviewNote || "—" }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import {
  createRectification,
  getRectificationPage,
  replyRectification,
  reviewRectification
} from "../../api/rectification";
import { getAdminStationPage } from "../../api/station";
import { useUserStore } from "../../stores/user";
import { errorMessage } from "../../api/http";

const props = defineProps({ mode: { type: String, default: "platform" } });
const userStore = useUserStore();
const platform = computed(() => props.mode === "platform" || userStore.roleCode === "PLATFORM_ADMIN");
const loading = ref(false);
const submitting = ref(false);
const rows = ref([]);
const total = ref(0);
const status = ref("");
const query = reactive({ current: 1, size: 10 });
const stations = ref([]);
const createVisible = ref(false);
const createForm = reactive({ stationId: null, title: "", content: "", level: "NORMAL", deadline: "" });
const replyVisible = ref(false);
const replyTarget = ref(null);
const replyText = ref("");
const reviewVisible = ref(false);
const reviewTarget = ref(null);
const reviewForm = reactive({ action: "approve", note: "" });
const detailVisible = ref(false);
const detail = ref(null);

async function load() {
  loading.value = true;
  try {
    const data = await getRectificationPage({ ...query, status: status.value });
    rows.value = data?.records || [];
    total.value = Number(data?.total) || 0;
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    loading.value = false;
  }
}

function search() { query.current = 1; load(); }
function openCreate() { Object.assign(createForm, { stationId: stations.value[0]?.id || null, title: "", content: "", level: "NORMAL", deadline: "" }); createVisible.value = true; }
async function submitCreate() {
  if (!createForm.stationId || !createForm.title.trim() || !createForm.content.trim()) {
    ElMessage.warning("请填写站点、整改事项和要求"); return;
  }
  submitting.value = true;
  try {
    await createRectification({ ...createForm });
    createVisible.value = false; ElMessage.success("整改通知已下发"); await load();
  } catch (error) { ElMessage.error(errorMessage(error)); } finally { submitting.value = false; }
}
function openReply(row) { replyTarget.value = row; replyText.value = ""; replyVisible.value = true; }
async function submitReply() {
  if (!replyText.value.trim()) { ElMessage.warning("请填写整改反馈"); return; }
  submitting.value = true;
  try {
    await replyRectification(replyTarget.value.id, replyText.value.trim());
    replyVisible.value = false; ElMessage.success("整改反馈已提交"); await load();
  } catch (error) { ElMessage.error(errorMessage(error)); } finally { submitting.value = false; }
}
function openReview(row) { reviewTarget.value = row; reviewForm.action = "approve"; reviewForm.note = ""; reviewVisible.value = true; }
async function submitReview() {
  submitting.value = true;
  try {
    await reviewRectification(reviewTarget.value.id, { ...reviewForm });
    reviewVisible.value = false; ElMessage.success("复核完成"); await load();
  } catch (error) { ElMessage.error(errorMessage(error)); } finally { submitting.value = false; }
}
function openDetail(row) { detail.value = row; detailVisible.value = true; }

onMounted(async () => {
  if (platform.value) {
    try { stations.value = (await getAdminStationPage({ current: 1, size: 100 }))?.records || []; } catch { /* ignore */ }
  }
  await load();
});
</script>

<style scoped>
.admin-panel { padding: 16px 18px 18px; background: #fff; border: 1px solid var(--line); border-radius: 6px; }
.admin-toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.admin-toolbar-select { width: 160px; }
.admin-toolbar-total { margin-left: auto; color: var(--muted); font-size: 13px; }
.admin-pager { display: flex; justify-content: flex-end; margin-top: 14px; }
.admin-dialog-meta { margin-bottom: 16px; }
</style>
