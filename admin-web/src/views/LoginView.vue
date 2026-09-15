<template>
  <div class="login-page">
    <el-card class="login-card" shadow="never">
      <h1>管理员登录</h1>
      <p class="hint">管理员账号登录，与用户前台会话相互独立</p>
      <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="onSubmit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
            autocomplete="current-password"
          />
        </el-form-item>
        <el-button type="primary" style="width: 100%" :loading="auth.loading" native-type="submit">
          登录
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { showApiError } from '@/utils/request'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const formRef = ref<FormInstance>()

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  try {
    await auth.login(form.username.trim(), form.password)
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
