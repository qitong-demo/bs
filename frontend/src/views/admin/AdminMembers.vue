<template>
  <section class="admin-panel">
    <div class="admin-toolbar">
      <span class="admin-toolbar-label">所属站点</span>
      <el-select v-model="stationId" class="admin-toolbar-station" filterable placeholder="选择站点" @change="load">
        <el-option
          v-for="item in stations"
          :key="item.id"
          :label="`${item.stationName}（${item.area || '未填区域'}）`"
          :value="item.id"
        />
      </el-select>
      <el-button type="primary" :disabled="!stationId" @click="openAdd">
        <el-icon><Plus /></el-icon>添加工作人员
      </el-button>
      <span class="admin-toolbar-total">共 {{ rows.length }} 名成员</span>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="工作人员可接待预约、处理诉求、维护服务项目；志愿者只协助预约接待与报名审核。成员需使用已注册账号登录。"
      class="admin-hint"
    />

    <el-table :data="rows" v-loading="loading" stripe empty-text="还没有工作人员或志愿者">
      <el-table-column prop="id" label="编号" width="80" />
      <el-table-column prop="username" label="账号" min-width="140" />
      <el-table-column label="成员角色" width="120">
        <template #default="{ row }">
          <el-tag :type="row.memberRole === 'VOLUNTEER' ? 'info' : 'success'" effect="plain">
            {{ row.memberRoleLabel }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">{{ row.statusLabel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="加入时间" width="170" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="toggle(row)">
            {{ row.status === "ACTIVE" ? "停用" : "启用" }}
          </el-button>
          <el-button size="small" type="danger" @click="remove(row)">移除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="addVisible" title="添加工作人员/志愿者" width="480px">
      <el-form label-position="top">
        <el-form-item label="已有账号" required>
          <el-input v-model="addForm.username" placeholder="输入已注册的用户名" />
        </el-form-item>
        <el-form-item label="成员角色">
          <el-radio-group v-model="addForm.memberRole">
            <el-radio-button value="STAFF">工作人员</el-radio-button>
            <el-radio-button value="VOLUNTEER">志愿者</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <p class="admin-hint">添加后该账号角色会变为工作人员/志愿者，并需要重新登录。</p>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAdd">添加</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { getMyStations } from "../../api/station";
import {
  addStationMember,
  getStationMembers,
  removeStationMember,
  updateStationMemberStatus
} from "../../api/station-member";
import { errorMessage } from "../../api/http";

const stations = ref([]);
const stationId = ref(null);
const rows = ref([]);
const loading = ref(false);
const submitting = ref(false);
const addVisible = ref(false);
const addForm = reactive({ username: "", memberRole: "STAFF" });

async function loadStations() {
  try {
    stations.value = await getMyStations();
    if (!stationId.value && stations.value.length) {
      stationId.value = stations.value[0].id;
    }
  } catch (error) {
    ElMessage.error(errorMessage(error));
  }
}

async function load() {
  if (!stationId.value) return;
  loading.value = true;
  try {
    rows.value = await getStationMembers(stationId.value);
  } catch (error) {
    ElMessage.error(errorMessage(error));
  } finally {
    loading.value = false;
  }
}

function openAdd() {
  addForm.username = "";
  addForm.memberRole = "STAFF";
  addVisible.value = true;
}

async function submitAdd() {
  if (!addForm.username.trim()) {
    ElMessage.warning("请输入已注册的用户名");
    return;
  }
  submitting.value = true;
  try {
    await addStationMember({
      stationId: stationId.value,
      username: addForm.username.trim(),
      memberRole: addForm.memberRole
    });
    addVisible.value = false;
    ElMessage.success("已添加，成员需要重新登录");
    await load();
  } catch (error) {
    ElMessage.error(errorMessage(error, "添加失败"));
  } finally {
    submitting.value = false;
  }
}

async function toggle(row) {
  try {
    await updateStationMemberStatus(row.id, row.status === "ACTIVE" ? "DISABLED" : "ACTIVE");
    ElMessage.success("状态已更新");
    await load();
  } catch (error) {
    ElMessage.error(errorMessage(error));
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认移除「${row.username}」吗？`, "移除成员", { type: "warning" });
  } catch {
    return;
  }
  try {
    await removeStationMember(row.id);
    ElMessage.success("已移除");
    await load();
  } catch (error) {
    ElMessage.error(errorMessage(error));
  }
}

onMounted(async () => {
  await loadStations();
  await load();
});
</script>

<style scoped>
.admin-panel { padding: 16px 18px 18px; background: #fff; border: 1px solid var(--line); border-radius: 6px; }
.admin-toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.admin-toolbar-station { width: 280px; }
.admin-toolbar-total { margin-left: auto; color: var(--muted); font-size: 13px; }
.admin-hint { margin-bottom: 14px; }
</style>
