<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="brand">
        <div class="brand-title">金语管理后台</div>
        <div class="brand-sub">chat.instser.cn/admin</div>
      </div>
      <el-menu
        :default-active="active"
        router
        background-color="#111827"
        text-color="#d1d5db"
        active-text-color="#ffffff"
      >
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <span class="env-tag">8080 /api</span>
          <span class="header-title">{{ currentTitle }}</span>
        </div>
        <div class="header-right">
          <el-dropdown trigger="click" @command="onUserCommand">
            <span class="user-trigger">
              <span class="user">{{ auth.displayName }}</span>
              <el-icon class="caret"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>

    <el-dialog v-model="pwdVisible" title="修改密码" width="480px" @closed="resetPwdForm">
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="100px">
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入旧密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="不少于 6 位" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="pwdForm.confirmPassword"
            type="password"
            show-password
            placeholder="请再次输入新密码"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSaving" @click="submitPassword">确定</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { changePassword } from '@/api/auth'
import { showApiError } from '@/utils/request'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const menus = [
  { path: '/dashboard', title: '概览' },
  { path: '/monitoring', title: '运维监控' },
  { path: '/users', title: '用户管理' },
  { path: '/packages', title: '套餐管理' },
  { path: '/models', title: '模型管理' },
  { path: '/orders', title: '订单支付' },
  { path: '/audits', title: '审计中心' },
  { path: '/ops', title: '运维中心' },
  { path: '/logs', title: '日志查看' },
]

const active = computed(() => route.path)
const currentTitle = computed(() => (route.meta.title as string) || '管理后台')

async function onLogout() {
  await auth.logout()
  router.replace('/login')
}

function onUserCommand(command: string) {
  if (command === 'password') {
    pwdVisible.value = true
  } else if (command === 'logout') {
    onLogout()
  }
}

// ===== 修改密码 =====

const pwdVisible = ref(false)
const pwdSaving = ref(false)
const pwdFormRef = ref<FormInstance>()
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '新密码不少于 6 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== pwdForm.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

function resetPwdForm() {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  pwdFormRef.value?.clearValidate()
}

async function submitPassword() {
  const valid = await pwdFormRef.value?.validate().catch(() => false)
  if (!valid) return
  pwdSaving.value = true
  try {
    await changePassword(pwdForm.oldPassword, pwdForm.newPassword)
    ElMessage.success('密码修改成功')
    pwdVisible.value = false
    resetPwdForm()
  } catch (e) {
    showApiError(e, '修改密码失败')
  } finally {
    pwdSaving.value = false
  }
}
</script>

<style scoped>
.layout {
  min-height: 100vh;
}
.aside {
  background: #111827;
  color: #fff;
}
.brand {
  padding: 18px 16px 12px;
  border-bottom: 1px solid #1f2937;
}
.brand-title {
  font-weight: 700;
  font-size: 15px;
}
.brand-sub {
  margin-top: 4px;
  color: #9ca3af;
  font-size: 12px;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  height: 56px;
}
.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.env-tag {
  font-size: 12px;
  color: #2563eb;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  border-radius: 999px;
  padding: 2px 8px;
}
.header-title {
  font-weight: 600;
}
.user {
  color: #4b5563;
  font-size: 13px;
}
.user-trigger {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  outline: none;
}
.caret {
  font-size: 12px;
  color: #9ca3af;
}
.main {
  background: #f5f6f8;
  padding: 0;
}
</style>
