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
          <span class="user">{{ auth.displayName }}</span>
          <el-button size="small" @click="onLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

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
.main {
  background: #f5f6f8;
  padding: 0;
}
</style>
