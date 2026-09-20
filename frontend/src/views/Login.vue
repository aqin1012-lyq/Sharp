<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="brand">
        <div class="glyph"><el-icon><Lock /></el-icon></div>
        <div class="brand-name display">Sharp</div>
      </div>

      <div class="tabs">
        <button :class="{ on: mode === 'login' }" @click="mode = 'login'">登录</button>
        <button :class="{ on: mode === 'register' }" @click="mode = 'register'">注册</button>
      </div>

      <el-form label-position="top" class="form" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="username" placeholder="用户名" @keyup.enter="submit" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" show-password placeholder="密码" @keyup.enter="submit" />
        </el-form-item>
        <el-form-item v-if="mode === 'register'" label="邀请码">
          <el-input v-model="inviteCode" placeholder="注册需邀请码" @keyup.enter="submit" />
        </el-form-item>

        <el-button type="primary" class="submit" :loading="loading" @click="submit">
          {{ mode === 'login' ? '登录' : '注册并登录' }}
        </el-button>
      </el-form>

      <p class="hint">{{ mode === 'login' ? '没有账号？点上方「注册」（需邀请码）。' : '注册需管理员提供的邀请码。' }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'
import { loginApi, registerApi } from '../api/session'
import { setAuth } from '../api/auth'

const router = useRouter()
const route = useRoute()
const mode = ref('login')
const username = ref('')
const password = ref('')
const inviteCode = ref('')
const loading = ref(false)

async function submit() {
  if (!username.value || !password.value) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  if (mode.value === 'register' && !inviteCode.value) {
    ElMessage.warning('请输入邀请码')
    return
  }
  loading.value = true
  try {
    const call = mode.value === 'login'
      ? loginApi({ username: username.value, password: password.value })
      : registerApi({ username: username.value, password: password.value, inviteCode: inviteCode.value })
    const res = await call
    setAuth(res.data.token, res.data.username)
    ElMessage.success(mode.value === 'login' ? '登录成功' : '注册成功')
    const redirect = route.query.redirect || '/email'
    router.replace(redirect)
  } catch {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: var(--bg, #f5f6f8);
  padding: 20px;
}
.login-card {
  width: 100%;
  max-width: 360px;
  background: var(--surface, #fff);
  border: 1px solid var(--border, #e5e7eb);
  border-radius: 16px;
  box-shadow: var(--shadow, 0 8px 30px rgba(0,0,0,.06));
  padding: 28px 26px;
}
.brand { display: flex; align-items: center; gap: 10px; justify-content: center; margin-bottom: 18px; }
.glyph {
  width: 38px; height: 38px; border-radius: 10px;
  display: grid; place-items: center;
  background: var(--accent-soft); color: var(--accent); font-size: 18px;
}
.brand-name { font-size: 24px; color: var(--ink); }
.tabs { display: flex; gap: 8px; margin-bottom: 18px; }
.tabs button {
  flex: 1; padding: 8px 0; border-radius: 8px; cursor: pointer;
  border: 1px solid var(--border-strong, #d1d5db); background: var(--surface-2, #f3f4f6);
  color: var(--muted); font-size: 13.5px;
}
.tabs button.on { background: var(--accent-soft); border-color: var(--accent); color: var(--accent); font-weight: 600; }
.submit { width: 100%; margin-top: 4px; }
.hint { margin: 14px 0 0; font-size: 12px; color: var(--faint); text-align: center; line-height: 1.6; }
</style>
