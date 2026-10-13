<template>
  <main class="login-page">
    <section class="login-aside">
      <div class="login-topline"><span class="login-brand-mark"><el-icon><House /></el-icon></span><span>新就业群体友好驿站服务与诉求响应平台</span></div>
      <div class="login-aside-copy">
        <p class="login-eyebrow">FRIENDLY STATION PLATFORM</p>
        <h1>每一次奔波，<br />都有地方停靠。</h1>
        <p>驿站服务、诉求响应和关爱活动，集中在一个清晰、可靠的工作台。</p>
      </div>
      <div class="login-service-strip"><span><i></i> 驿站查询与预约</span><span><i></i> 诉求闭环与跟踪</span></div>
      <div class="login-aside-footer"><span>服务与诉求响应平台</span><span>2026</span></div>
    </section>
    <section class="login-panel">
      <div class="login-card">
        <div class="login-heading">
          <span class="login-kicker">WORKSPACE ACCESS</span>
          <h2>进入服务工作台</h2>
          <p>登录后查看附近驿站、预约服务和诉求进度。</p>
        </div>
        <el-form :model="form" @submit.prevent="submit">
          <el-form-item label="账号">
            <el-input v-model="form.username" size="large" placeholder="请输入用户名" autocomplete="username">
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" size="large" type="password" show-password placeholder="请输入密码" autocomplete="current-password" @keyup.enter="submit">
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-button class="login-submit" type="primary" size="large" :loading="loading" @click="submit">登录</el-button>
        </el-form>
        <div class="demo-account">
          <div class="demo-account-heading"><span>快速进入演示环境</span><small>点击角色自动填充账号</small></div>
          <div class="demo-actions">
            <button type="button" :class="{ active: form.username === 'rider_xiaowang' }" @click="useDemo('rider_xiaowang')"><el-icon><User /></el-icon><span>普通用户</span><small>浏览服务</small></button>
            <button type="button" :class="{ active: form.username === 'station_admin_01' }" @click="useDemo('station_admin_01')"><el-icon><Setting /></el-icon><span>驿站管理员</span><small>管理驿站</small></button>
            <button type="button" :class="{ active: form.username === 'platform_admin' }" @click="useDemo('platform_admin')"><el-icon><Monitor /></el-icon><span>平台管理员</span><small>运营工作台</small></button>
          </div>
          <p class="demo-hint"><el-icon><InfoFilled /></el-icon> 演示账号密码均为 <b>password</b></p>
        </div>
      </div>
    </section>
  </main>
</template>

<script setup>
import { ref } from "vue";
import { ElMessage } from "element-plus";
import { useRoute, useRouter } from "vue-router";
import { login } from "../api/auth";
import { useUserStore } from "../stores/user";

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();
const loading = ref(false);
const form = ref({ username: "rider_xiaowang", password: "password" });

function useDemo(username) {
  form.value.username = username;
  form.value.password = "password";
}

async function submit() {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning("请输入用户名和密码");
    return;
  }
  loading.value = true;
  try {
    const session = await login(form.value);
    userStore.setSession(session);
    ElMessage.success("登录成功");
    router.replace(route.query.redirect || "/");
  } catch (error) {
    ElMessage.error(error.userMessage || error.response?.data?.message || "登录失败，请稍后重试");
  } finally {
    loading.value = false;
  }
}
</script>
