<template>
  <section class="admin-panel">
    <div class="admin-toolbar">
      <el-input
        v-model="query.keyword"
        class="admin-toolbar-search"
        clearable
        placeholder="搜索账号、姓名、电话或区域"
        @keyup.enter="search"
        @clear="search"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="query.role" class="admin-toolbar-select" clearable placeholder="全部角色" @change="search">
        <el-option v-for="item in ROLE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-button type="primary" @click="search"><el-icon><Search /></el-icon>查询</el-button>
      <el-button @click="reset"><el-icon><RefreshLeft /></el-icon>重置</el-button>
      <span class="admin-toolbar-total">共 {{ total }} 个账号</span>
    </div>

    <el-table :data="rows" v-loading="loading" stripe empty-text="暂无账号数据">
      <el-table-column prop="id" label="编号" width="80" />
      <el-table-column label="账号" min-width="140">
        <template #default="{ row }">{{ textOf(row.username) }}</template>
      </el-table-column>
      <el-table-column label="姓名" width="120">
        <template #default="{ row }">{{ textOf(row.realName) }}</template>
      </el-table-column>
      <el-table-column label="联系电话" width="140">
        <template #default="{ row }">{{ textOf(row.phone) }}</template>
      </el-table-column>
      <el-table-column label="群体类型" width="130">
        <template #default="{ row }">{{ textOf(row.jobType) }}</template>
      </el-table-column>
      <el-table-column label="所属区域" width="120">
        <template #default="{ row }">{{ textOf(row.area) }}</template>
      </el-table-column>
      <el-table-column label="角色" min-width="160">
        <template #default="{ row }">
          <el-tag v-for="code in rolesOf(row)" :key="code" size="small" effect="light" class="admin-tag">{{ roleNameOf(code) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="userStatusTag(row.status)">{{ userStatusLabel(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ textOf(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="270" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑资料</el-button>
          <el-button size="small" @click="openRoles(row)">设置角色</el-button>
          <el-button
            size="small"
            :type="Number(row.status) === 1 ? 'danger' : 'success'"
            @click="toggleStatus(row)"
          >
            {{ Number(row.status) === 1 ? "停用" : "启用" }}
          </el-button>
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

    <el-dialog v-model="editVisible" title="编辑账号资料" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="94px">
        <el-form-item label="账号">
          <el-input :model-value="textOf(current?.username)" disabled />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" maxlength="30" placeholder="请输入真实姓名" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="form.phone" maxlength="20" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="群体类型" prop="jobType">
          <el-select v-model="form.jobType" class="admin-full" filterable allow-create clearable placeholder="例如：外卖骑手">
            <el-option v-for="item in jobTypes" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属区域" prop="area">
          <el-input v-model="form.area" maxlength="30" placeholder="例如：西湖区" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitProfile">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rolesVisible" title="设置角色" width="520px">
      <el-alert
        class="admin-dialog-tip"
        type="info"
        :closable="false"
        show-icon
        title="至少保留一个角色；内存演示模式下每个账号只生效列表中的第一个角色。"
      />
      <el-form label-position="top">
        <el-form-item label="账号">
          <el-input :model-value="textOf(current?.username)" disabled />
        </el-form-item>
        <el-form-item label="角色（可多选）">
          <el-select v-model="roleCodes" multiple class="admin-full" placeholder="请选择角色">
            <el-option v-for="item in ROLE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rolesVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitRoles">保存角色</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  getAdminUsers,
  updateAdminUser,
  updateAdminUserRoles,
  updateAdminUserStatus
} from "../../api/admin";
import { errorMessage } from "../../api/http";
import { ROLE_OPTIONS, textOf, userStatusLabel, userStatusTag } from "./admin-shared";

const jobTypes = ["外卖骑手", "快递员", "网约车司机", "货车司机", "家政服务", "驿站工作人员"];

const loading = ref(false);
const submitting = ref(false);
const rows = ref([]);
const total = ref(0);
const query = reactive({ current: 1, size: 10, role: "", keyword: "" });

const current = ref(null);
const editVisible = ref(false);
const rolesVisible = ref(false);
const formRef = ref(null);
const form = reactive({ realName: "", phone: "", jobType: "", area: "" });
const roleCodes = ref([]);

const rules = {
  realName: [{ required: true, message: "请填写姓名", trigger: "blur" }]
};

function rolesOf(row) {
  const codes = Array.isArray(row?.roles) && row.roles.length ? row.roles : [row?.role].filter(Boolean);
  return codes;
}

function roleNameOf(code) {
  const matched = ROLE_OPTIONS.find((item) => item.value === code);
  return matched ? matched.label : code;
}

async function load() {
  loading.value = true;
  try {
    const page = await getAdminUsers({
      current: query.current,
      size: query.size,
      role: query.role || undefined,
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
  query.role = "";
  query.keyword = "";
  search();
}

function openEdit(row) {
  current.value = row;
  Object.assign(form, {
    realName: row.realName || "",
    phone: row.phone || "",
    jobType: row.jobType || "",
    area: row.area || ""
  });
  formRef.value?.clearValidate();
  editVisible.value = true;
}

async function submitProfile() {
  if (!current.value) return;
  try {
    await formRef.value.validate();
  } catch {
    return;
  }
  submitting.value = true;
  try {
    await updateAdminUser(current.value.id, {
      realName: form.realName,
      phone: form.phone,
      jobType: form.jobType,
      area: form.area
    });
    ElMessage.success("账号资料已更新");
    editVisible.value = false;
    await load();
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    submitting.value = false;
  }
}

function openRoles(row) {
  current.value = row;
  roleCodes.value = rolesOf(row);
  rolesVisible.value = true;
}

async function submitRoles() {
  if (!current.value) return;
  if (!roleCodes.value.length) {
    ElMessage.warning("请至少选择一个角色");
    return;
  }
  submitting.value = true;
  try {
    await updateAdminUserRoles(current.value.id, roleCodes.value);
    ElMessage.success("账号角色已更新");
    rolesVisible.value = false;
    await load();
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    submitting.value = false;
  }
}

async function toggleStatus(row) {
  const enabling = Number(row.status) !== 1;
  const nextStatus = enabling ? "正常" : "停用";
  try {
    await ElMessageBox.confirm(
      enabling
        ? `确定启用账号「${textOf(row.username)}」吗？`
        : `停用后账号「${textOf(row.username)}」将无法登录平台，确定停用吗？`,
      enabling ? "启用账号" : "停用账号",
      { type: "warning", confirmButtonText: "确定", cancelButtonText: "取消" }
    );
  } catch {
    return;
  }
  loading.value = true;
  try {
    await updateAdminUserStatus(row.id, nextStatus);
    ElMessage.success(enabling ? "账号已启用" : "账号已停用");
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

.admin-toolbar-search { width: 280px; }
.admin-toolbar-select { width: 160px; }
.admin-toolbar-total { margin-left: auto; color: var(--muted); font-size: 13px; }

.admin-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.admin-full { width: 100%; }
.admin-tag { margin: 2px 4px 2px 0; }
.admin-dialog-tip { margin-bottom: 16px; }
</style>
