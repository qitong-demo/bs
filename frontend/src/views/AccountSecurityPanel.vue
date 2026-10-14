<script setup>
/**
 * 账号安全面板：改账号 ID、改密码、绑定邮箱。
 *
 * 三处都要求邮箱验证码。改账号 ID 或密码后后端会让所有旧令牌失效，
 * 本组件据此注销本地会话并跳登录页——**必须两边同时生效**：
 * 只清本地令牌的话，旧令牌在过期前仍然可用。
 */
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { errorMessage } from "../api/http";
import { useUserStore } from "../stores/user";
import {
  bindEmail, changePassword, changeUsername,
  getCredentialInfo, getMailStatus, sendVerificationCode
} from "../api/credentials";

const router = useRouter();
const userStore = useUserStore();

const info = reactive({ username: "", email: "", emailBound: false });
const mailConfigured = ref(true);
const mailHint = ref("");
const sending = ref(false);
const submitting = ref(false);
/** 演示环境下后端会带出验证码，便于演示继续走通。 */
const demoCode = ref("");

const dialog = reactive({ visible: false, mode: "email" });
const form = reactive({ email: "", code: "", newUsername: "", oldPassword: "", newPassword: "", confirmPassword: "" });

const TITLES = {
  email: "绑定或更换邮箱",
  username: "修改账号 ID",
  password: "修改密码"
};

async function load() {
  try {
    Object.assign(info, await getCredentialInfo());
  } catch {
    /* 取不到就保持默认值，不阻塞页面 */
  }
  try {
    const status = await getMailStatus();
    mailConfigured.value = Boolean(status?.mailConfigured);
    mailHint.value = status?.hint || "";
  } catch {
    mailConfigured.value = false;
    mailHint.value = "无法确认邮件服务状态，验证码可能不会真实发送。";
  }
}

function open(mode) {
  dialog.mode = mode;
  dialog.visible = true;
  demoCode.value = "";
  Object.assign(form, { email: "", code: "", newUsername: "", oldPassword: "", newPassword: "", confirmPassword: "" });
}

async function sendCode() {
  const purpose = { email: "BIND_EMAIL", username: "CHANGE_USERNAME", password: "CHANGE_PASSWORD" }[dialog.mode];
  sending.value = true;
  try {
    const result = await sendVerificationCode(purpose, dialog.mode === "email" ? form.email : undefined);
    demoCode.value = result?.demoCode || "";
    if (result?.delivered) {
      ElMessage.success("验证码已发送，请查收邮件");
    } else {
      // 如实告知：演示环境没有真实发送，验证码在返回值与后端日志里
      ElMessage.warning(result?.demoHint || "演示环境未真实发送邮件，验证码见后端控制台");
    }
  } catch (error) {
    ElMessage.error(errorMessage(error, "验证码发送失败"));
  } finally {
    sending.value = false;
  }
}

/** 改完凭据后注销本地会话并跳登录页；后端的令牌已失效，两边一致。 */
function forceRelogin(message) {
  ElMessage.success(`${message}，请用新信息重新登录`);
  userStore.logout?.();
  localStorage.clear();
  sessionStorage.clear();
  router.push("/login");
}

async function submit() {
  submitting.value = true;
  try {
    if (dialog.mode === "email") {
      await bindEmail(form.email, form.code);
      ElMessage.success("邮箱已绑定");
      dialog.visible = false;
      await load();
      return;
    }
    if (dialog.mode === "username") {
      const result = await changeUsername(form.newUsername, form.code);
      dialog.visible = false;
      if (result?.requireRelogin) {
        forceRelogin("账号 ID 已修改");
      } else {
        await load();
      }
      return;
    }
    const result = await changePassword(form.oldPassword, form.newPassword, form.confirmPassword, form.code);
    dialog.visible = false;
    if (result?.requireRelogin) {
      forceRelogin("密码已修改");
    }
  } catch (error) {
    ElMessage.error(errorMessage(error, "操作失败"));
  } finally {
    submitting.value = false;
  }
}

onMounted(load);
</script>

<template>
  <section class="panel">
    <div class="sub-heading">
      <h2>账号安全</h2>
      <span>修改账号 ID、密码或绑定邮箱；三项都需要邮箱验证码</span>
    </div>

    <!-- 未配置邮件服务时必须显著提示，否则用户会一直等一封不会来的邮件。
         标题与描述分开写：后端的 hint 本身已含"未配置"字样，直接用会重复。 -->
    <el-alert
      v-if="!mailConfigured"
      type="warning"
      show-icon
      :closable="false"
      class="security-alert"
      title="验证码不会真实发送"
      :description="mailHint || '演示环境未配置邮件服务，请查看后端控制台日志；接口返回值里也会带出验证码。'"
    />

    <el-descriptions :column="2" border size="small" class="security-meta">
      <el-descriptions-item label="账号 ID">{{ info.username || "—" }}</el-descriptions-item>
      <el-descriptions-item label="绑定邮箱">
        <span v-if="info.emailBound">{{ info.email }}</span>
        <span v-else class="muted">未绑定</span>
      </el-descriptions-item>
    </el-descriptions>

    <div class="security-actions">
      <el-button @click="open('email')">{{ info.emailBound ? "更换邮箱" : "绑定邮箱" }}</el-button>
      <el-button @click="open('username')">修改账号 ID</el-button>
      <el-button @click="open('password')">修改密码</el-button>
    </div>
    <p class="form-hint">
      修改账号 ID 或密码后，<strong>当前登录会立即失效并需要重新登录</strong>——
      这不是前端清一下本地令牌，后端会让所有已签发的令牌同时失效。
    </p>

    <el-dialog v-model="dialog.visible" :title="TITLES[dialog.mode]" width="480px">
      <el-form label-position="top">
        <template v-if="dialog.mode === 'email'">
          <el-form-item label="新邮箱" required>
            <el-input v-model="form.email" placeholder="例如：rider@example.com" />
          </el-form-item>
        </template>
        <template v-else-if="dialog.mode === 'username'">
          <el-form-item label="新账号 ID" required>
            <el-input v-model="form.newUsername" placeholder="4~20 位，字母开头，可用字母数字下划线" />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="当前密码" required>
            <el-input v-model="form.oldPassword" type="password" show-password placeholder="请输入当前密码" />
          </el-form-item>
          <el-form-item label="新密码" required>
            <el-input v-model="form.newPassword" type="password" show-password placeholder="8~32 位，需含字母与数字" />
          </el-form-item>
          <el-form-item label="确认新密码" required>
            <el-input v-model="form.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
          </el-form-item>
        </template>

        <el-form-item label="邮箱验证码" required>
          <div class="code-row">
            <el-input v-model="form.code" placeholder="6 位数字" maxlength="6" />
            <el-button :loading="sending" @click="sendCode">获取验证码</el-button>
          </div>
          <p v-if="demoCode" class="field-hint">
            演示环境未真实发送邮件，本次验证码为 <strong>{{ demoCode }}</strong>
          </p>
          <p v-else-if="dialog.mode !== 'email' && !info.emailBound" class="field-hint">
            当前账号尚未绑定邮箱，请先绑定后再修改账号 ID 或密码。
          </p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.security-alert {
  margin-bottom: 14px;
}

.security-meta {
  margin-bottom: 14px;
}

.security-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.code-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
</style>
