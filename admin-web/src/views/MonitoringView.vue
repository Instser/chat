<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">运维监控</h2>
        <div class="sub">
          进程内快照（类似 Grafana 轻量面板）· 更新于 {{ generatedAt || '-' }}
          <span v-if="healthStatus" class="health-pill" :class="healthClass">{{ healthStatus }}</span>
        </div>
      </div>
      <div class="actions">
        <el-switch v-model="autoRefresh" active-text="自动刷新" />
        <el-button :loading="loading" type="primary" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="card-grid" v-loading="loading">
      <div class="metric-card">
        <div class="metric-label">AI 调用成功/失败</div>
        <div class="metric-value">{{ aiSuccess }} / {{ aiFailed }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">HTTP 请求总量</div>
        <div class="metric-value">{{ httpTotal }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">堆内存(MB)</div>
        <div class="metric-value">{{ heapUsed }} / {{ heapMax }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">近7日支付成功</div>
        <div class="metric-value">{{ paid7d }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">模型探测 UP</div>
        <div class="metric-value">{{ modelUp }} / {{ modelTotal }}</div>
      </div>
    </div>

    <el-row :gutter="12" class="charts">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="block">
          <template #header>AI 调用（成功/失败）</template>
          <div ref="aiPieRef" class="chart"></div>
          <div class="hint">来源：Micrometer ai_calls_total · 进程累计</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="block">
          <template #header>AI 按模型耗时 / TTFT</template>
          <div ref="aiBarRef" class="chart"></div>
          <div class="hint">来源：ai_call_duration_seconds / ai_ttft_seconds</div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="block">
          <template #header>HTTP 请求 Top 路径</template>
          <div ref="httpBarRef" class="chart"></div>
          <div class="hint">来源：api_requests_total（业务拦截器）</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="block">
          <template #header>HTTP 状态码分布</template>
          <div ref="httpStatusRef" class="chart"></div>
          <div class="hint">空态表示尚无采样请求</div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="block">
          <template #header>系统资源</template>
          <div ref="systemBarRef" class="chart"></div>
          <div class="hint">JVM 堆/线程 + DB 连接池快照</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="block">
          <template #header>支付 / 订单（近 7 日收入）</template>
          <div ref="paymentLineRef" class="chart"></div>
          <div class="hint">来源：PaymentAnalytics 日收入 + 订单状态</div>
        </el-card>
      </el-col>

      <el-col :xs="24">
        <el-card shadow="never" class="block">
          <template #header>模型实时连通探测</template>
          <div ref="modelBarRef" class="chart"></div>
          <el-table :data="modelRows" size="small" border style="margin-top: 12px">
            <el-table-column prop="modelCode" label="模型" min-width="160" />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column prop="latencyMs" label="耗时(ms)" width="100" />
            <el-table-column prop="timedOut" label="超时" width="80">
              <template #default="{ row }">{{ row.timedOut ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column prop="probeMode" label="模式" width="180" />
            <el-table-column prop="message" label="说明" min-width="240" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { ECharts } from 'echarts'
import { getOpsMonitoring } from '@/api/admin'
import { showApiError } from '@/utils/request'

const loading = ref(false)
const autoRefresh = ref(true)
const data = ref<Record<string, any>>({})
let timer: number | undefined

const aiPieRef = ref<HTMLDivElement | null>(null)
const aiBarRef = ref<HTMLDivElement | null>(null)
const httpBarRef = ref<HTMLDivElement | null>(null)
const httpStatusRef = ref<HTMLDivElement | null>(null)
const systemBarRef = ref<HTMLDivElement | null>(null)
const paymentLineRef = ref<HTMLDivElement | null>(null)
const modelBarRef = ref<HTMLDivElement | null>(null)

const charts: ECharts[] = []

const generatedAt = computed(() => data.value.generatedAt || '')
const healthStatus = computed(() => data.value.health?.status || '')
const healthClass = computed(() => {
  const s = healthStatus.value
  if (s === 'UP') return 'ok'
  if (s === 'DEGRADED') return 'warn'
  return 'bad'
})

const aiSuccess = computed(() => data.value.ai?.totals?.callsSuccess ?? 0)
const aiFailed = computed(() => data.value.ai?.totals?.callsFailed ?? 0)
const httpTotal = computed(() => data.value.http?.totalRequests ?? 0)
const heapUsed = computed(() => data.value.system?.jvm?.heapUsedMb ?? '-')
const heapMax = computed(() => data.value.system?.jvm?.heapMaxMb ?? '-')
const paid7d = computed(() => data.value.payment?.last7Days?.successfulOrders ?? 0)
const modelRows = computed(() => {
  const models = data.value.models?.models
  return Array.isArray(models) ? models : []
})
const modelUp = computed(() => modelRows.value.filter((m: any) => m.status === 'UP').length)
const modelTotal = computed(() => modelRows.value.length)

function ensureChart(el: HTMLDivElement | null): ECharts | null {
  if (!el) return null
  let chart = echarts.getInstanceByDom(el)
  if (!chart) {
    chart = echarts.init(el)
    charts.push(chart)
  }
  return chart
}

function emptyOption(title: string) {
  return {
    title: {
      text: title,
      left: 'center',
      top: 'center',
      textStyle: { color: '#9ca3af', fontSize: 13, fontWeight: 400 },
    },
  }
}

function renderCharts() {
  // AI pie
  const aiPie = ensureChart(aiPieRef.value)
  const statusPie = data.value.ai?.statusPie || []
  if (aiPie) {
    if (!statusPie.length || statusPie.every((x: any) => !Number(x.value))) {
      aiPie.setOption(emptyOption('暂无 AI 调用采样'), true)
    } else {
      aiPie.setOption(
        {
          tooltip: { trigger: 'item' },
          legend: { bottom: 0 },
          series: [
            {
              type: 'pie',
              radius: ['40%', '68%'],
              data: statusPie.map((x: any) => ({ name: x.name, value: Number(x.value) || 0 })),
              label: { formatter: '{b}: {c}' },
            },
          ],
        },
        true,
      )
    }
  }

  // AI model bars
  const aiBar = ensureChart(aiBarRef.value)
  const modelBars = data.value.ai?.modelBars || []
  if (aiBar) {
    if (!modelBars.length) {
      aiBar.setOption(emptyOption('暂无按模型耗时数据'), true)
    } else {
      const names = modelBars.map((m: any) => m.name)
      aiBar.setOption(
        {
          tooltip: { trigger: 'axis' },
          legend: { data: ['调用次数', 'TTFT均值(ms)', '总耗时均值(ms)'] },
          grid: { left: 40, right: 16, top: 36, bottom: 48 },
          xAxis: { type: 'category', data: names, axisLabel: { rotate: 25 } },
          yAxis: { type: 'value' },
          series: [
            {
              name: '调用次数',
              type: 'bar',
              data: modelBars.map((m: any) => Number(m.calls) || 0),
            },
            {
              name: 'TTFT均值(ms)',
              type: 'line',
              data: modelBars.map((m: any) => Number(m.ttftMeanMs) || 0),
            },
            {
              name: '总耗时均值(ms)',
              type: 'line',
              data: modelBars.map((m: any) => Number(m.durationMeanMs) || 0),
            },
          ],
        },
        true,
      )
    }
  }

  // HTTP top paths
  const httpBar = ensureChart(httpBarRef.value)
  const topPaths = data.value.http?.topPaths || []
  if (httpBar) {
    if (!topPaths.length) {
      httpBar.setOption(emptyOption('暂无 HTTP 路径采样'), true)
    } else {
      const rows = [...topPaths].reverse()
      httpBar.setOption(
        {
          tooltip: { trigger: 'axis' },
          grid: { left: 120, right: 20, top: 20, bottom: 24 },
          xAxis: { type: 'value' },
          yAxis: { type: 'category', data: rows.map((r: any) => r.name) },
          series: [
            {
              type: 'bar',
              data: rows.map((r: any) => Number(r.value) || 0),
              itemStyle: { color: '#3b82f6' },
            },
          ],
        },
        true,
      )
    }
  }

  // HTTP status
  const httpStatus = ensureChart(httpStatusRef.value)
  const statusBars = data.value.http?.statusBars || []
  if (httpStatus) {
    if (!statusBars.length) {
      httpStatus.setOption(emptyOption('暂无状态码采样'), true)
    } else {
      httpStatus.setOption(
        {
          tooltip: { trigger: 'axis' },
          grid: { left: 40, right: 16, top: 20, bottom: 36 },
          xAxis: { type: 'category', data: statusBars.map((s: any) => String(s.name)) },
          yAxis: { type: 'value' },
          series: [
            {
              type: 'bar',
              data: statusBars.map((s: any) => Number(s.value) || 0),
              itemStyle: { color: '#10b981' },
            },
          ],
        },
        true,
      )
    }
  }

  // System resources
  const systemBar = ensureChart(systemBarRef.value)
  const resourceBars = data.value.system?.resourceBars || []
  if (systemBar) {
    if (!resourceBars.length) {
      systemBar.setOption(emptyOption('暂无系统指标'), true)
    } else {
      systemBar.setOption(
        {
          tooltip: { trigger: 'axis' },
          grid: { left: 40, right: 16, top: 20, bottom: 36 },
          xAxis: { type: 'category', data: resourceBars.map((r: any) => r.name) },
          yAxis: { type: 'value' },
          series: [
            {
              type: 'bar',
              data: resourceBars.map((r: any) => Number(r.value) || 0),
              itemStyle: { color: '#8b5cf6' },
            },
          ],
        },
        true,
      )
    }
  }

  // Payment daily revenue
  const paymentLine = ensureChart(paymentLineRef.value)
  const daily = data.value.payment?.dailyRevenue || []
  const methods = data.value.payment?.paymentMethods || []
  if (paymentLine) {
    if (!daily.length && !methods.length) {
      paymentLine.setOption(emptyOption('暂无支付统计'), true)
    } else {
      paymentLine.setOption(
        {
          tooltip: { trigger: 'axis' },
          legend: { data: ['日收入', '支付方式订单数'] },
          grid: { left: 48, right: 16, top: 36, bottom: 36 },
          xAxis: {
            type: 'category',
            data: daily.length
              ? daily.map((d: any) => d.date)
              : methods.map((m: any) => m.name),
          },
          yAxis: { type: 'value' },
          series: [
            {
              name: '日收入',
              type: 'line',
              smooth: true,
              data: daily.map((d: any) => Number(d.revenue) || 0),
              areaStyle: { opacity: 0.12 },
            },
            {
              name: '支付方式订单数',
              type: 'bar',
              data: methods.map((m: any) => Number(m.value) || 0),
              // 若 daily 存在，methods 可能轴不同；仅在无 daily 时用 methods 轴
              ...(daily.length ? { xAxisIndex: 0, data: [] as number[] } : {}),
            },
          ],
        },
        true,
      )
      // 若两者都有，叠加支付方式为饼图数据在 tooltip note 中即可；这里优先日收入折线
      if (daily.length) {
        paymentLine.setOption(
          {
            series: [
              {
                name: '日收入',
                type: 'line',
                smooth: true,
                data: daily.map((d: any) => Number(d.revenue) || 0),
                areaStyle: { opacity: 0.12 },
              },
            ],
          },
          false,
        )
      }
    }
  }

  // Model probe bars
  const modelBar = ensureChart(modelBarRef.value)
  if (modelBar) {
    if (!modelRows.value.length) {
      modelBar.setOption(emptyOption('暂无模型探测结果'), true)
    } else {
      modelBar.setOption(
        {
          tooltip: {
            trigger: 'axis',
            formatter: (params: any) => {
              const p = Array.isArray(params) ? params[0] : params
              const row = modelRows.value[p.dataIndex] || {}
              return `${row.modelCode}<br/>状态: ${row.status}<br/>耗时: ${row.latencyMs ?? '-'} ms<br/>${row.message || ''}`
            },
          },
          grid: { left: 40, right: 16, top: 20, bottom: 48 },
          xAxis: {
            type: 'category',
            data: modelRows.value.map((m: any) => m.modelCode),
            axisLabel: { rotate: 20 },
          },
          yAxis: { type: 'value', name: 'ms' },
          series: [
            {
              type: 'bar',
              data: modelRows.value.map((m: any) => ({
                value: Number(m.latencyMs) || 0,
                itemStyle: {
                  color:
                    m.status === 'UP' ? '#10b981' : m.timedOut ? '#f59e0b' : '#ef4444',
                },
              })),
            },
          ],
        },
        true,
      )
    }
  }
}

async function load() {
  loading.value = true
  try {
    data.value = (await getOpsMonitoring()) as Record<string, any>
    await nextTick()
    renderCharts()
  } catch (e) {
    showApiError(e, '加载监控数据失败')
  } finally {
    loading.value = false
  }
}

function onResize() {
  charts.forEach((c) => c.resize())
}

function setupAutoRefresh() {
  if (timer) {
    window.clearInterval(timer)
    timer = undefined
  }
  if (autoRefresh.value) {
    timer = window.setInterval(() => {
      load()
    }, 30000)
  }
}

watch(autoRefresh, setupAutoRefresh)

onMounted(async () => {
  await load()
  setupAutoRefresh()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
  window.removeEventListener('resize', onResize)
  charts.forEach((c) => c.dispose())
})
</script>

<style scoped>
.sub {
  margin-top: 4px;
  color: #6b7280;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.health-pill {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}
.health-pill.ok {
  background: #d1fae5;
  color: #065f46;
}
.health-pill.warn {
  background: #fef3c7;
  color: #92400e;
}
.health-pill.bad {
  background: #fee2e2;
  color: #991b1b;
}
.charts {
  margin-top: 12px;
}
.block {
  margin-bottom: 12px;
  border: 1px solid #e5e7eb;
}
.chart {
  width: 100%;
  height: 300px;
}
.hint {
  margin-top: 4px;
  color: #9ca3af;
  font-size: 12px;
}
</style>
