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
      <!-- 子账号：角色同样是管理员，能做哪些事由勾选的权限决定 -->
      <el-button type="success" class="admin-toolbar-action" @click="openCreate">
        <el-icon><Plus /></el-icon>新建子账号
      </el-button>
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
          <el-button size="small" @click="openPermissions(row)">分发权限</el-button>
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

    <!-- 新建子账号：一次性填账号信息并勾选权限 -->
    <el-dialog v-model="createVisible" title="新建子账号" width="620px">
      <el-alert
        type="info"
        show-icon
        :closable="false"
        class="admin-dialog-hint"
        title="子账号的角色同样是管理员，但只能做被勾选的事"
        description="「账号管理」不可授予子账号——否则子账号能给自己加权限。创建后对方用这里填的账号密码登录管理工作台。"
      />
      <el-form label-position="top">
        <el-form-item label="账号 ID" required>
          <el-input v-model="createForm.username" placeholder="4~20 位，字母开头，可用字母数字下划线" />
        </el-form-item>
        <el-form-item label="手机号" required>
          <el-input v-model="createForm.phone" placeholder="用于账号识别" />
        </el-form-item>
        <el-form-item label="初始密码" required>
          <el-input v-model="createForm.password" type="password" show-password placeholder="8~32 位，需含字母与数字" />
        </el-form-item>
        <el-form-item label="功能权限" required>
          <el-checkbox-group v-model="createForm.permissions" class="permission-grid">
            <el-checkbox
              v-for="item in catalog"
              :key="item.permissionCode"
              :value="item.permissionCode"
              :disabled="!item.grantable"
            >
              {{ item.permissionName }}
              <span v-if="!item.grantable" class="muted">（仅管理员本人）</span>
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <!-- 二次确认：管理员账号被盗后最省事的破坏就是批量建号 -->
        <el-form-item label="你的登录密码" required>
          <el-input v-model="createForm.adminPassword" type="password" show-password placeholder="确认本次操作，防止账号被盗后被人建号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="permissionVisible" title="分发功能权限" width="620px">
      <el-descriptions v-if="current" :column="1" border size="small" class="admin-dialog-meta">
        <el-descriptions-item label="账号">{{ textOf(current.username) }}</el-descriptions-item>
        <el-descriptions-item label="当前角色">{{ textOf(current.roleName) }}</el-descriptions-item>
      </el-descriptions>
      <el-alert
        v-if="isStationAdmin"
        type="info"
        show-icon
        :closable="false"
        class="admin-dialog-hint"
        title="驿站管理员不走权限表"
        description="驿站管理员的标签由角色固定（预约审核、诉求处理、活动管理、报名记录、变更审批、报表导出），不需要逐个勾选。此处设置仅对管理员子账号生效。"
      />
      <el-checkbox-group v-model="permissionForm.permissions" class="permission-grid">
        <el-checkbox
          v-for="item in catalog"
          :key="item.permissionCode"
          :value="item.permissionCode"
          :disabled="!item.grantable"
        >
          {{ item.permissionName }}
          <span v-if="!item.grantable" class="muted">（仅管理员本人）</span>
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="permissionVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitPermissions">保存权限</el-button>
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
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  getAdminUsers,
  updateAdminUser,
  updateAdminUserRoles,
  updateAdminUserStatus
} from "../../api/admin";
import { errorMessage } from "../../api/http";
import { JOB_TYPES } from "../../utils/profile-options";
import {
  createSubAccount, getPermissionCatalog, getUserPermissions, grantPermissions
} from "../../api/admin";
import { ROLE_OPTIONS, textOf, userStatusLabel, userStatusTag } from "./admin-shared";

// 与管理端共用同一份群体类型清单，避免两处各写一份而漂移
const jobTypes = JOB_TYPES;

const loading = ref(false);

// ---------------------------------------------------------------- 子账号与权限

const catalog = ref([]);
const createVisible = ref(false);
const permissionVisible = ref(false);
const isStationAdmin = computed(() => current.value?.role === "STATION_ADMIN");
const createForm = reactive({
  username: "", phone: "", password: "", adminPassword: "", permissions: []
});
const permissionForm = reactive({ permissions: [] });

async function loadCatalog() {
  try {
    catalog.value = (await getPermissionCatalog()) || [];
  } catch {
    catalog.value = [];
  }
}

function openCreate() {
  Object.assign(createForm, { username: "", phone: "", password: "", adminPassword: "", permissions: [] });
  createVisible.value = true;
}

async function submitCreate() {
  submitting.value = true;
  try {
    await createSubAccount({ ...createForm });
    createVisible.value = false;
    ElMessage.success("子账号已创建，对方可用该账号登录管理工作台");
    await load();
  } catch (error) {
    ElMessage.error(errorMessage(error, "创建失败"));
  } finally {
    submitting.value = false;
  }
}

async function openPermissions(row) {
  current.value = row;
  try {
    const data = await getUserPermissions(row.id);
    permissionForm.permissions = data?.permissions || [];
  } catch {
    permissionForm.permissions = [];
  }
  permissionVisible.value = true;
}

async function submitPermissions() {
  submitting.value = true;
  try {
    await grantPermissions(current.value.id, permissionForm.permissions);
    permissionVisible.value = false;
    ElMessage.success("权限已保存，对方下次进入管理端即按新权限显示");
    await load();
  } catch (error) {
    ElMessage.error(errorMessage(error, "保存失败"));
  } finally {
    submitting.value = false;
  }
}
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

onMounted(() => {
  loadCatalog();
  load();
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
