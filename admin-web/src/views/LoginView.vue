<template>
  <div class="login-page">
    <el-card class="login-card" shadow="never">
      <h1>管理员登录</h1>
      <p class="hint">使用 t_user 管理员账号，JWT Cookie 鉴权</p>
      <el-form :model="form" @submit.prevent="onSubmit">
        <el-form-item label="用户名/邮箱">
          <el-input v-model="form.usernameOrEmail" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-button type="primary" style="width: 100%" :loading="auth.loading" native-type="submit">
          登录
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { showApiError } from '@/utils/request'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const form = reactive({
  usernameOrEmail: '',
  password: '',
})

async function onSubmit() {
  try {
    await auth.login(form.usernameOrEmail.trim(), form.password)
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.replace(redirect)
  } catch (e) {
    showApiError(e, '登录失败')
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: linear-gradient(180deg, #eef2ff 0%, #f5f6f8 60%);
}
.login-card {
  width: min(420px, calc(100vw - 32px));
  border: 1px solid #e5e7eb;
}
h1 {
  margin: 0 0 8px;
  font-size: 20px;
}
.hint {
  margin: 0 0 20px;
  color: #6b7280;
  font-size: 13px;
}
</style>
