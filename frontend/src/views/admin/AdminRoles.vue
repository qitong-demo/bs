<template>
  <section class="admin-panel">
    <div class="sub-heading">
      <div>
        <h2>内置角色与数据范围</h2>
        <p class="admin-note">角色与用户数来自角色接口；数据范围为系统约定的权限口径。</p>
      </div>
      <el-button size="small" @click="load"><el-icon><Refresh /></el-icon>刷新</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" stripe empty-text="暂无角色数据">
      <el-table-column prop="id" label="编号" width="80" />
      <el-table-column label="角色编码" width="160">
        <template #default="{ row }">
          <el-tag effect="plain">{{ textOf(row.roleCode) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="角色名称" width="140">
        <template #default="{ row }">{{ textOf(row.roleName) }}</template>
      </el-table-column>
      <el-table-column label="角色说明" min-width="180">
        <template #default="{ row }">{{ textOf(row.remark) }}</template>
      </el-table-column>
      <el-table-column label="账号数量" width="110">
        <template #default="{ row }">{{ numberText(row.userCount) }}</template>
      </el-table-column>
      <el-table-column label="可见标签页" min-width="240">
        <template #default="{ row }">
          <el-tag v-for="tab in tabsOf(row.roleCode)" :key="tab" size="small" effect="light" class="admin-tag">{{ tab }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="数据范围" min-width="360">
        <template #default="{ row }">
          <span class="admin-scope">{{ scopeOf(row.roleCode) }}</span>
        </template>
      </el-table-column>
    </el-table>

    <el-alert
      class="admin-notice"
      type="info"
      :closable="false"
      show-icon
      title="前端按角色隐藏标签页只是体验优化，后端每个接口都带有 @RequireRole 校验，越权请求会被拒绝。"
    />
  </section>
</template>

<script setup>
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { getRoles } from "../../api/admin";
import { errorMessage } from "../../api/http";
import { ROLE_SCOPE, numberText, textOf } from "./admin-shared";

const ROLE_TABS = {
  USER: ["无（不可进入管理工作台）"],
  STATION_ADMIN: ["运营概览", "预约审核", "诉求处理", "活动管理", "报表导出"],
  PLATFORM_ADMIN: ["运营概览", "预约审核", "诉求处理", "驿站管理", "服务项目管理", "资讯公告管理", "活动管理", "账号管理", "角色权限", "报表导出"],
  
};

const loading = ref(false);
const rows = ref([]);

function tabsOf(roleCode) {
  return ROLE_TABS[roleCode] || ["—"];
}

function scopeOf(roleCode) {
  return ROLE_SCOPE[roleCode] || "—";
}

async function load() {
  loading.value = true;
  try {
    const data = await getRoles();
    rows.value = Array.isArray(data) ? data : [];
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

.admin-note {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 12px;
}

.admin-tag { margin: 2px 4px 2px 0; }

.admin-scope {
  color: #4b5563;
  font-size: 13px;
  line-height: 1.6;
}

.admin-notice { margin-top: 16px; }
</style>
