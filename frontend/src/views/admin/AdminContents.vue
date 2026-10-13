<template>
  <section class="admin-panel">
    <div class="admin-tabs-row">
      <el-radio-group v-model="activeType" @change="reset">
        <el-radio-button value="news">资讯管理</el-radio-button>
        <el-radio-button value="notice">公告管理</el-radio-button>
      </el-radio-group>
      <span class="admin-tabs-hint">
        {{ activeType === "news" ? "资讯面向前台「政策资讯」栏目展示" : "公告用于平台通知与投放" }}
      </span>
    </div>

    <div class="admin-toolbar">
      <el-input
        v-model="query.keyword"
        class="admin-toolbar-search"
        clearable
        placeholder="搜索标题或内容"
        @keyup.enter="search"
        @clear="search"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select
        v-if="activeType === 'news'"
        v-model="query.category"
        class="admin-toolbar-select"
        clearable
        placeholder="全部分类"
        @change="search"
      >
        <el-option v-for="item in NEWS_CATEGORIES" :key="item" :label="item" :value="item" />
      </el-select>
      <el-select
        v-else
        v-model="query.noticeType"
        class="admin-toolbar-select"
        clearable
        placeholder="全部类型"
        @change="search"
      >
        <el-option v-for="item in NOTICE_TYPES" :key="item" :label="item" :value="item" />
      </el-select>
      <el-select v-model="query.status" class="admin-toolbar-select" clearable placeholder="全部状态" @change="search">
        <el-option label="已发布" value="PUBLISHED" />
        <el-option label="草稿" value="DRAFT" />
      </el-select>
      <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
      <el-button @click="reset"><el-icon><RefreshLeft /></el-icon>重置</el-button>
      <el-button type="success" class="admin-toolbar-action" @click="openCreate">
        <el-icon><Plus /></el-icon>{{ activeType === "news" ? "新增资讯" : "新增公告" }}
      </el-button>
      <span class="admin-toolbar-total">共 {{ total }} 条</span>
    </div>

    <el-table v-if="activeType === 'news'" :data="rows" v-loading="loading" stripe empty-text="暂无资讯数据">
      <el-table-column prop="id" label="编号" width="80" />
      <el-table-column label="标题" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">{{ textOf(row.title) }}</template>
      </el-table-column>
      <el-table-column label="分类" width="110">
        <template #default="{ row }">{{ textOf(row.category) }}</template>
      </el-table-column>
      <el-table-column label="浏览量" width="100">
        <template #default="{ row }">{{ numberText(row.viewCount) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="contentStatusTag(row.status)">{{ contentStatusLabel(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="日期" width="120">
        <template #default="{ row }">{{ textOf(row.date) }}</template>
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

    <el-table v-else :data="rows" v-loading="loading" stripe empty-text="暂无公告数据">
      <el-table-column prop="id" label="编号" width="80" />
      <el-table-column label="标题" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">{{ textOf(row.title) }}</template>
      </el-table-column>
      <el-table-column label="公告类型" width="120">
        <template #default="{ row }">{{ textOf(row.noticeType) }}</template>
      </el-table-column>
      <el-table-column label="投放对象" width="140">
        <template #default="{ row }">{{ targetLabel(row.targetType) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="contentStatusTag(row.status)">{{ contentStatusLabel(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="日期" width="120">
        <template #default="{ row }">{{ textOf(row.date) }}</template>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="80" show-word-limit placeholder="请输入标题" />
        </el-form-item>
        <el-form-item v-if="activeType === 'news'" label="分类" prop="category">
          <el-select v-model="form.category" class="admin-full" placeholder="选择资讯分类">
            <el-option v-for="item in NEWS_CATEGORIES" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <template v-else>
          <el-form-item label="公告类型" prop="noticeType">
            <el-select v-model="form.noticeType" class="admin-full" placeholder="选择公告类型">
              <el-option v-for="item in NOTICE_TYPES" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="投放对象" prop="targetType">
            <el-select v-model="form.targetType" class="admin-full" placeholder="选择投放对象">
              <el-option v-for="item in NOTICE_TARGETS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </template>
        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="7" maxlength="2000" show-word-limit placeholder="请输入正文内容" />
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

    <el-dialog v-model="previewVisible" :title="previewTitle" width="620px">
      <div v-if="preview" class="admin-preview">
        <div class="admin-preview-meta">
          <el-tag :type="contentStatusTag(preview.status)">{{ contentStatusLabel(preview) }}</el-tag>
          <span>{{ activeType === "news" ? textOf(preview.category) : textOf(preview.noticeType) }}</span>
          <span>{{ textOf(preview.date) }}</span>
        </div>
        <p class="admin-preview-content">{{ textOf(preview.content) }}</p>
      </div>
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
  createNews,
  createNotice,
  deleteNews,
  deleteNotice,
  getAdminNewsPage,
  getAdminNoticePage,
  updateNews,
  updateNotice
} from "../../api/content";
import { errorMessage } from "../../api/http";
import {
  NEWS_CATEGORIES,
  NOTICE_TARGETS,
  NOTICE_TYPES,
  contentStatusLabel,
  contentStatusTag,
  numberText,
  textOf
} from "./admin-shared";

const activeType = ref("news");
const loading = ref(false);
const submitting = ref(false);
const rows = ref([]);
const total = ref(0);
const query = reactive({ current: 1, size: 10, keyword: "", status: "", category: "", noticeType: "" });

const dialogVisible = ref(false);
const editingId = ref(null);
const formRef = ref(null);
const form = reactive({ title: "", category: "政策", noticeType: "通知", targetType: "ALL", content: "", status: "PUBLISHED" });

const previewVisible = ref(false);
const preview = ref(null);

const rules = {
  title: [{ required: true, message: "请填写标题", trigger: "blur" }],
  content: [{ required: true, message: "请填写正文内容", trigger: "blur" }]
};

const dialogTitle = computed(() => {
  if (activeType.value === "news") return editingId.value ? "编辑资讯" : "新增资讯";
  return editingId.value ? "编辑公告" : "新增公告";
});

const previewTitle = computed(() => (activeType.value === "news" ? "资讯预览" : "公告预览"));

function targetLabel(targetType) {
  const matched = NOTICE_TARGETS.find((item) => item.value === targetType);
  return matched ? matched.label : textOf(targetType);
}

async function load() {
  loading.value = true;
  try {
    const params = {
      current: query.current,
      size: query.size,
      keyword: query.keyword || undefined,
      status: query.status || undefined
    };
    const page = activeType.value === "news"
      ? await getAdminNewsPage({ ...params, category: query.category || undefined })
      : await getAdminNoticePage({ ...params, noticeType: query.noticeType || undefined });
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
  query.keyword = "";
  query.status = "";
  query.category = "";
  query.noticeType = "";
  search();
}

function openCreate() {
  editingId.value = null;
  Object.assign(form, {
    title: "",
    category: "政策",
    noticeType: "通知",
    targetType: "ALL",
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
    category: row.category || "政策",
    noticeType: row.noticeType || "通知",
    targetType: row.targetType || "ALL",
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
    if (activeType.value === "news") {
      const payload = { title: form.title, category: form.category, content: form.content, status: form.status };
      if (editingId.value) {
        await updateNews(editingId.value, payload);
        ElMessage.success("资讯已更新");
      } else {
        await createNews(payload);
        ElMessage.success("资讯已新增");
      }
    } else {
      const payload = {
        title: form.title,
        noticeType: form.noticeType,
        targetType: form.targetType,
        content: form.content,
        status: form.status
      };
      if (editingId.value) {
        await updateNotice(editingId.value, payload);
        ElMessage.success("公告已更新");
      } else {
        await createNotice(payload);
        ElMessage.success("公告已新增");
      }
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
  const name = activeType.value === "news" ? "资讯" : "公告";
  try {
    await ElMessageBox.confirm(`确定删除${name}「${textOf(row.title)}」吗？`, `删除${name}`, {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消",
      confirmButtonClass: "el-button--danger"
    });
  } catch {
    return;
  }
  loading.value = true;
  try {
    if (activeType.value === "news") {
      await deleteNews(row.id);
    } else {
      await deleteNotice(row.id);
    }
    ElMessage.success(`${name}已删除`);
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

.admin-tabs-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 14px;
}

.admin-tabs-hint { color: var(--muted); font-size: 12px; }

.admin-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.admin-toolbar-search { width: 240px; }
.admin-toolbar-select { width: 150px; }
.admin-toolbar-action { margin-left: auto; }
.admin-toolbar-total { color: var(--muted); font-size: 13px; }

.admin-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.admin-full { width: 100%; }

.admin-preview-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--muted);
  font-size: 13px;
}

.admin-preview-content {
  margin: 14px 0 0;
  color: #374151;
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
}
</style>
