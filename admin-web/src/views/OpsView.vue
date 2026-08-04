<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">运维中心</h2>
      <el-button :loading="loading" @click="refreshAll">刷新全部</el-button>
    </div>

    <el-row :gutter="12">
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="block">
          <template #header>版本信息</template>
          <pre class="mono json">{{ pretty(version) }}</pre>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="block">
          <template #header>健康检查</template>
          <pre class="mono json">{{ pretty(health) }}</pre>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="block">
          <template #header>指标摘要</template>
          <pre class="mono json">{{ pretty(metrics) }}</pre>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="block">
          <template #header>缓存</template>
          <pre class="mono json">{{ pretty(cache) }}</pre>
          <el-button type="danger" style="margin-top: 10px" @click="evictCache">清理用户套餐缓存</el-button>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="block">
      <template #header>
        <div class="card-header">
          <span>日志级别</span>
          <div class="actions">
            <el-button size="small" @click="applyPreset('development')">开发预设</el-button>
            <el-button size="small" @click="applyPreset('production')">生产预设</el-button>
            <el-button size="small" @click="applyPreset('debugging')">排障预设</el-button>
          </div>
        </div>
      </template>
      <el-table :data="loggerRows" size="small" border>
        <el-table-column prop="name" label="Logger" min-width="260" />
        <el-table-column prop="level" label="当前级别" width="120" />
        <el-table-column label="操作" width="280">
          <template #default="{ row }">
            <el-select v-model="row.nextLevel" size="small" style="width: 110px">
              <el-option v-for="lv in levels" :key="lv" :label="lv" :value="lv" />
            </el-select>
            <el-button size="small" type="primary" link @click="setLogger(row)">设置</el-button>
            <el-button size="small" link @click="resetOne(row.name)">重置</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="block">
      <template #header>AI 错误分析（已脱敏）</template>
      <el-input
        v-model="errorLog"
        type="textarea"
        :rows="6"
        placeholder="粘贴异常日志（勿包含明文密钥）"
      />
      <el-input
        v-model="contextInfo"
        style="margin-top: 8px"
        placeholder="上下文（可选）"
      />
      <el-button type="primary" style="margin-top: 10px" :loading="analyzing" @click="runAnalyze">
        分析
      </el-button>
      <pre v-if="analysis" class="mono json" style="margin-top: 12px">{{ pretty(analysis) }}</pre>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  analyzeError,
  applyLoggerPreset,
  evictUserPackageCache,
  getOpsCacheSummary,
  getOpsHealth,
  getOpsMetrics,
  getOpsVersion,
  listLoggers,
  resetLogger,
  updateLogger,
} from '@/api/admin'
import { showApiError } from '@/utils/request'

const loading = ref(false)
const analyzing = ref(false)
const version = ref<Record<string, unknown>>({})
const health = ref<Record<string, unknown>>({})
const metrics = ref<Record<string, unknown>>({})
const cache = ref<Record<string, unknown>>({})
const loggers = ref<Record<string, string>>({})
const errorLog = ref('')
const contextInfo = ref('')
const analysis = ref<Record<string, unknown> | null>(null)
const levels = ['DEBUG', 'INFO', 'WARN', 'ERROR']

const loggerRows = computed(() =>
  Object.entries(loggers.value).map(([name, level]) => ({
    name,
    level,
    nextLevel: level === 'INHERITED' ? 'INFO' : level,
  })),
)

function pretty(data: unknown) {
  return JSON.stringify(data || {}, null, 2)
}

async function refreshAll() {
  loading.value = true
  try {
    const [v, h, m, c, l] = await Promise.all([
      getOpsVersion(),
      getOpsHealth(),
      getOpsMetrics(),
      getOpsCacheSummary(),
      listLoggers(),
    ])
    version.value = v as any
    health.value = h as any
    metrics.value = m as any
    cache.value = c as any
    loggers.value = l as any
  } catch (e) {
    showApiError(e, '加载运维数据失败')
  } finally {
    loading.value = false
  }
}

async function evictCache() {
  try {
    const { value } = await ElMessageBox.prompt('清理原因', '清理用户套餐缓存', {
      inputPlaceholder: 'reason',
    })
    await evictUserPackageCache(value)
    ElMessage.success('缓存清理完成')
  } catch (e) {
    if (e !== 'cancel') showApiError(e)
  }
}

async function setLogger(row: { name: string; nextLevel: string }) {
  try {
    const { value } = await ElMessageBox.prompt('修改原因', `设置 ${row.name}`)
    await updateLogger(row.name, row.nextLevel, value)
    ElMessage.success('日志级别已更新')
    loggers.value = await listLoggers()
  } catch (e) {
    if (e !== 'cancel') showApiError(e)
  }
}

async function resetOne(name: string) {
  try {
    const { value } = await ElMessageBox.prompt('重置原因', `重置 ${name}`)
    await resetLogger(name, value)
    ElMessage.success('已重置')
    loggers.value = await listLoggers()
  } catch (e) {
    if (e !== 'cancel') showApiError(e)
  }
}

async function applyPreset(preset: string) {
  try {
    const { value } = await ElMessageBox.prompt('应用预设原因', `预设: ${preset}`)
    loggers.value = (await applyLoggerPreset(preset, value)) as any
    ElMessage.success('预设已应用')
  } catch (e) {
    if (e !== 'cancel') showApiError(e)
  }
}

async function runAnalyze() {
  if (!errorLog.value.trim()) {
    ElMessage.warning('请先填写异常日志')
    return
  }
  analyzing.value = true
  try {
    analysis.value = (await analyzeError(errorLog.value, contextInfo.value || undefined)) as any
  } catch (e) {
    showApiError(e, '分析失败')
  } finally {
    analyzing.value = false
  }
}

onMounted(refreshAll)
</script>

<style scoped>
.block {
  margin-bottom: 12px;
  border: 1px solid #e5e7eb;
}
.json {
  margin: 0;
  max-height: 260px;
  overflow: auto;
  font-size: 12px;
  background: #fafafa;
  padding: 10px;
  border-radius: 6px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
</style>
