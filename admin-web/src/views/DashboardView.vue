<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">系统概览</h2>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="card-grid" v-loading="loading">
      <div class="metric-card">
        <div class="metric-label">用户总数</div>
        <div class="metric-value">{{ users.total ?? '-' }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">启用用户</div>
        <div class="metric-value">{{ users.enabled ?? '-' }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">管理员</div>
        <div class="metric-value">{{ users.admins ?? '-' }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">订单总数</div>
        <div class="metric-value">{{ orders.total ?? '-' }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">已支付</div>
        <div class="metric-value">{{ orders.paid ?? '-' }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">待支付</div>
        <div class="metric-value">{{ orders.pending ?? '-' }}</div>
      </div>
    </div>

    <el-card class="block" shadow="never">
      <template #header>依赖健康</template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="总体状态">
          <el-tag :type="healthStatusType">{{ health.status || '-' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="检查时间">{{ health.checkedAt || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="componentRows" style="margin-top: 12px" size="small">
        <el-table-column prop="name" label="组件" width="140" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="latencyMs" label="耗时(ms)" width="100" />
        <el-table-column prop="message" label="说明" />
      </el-table>

      <div v-if="aiModelRows.length" style="margin-top: 16px">
        <div style="margin-bottom: 8px; font-weight: 600">AI 模型实时探测（与模型页「配置启用」不同）</div>
        <el-table :data="aiModelRows" size="small" border>
          <el-table-column prop="modelCode" label="模型" min-width="160" />
          <el-table-column prop="status" label="探测" width="100" />
          <el-table-column prop="latencyMs" label="耗时(ms)" width="100" />
          <el-table-column prop="timedOut" label="超时" width="80">
            <template #default="{ row }">{{ row.timedOut ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column prop="message" label="说明" min-width="220" />
        </el-table>
      </div>
    </el-card>

    <el-card class="block" shadow="never">
      <template #header>近 7 日支付摘要</template>
      <pre class="json mono">{{ paymentJson }}</pre>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getDashboardOverview } from '@/api/admin'
import { showApiError } from '@/utils/request'

const loading = ref(false)
const overview = ref<Record<string, any>>({})

const users = computed(() => overview.value.users || {})
const orders = computed(() => overview.value.orders || {})
const health = computed(() => overview.value.health || {})
const paymentJson = computed(() => JSON.stringify(overview.value.paymentLast7Days || {}, null, 2))
const healthStatusType = computed(() => {
  const s = health.value.status
  if (s === 'UP') return 'success'
  if (s === 'DEGRADED') return 'warning'
  return 'danger'
})
const componentRows = computed(() => {
  const components = health.value.components || {}
  return Object.keys(components).map((name) => ({
    name,
    status: components[name]?.status,
    latencyMs: components[name]?.latencyMs,
    message: components[name]?.message || components[name]?.ping || '',
  }))
})

const aiModelRows = computed(() => {
  const ai = (health.value.components || {}).aiApi || {}
  return Array.isArray(ai.models) ? ai.models : []
})

async function load() {
  loading.value = true
  try {
    overview.value = await getDashboardOverview()
  } catch (e) {
    showApiError(e, '加载概览失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.block {
  margin-top: 16px;
  border: 1px solid #e5e7eb;
}
.json {
  margin: 0;
  max-height: 280px;
  overflow: auto;
  font-size: 12px;
  background: #fafafa;
  padding: 12px;
  border-radius: 6px;
}
</style>
