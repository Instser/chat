<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">运维监控</h2>
        <div class="sub">
          进程内快照（类似 Grafana 轻量面板）· 更新于 {{ generatedAt || '-' }}
          <span v-if="healthStatus" class="health-pill" :class="healthClass">{{ healthStatus }}</span>
          <span class="range-tag">{{ rangeLabel }} · {{ aiSourceLabel }}</span>
        </div>
      </div>
      <div class="actions">
        <el-radio-group v-model="range" size="small" @change="onRangeChange">
          <el-radio-button value="1d">近1日</el-radio-button>
          <el-radio-button value="7d">近7日</el-radio-button>
          <el-radio-button value="30d">近30日</el-radio-button>
          <el-radio-button value="all">进程累计</el-radio-button>
        </el-radio-group>
        <el-switch v-model="autoRefresh" active-text="自动刷新" />
        <el-button :loading="loading" type="primary" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="card-grid" v-loading="loading">
      <div class="metric-card">
        <div class="metric-label">AI 调用成功/失败（{{ rangeLabel }}）</div>
        <div class="metric-value">{{ aiSuccess }} / {{ aiFailed }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">HTTP 请求总量（进程累计）</div>
        <div class="metric-value">{{ httpTotal }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">堆内存(MB)</div>
        <div class="metric-value">{{ heapUsed }} / {{ heapMax }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">支付成功（{{ paymentWindowLabel }}）</div>
        <div class="metric-value">{{ paid7d }}</div>
      </div>
      <div class="metric-card">
        <div class="metric-label">模型探测 UP（手动）</div>
        <div class="metric-value">{{ modelTotal ? `${modelUp} / ${modelTotal}` : '-' }}</div>
      </div>
    </div>

    <el-row :gutter="12" class="charts">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="block">
          <template #header>AI 调用趋势（{{ rangeLabel }}）</template>
          <div ref="aiTimelineRef" class="chart"></div>
          <div class="hint">{{ timelineHint }}</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="block">
          <template #header>AI 成功/失败（{{ rangeLabel }}）</template>
          <div ref="aiPieRef" class="chart"></div>
          <div class="hint">{{ aiSourceHint }}</div>
        </el-card>
      </el-col>
      <el-col :xs="24">
        <el-card shadow="never" class="block">
          <template #header>AI 按模型耗时 / TTFT（{{ rangeLabel }}）</template>
          <div ref="aiBarRef" class="chart"></div>
          <div class="hint">左轴=调用次数 · 右轴=TTFT/总耗时(ms) · 切换时间刻度后重新聚合</div>
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
          <template #header>支付 / 订单（{{ paymentWindowLabel }}收入）</template>
          <div ref="paymentLineRef" class="chart"></div>
          <div class="hint">来源：t_user_order 真实统计（非模拟）</div>
        </el-card>
      </el-col>

      <el-col :xs="24">
        <el-card shadow="never" class="block">
          <template #header>
            <div class="probe-header">
              <span>模型实时连通探测（手动）</span>
              <div class="probe-actions">
                <span class="probe-meta" v-if="probeMeta">{{ probeMeta }}</span>
                <el-button type="primary" size="small" :loading="probing" @click="probeModels">
                  探测模型
                </el-button>
              </div>
            </div>
          </template>
          <div ref="modelBarRef" class="chart"></div>
          <el-empty
            v-if="!modelRows.length && !probing"
            :description="modelsIdleText"
            :image-size="64"
          />
          <el-table v-else :data="modelRows" size="small" border style="margin-top: 12px" v-loading="probing">
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
import { getOpsMonitoring, probeOpsModels } from '@/api/admin'
import { showApiError } from '@/utils/request'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const probing = ref(false)
const autoRefresh = ref(true)
const range = ref<'1d' | '7d' | '30d' | 'all'>('7d')
const data = ref<Record<string, any>>({})
/** 手动探测结果，不随自动刷新覆盖 */
const modelProbe = ref<Record<string, any> | null>(null)
const lastProbeAt = ref<string>('')
let timer: number | undefined

const aiTimelineRef = ref<HTMLDivElement | null>(null)
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

const rangeLabel = computed(() => {
  switch (range.value) {
    case '1d':
      return '近1日'
    case '30d':
      return '近30日'
    case 'all':
      return '进程累计'
    default:
      return '近7日'
  }
})
const aiSourceLabel = computed(() =>
  data.value.ai?.source === 'process-lifetime' ? 'Micrometer 累计' : '小时桶窗口',
)
const aiSourceHint = computed(() =>
  data.value.ai?.source === 'process-lifetime'
    ? '来源：Micrometer 进程累计（重启清零，无时间窗）'
    : '来源：进程内小时桶窗口（重启清空；最长保留 30 天）',
)
const timelineHint = computed(() =>
  range.value === 'all'
    ? '进程累计无时间轴；请选 1日/7日/30日 查看趋势'
    : range.value === '1d'
      ? '按小时桶'
      : '按日汇总（由小时桶聚合）',
)
const paymentWindowLabel = computed(() => {
  const days = data.value.payment?.period?.days ?? data.value.payment?.windowDays ?? 7
  return `近${days}日`
})

const aiSuccess = computed(() => data.value.ai?.totals?.callsSuccess ?? 0)
const aiFailed = computed(() => data.value.ai?.totals?.callsFailed ?? 0)
const httpTotal = computed(() => data.value.http?.totalRequests ?? 0)
const heapUsed = computed(() => data.value.system?.jvm?.heapUsedMb ?? '-')
const heapMax = computed(() => data.value.system?.jvm?.heapMaxMb ?? '-')
const paid7d = computed(
  () =>
    data.value.payment?.period?.successfulOrders ??
    data.value.payment?.last7Days?.successfulOrders ??
    0,
)
const modelRows = computed(() => {
  const models = modelProbe.value?.models ?? data.value.models?.models
  return Array.isArray(models) ? models : []
})
const modelUp = computed(() => modelRows.value.filter((m: any) => m.status === 'UP').length)
const modelTotal = computed(() => modelRows.value.length)
const modelsIdleText = computed(() => {
  if (modelProbe.value?.message) return String(modelProbe.value.message)
  return '点击右上角「探测模型」发起真实可聊检测；自动刷新不会探测'
})
const probeMeta = computed(() => {
  if (!lastProbeAt.value) return ''
  const st = modelProbe.value?.status || ''
  return `上次探测 ${lastProbeAt.value}${st ? ` · ${st}` : ''}`
})

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

/** 缩短模型名，避免 x 轴重叠；完整名放 tooltip */
function shortModelLabel(name: string, max = 16): string {
  const s = String(name || '').trim()
  if (!s) return '-'
  // 去掉常见前缀噪音
  const cleaned = s.replace(/^(openai|google|anthropic|azure)[/:]/i, '')
  if (cleaned.length <= max) return cleaned
  return `${cleaned.slice(0, max - 1)}…`
}

/** 缩短 HTTP 路径标签 */
function shortPathLabel(name: string, max = 28): string {
  const s = String(name || '')
  if (s.length <= max) return s
  return `…${s.slice(-(max - 1))}`
}

function formatNum(v: unknown, digits = 0): string {
  if (v == null || v === '' || Number.isNaN(Number(v))) return '-'
  const n = Number(v)
  return digits > 0 ? n.toFixed(digits) : String(Math.round(n))
}

/**
 * dataset 模式下 series 的 value 可能是整行对象；
 * 必须从 dimension 取值，否则会显示 [object Object]。
 */
function seriesNumericValue(param: any, dimension?: string): number | null {
  if (param == null) return null
  const data = param.data
  if (dimension && data && typeof data === 'object' && !Array.isArray(data)) {
    const raw = data[dimension]
    if (raw == null || raw === '') return null
    const n = Number(raw)
    return Number.isFinite(n) ? n : null
  }
  if (typeof data === 'number') return Number.isFinite(data) ? data : null
  if (Array.isArray(data)) {
    const idx = param.encode?.y?.[0]
    const raw = typeof idx === 'number' ? data[idx] : data[data.length - 1]
    if (raw == null) return null
    const n = Number(raw)
    return Number.isFinite(n) ? n : null
  }
  if (param.value != null && typeof param.value !== 'object') {
    const n = Number(param.value)
    return Number.isFinite(n) ? n : null
  }
  return null
}

function axisTooltip(params: any, units: Record<string, string> = {}): string {
  const rows = Array.isArray(params) ? params : [params]
  if (!rows.length) return ''
  const title = rows[0]?.axisValueLabel || rows[0]?.name || ''
  const lines = rows.map((p: any) => {
    // 优先用 series 维度映射；fallback value
    const dimHint =
      p.seriesName?.includes('TTFT')
        ? 'ttft'
        : p.seriesName?.includes('总耗时')
          ? 'duration'
          : p.seriesName?.includes('次数') || p.seriesName?.includes('调用')
            ? 'calls'
            : undefined
    const v = seriesNumericValue(p, dimHint)
    const unit = units[p.seriesName] ?? (p.seriesName?.includes('ms') || p.seriesName?.includes('耗时') || p.seriesName?.includes('TTFT') ? ' ms' : '')
    const digits = unit.includes('ms') ? 1 : 0
    return `${p.marker}${p.seriesName}: ${formatNum(v, digits)}${v == null ? '' : unit}`
  })
  return `<div style="font-weight:600;margin-bottom:4px">${title}</div>${lines.join('<br/>')}`
}

function pieLabelFormatter(params: any): string {
  // 只显示名称，数值进 tooltip，减少扇区标签重叠
  return String(params?.name ?? '')
}

function renderCharts() {
  // AI timeline
  const aiTimeline = ensureChart(aiTimelineRef.value)
  const timeline = data.value.ai?.timeline || []
  if (aiTimeline) {
    if (!timeline.length) {
      aiTimeline.setOption(
        emptyOption(range.value === 'all' ? '进程累计无趋势轴，请切换 1/7/30 日' : '暂无窗口采样'),
        true,
      )
    } else {
      const dense = timeline.length > 12
      aiTimeline.setOption(
        {
          tooltip: {
            trigger: 'axis',
            confine: true,
            formatter: (params: any) =>
              axisTooltip(params, {
                调用次数: '',
                成功: '',
                失败: '',
                'TTFT均值(ms)': ' ms',
              }),
          },
          legend: {
            top: 0,
            type: 'scroll',
            data: ['调用次数', '成功', '失败', 'TTFT均值(ms)'],
          },
          grid: { left: 52, right: 56, top: 40, bottom: dense ? 56 : 40, containLabel: true },
          xAxis: {
            type: 'category',
            data: timeline.map((p: any) => p.label || p.bucket),
            axisLabel: {
              rotate: dense ? 35 : 0,
              interval: dense ? 'auto' : 0,
              hideOverlap: true,
              fontSize: 11,
            },
          },
          yAxis: [
            { type: 'value', name: '次数', minInterval: 1, splitNumber: 4 },
            { type: 'value', name: 'ms', splitLine: { show: false }, splitNumber: 4 },
          ],
          series: [
            {
              name: '调用次数',
              type: 'bar',
              yAxisIndex: 0,
              data: timeline.map((p: any) => Number(p.calls) || 0),
              barMaxWidth: 18,
              label: { show: false },
            },
            {
              name: '成功',
              type: 'line',
              yAxisIndex: 0,
              data: timeline.map((p: any) => Number(p.success) || 0),
              showSymbol: timeline.length <= 8,
              symbolSize: 6,
              label: { show: false },
            },
            {
              name: '失败',
              type: 'line',
              yAxisIndex: 0,
              data: timeline.map((p: any) => Number(p.failed) || 0),
              showSymbol: timeline.length <= 8,
              symbolSize: 6,
              label: { show: false },
            },
            {
              name: 'TTFT均值(ms)',
              type: 'line',
              yAxisIndex: 1,
              data: timeline.map((p: any) => (p.ttftMeanMs == null ? null : Number(p.ttftMeanMs))),
              connectNulls: false,
              showSymbol: true,
              symbolSize: 6,
              label: { show: false },
            },
          ],
        },
        true,
      )
    }
  }

  // AI pie
  const aiPie = ensureChart(aiPieRef.value)
  const statusPie = data.value.ai?.statusPie || []
  if (aiPie) {
    if (!statusPie.length || statusPie.every((x: any) => !Number(x.value))) {
      aiPie.setOption(emptyOption('暂无 AI 调用采样'), true)
    } else {
      aiPie.setOption(
        {
          tooltip: {
            trigger: 'item',
            confine: true,
            formatter: (p: any) => `${p.name}<br/>${p.marker}${p.value}（${p.percent}%）`,
          },
          legend: { bottom: 0, type: 'scroll' },
          series: [
            {
              type: 'pie',
              radius: ['42%', '68%'],
              center: ['50%', '46%'],
              data: statusPie.map((x: any) => ({ name: x.name, value: Number(x.value) || 0 })),
              label: {
                formatter: pieLabelFormatter,
                fontSize: 11,
              },
              labelLine: { length: 10, length2: 8 },
              avoidLabelOverlap: true,
            },
          ],
        },
        true,
      )
    }
  }

  // AI model bars：显式 categories + 平行数组，tooltip 绝不吐 object
  const aiBar = ensureChart(aiBarRef.value)
  const modelBars = (data.value.ai?.modelBars || []).filter(
    (m: any) => m && m.name && m.name !== 'unknown',
  )
  if (aiBar) {
    if (!modelBars.length) {
      aiBar.setOption(emptyOption('暂无按模型耗时数据'), true)
    } else {
      const fullNames = modelBars.map((m: any) => String(m.name))
      const shortNames = fullNames.map((n: string) => shortModelLabel(n, modelBars.length > 6 ? 12 : 16))
      const calls = modelBars.map((m: any) => Number(m.calls) || 0)
      const ttft = modelBars.map((m: any) =>
        m.ttftMeanMs == null ? null : Number(m.ttftMeanMs),
      )
      const duration = modelBars.map((m: any) =>
        m.durationMeanMs == null ? null : Number(m.durationMeanMs),
      )
      const denseModels = modelBars.length > 5
      aiBar.setOption(
        {
          tooltip: {
            trigger: 'axis',
            axisPointer: { type: 'shadow' },
            confine: true,
            formatter: (params: any) => {
              const rows = Array.isArray(params) ? params : [params]
              const idx = rows[0]?.dataIndex ?? 0
              const full = fullNames[idx] || rows[0]?.axisValueLabel || ''
              const lines = rows.map((p: any) => {
                // 这里 data 是 number | null，不是 object
                const raw = p.value
                const v =
                  raw == null || (typeof raw === 'object' && raw !== null)
                    ? null
                    : Number(raw)
                const isCount = p.seriesName === '调用次数'
                const unit = isCount ? '' : ' ms'
                const text =
                  v == null || Number.isNaN(v) ? '-' : isCount ? String(Math.round(v)) : v.toFixed(1)
                return `${p.marker}${p.seriesName}: ${text}${v == null || Number.isNaN(v) ? '' : unit}`
              })
              return `<div style="font-weight:600;margin-bottom:4px">${full}</div>${lines.join('<br/>')}`
            },
          },
          legend: {
            top: 0,
            type: 'scroll',
            data: ['调用次数', 'TTFT均值(ms)', '总耗时均值(ms)'],
          },
          grid: {
            left: 52,
            right: 56,
            top: 40,
            bottom: denseModels ? 72 : 48,
            containLabel: true,
          },
          xAxis: {
            type: 'category',
            data: shortNames,
            axisLabel: {
              rotate: denseModels ? 40 : 20,
              interval: 0,
              hideOverlap: true,
              fontSize: 11,
              formatter: (val: string) => shortModelLabel(val, denseModels ? 10 : 14),
            },
          },
          yAxis: [
            { type: 'value', name: '次数', minInterval: 1, splitNumber: 4 },
            { type: 'value', name: 'ms', splitLine: { show: false }, splitNumber: 4 },
          ],
          series: [
            {
              name: '调用次数',
              type: 'bar',
              yAxisIndex: 0,
              data: calls,
              barMaxWidth: denseModels ? 22 : 32,
              // 柱顶仅次数，且自动避让
              label: {
                show: modelBars.length <= 8,
                position: 'top',
                fontSize: 10,
                color: '#6b7280',
                formatter: (p: any) => formatNum(p.value),
              },
            },
            {
              name: 'TTFT均值(ms)',
              type: 'line',
              yAxisIndex: 1,
              data: ttft,
              connectNulls: false,
              symbol: 'circle',
              symbolSize: 7,
              // 线系列默认 label 在 dataset 下会吐 object；彻底关闭
              label: { show: false },
              emphasis: { focus: 'series' },
            },
            {
              name: '总耗时均值(ms)',
              type: 'line',
              yAxisIndex: 1,
              data: duration,
              connectNulls: false,
              symbol: 'circle',
              symbolSize: 7,
              label: { show: false },
              emphasis: { focus: 'series' },
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
      const rows = [...topPaths].slice(0, 12).reverse()
      const fullPaths = rows.map((r: any) => String(r.name))
      httpBar.setOption(
        {
          tooltip: {
            trigger: 'axis',
            confine: true,
            formatter: (params: any) => {
              const p = Array.isArray(params) ? params[0] : params
              const idx = p?.dataIndex ?? 0
              const full = fullPaths[idx] || p?.name || ''
              return `${full}<br/>${p.marker}次数: ${formatNum(p.value)}`
            },
          },
          grid: { left: 8, right: 28, top: 12, bottom: 12, containLabel: true },
          xAxis: { type: 'value', minInterval: 1, splitNumber: 3 },
          yAxis: {
            type: 'category',
            data: fullPaths.map((n) => shortPathLabel(n, 26)),
            axisLabel: {
              fontSize: 11,
              width: 150,
              overflow: 'truncate',
            },
          },
          series: [
            {
              type: 'bar',
              data: rows.map((r: any) => Number(r.value) || 0),
              itemStyle: { color: '#3b82f6' },
              barMaxWidth: 18,
              label: {
                show: true,
                position: 'right',
                fontSize: 10,
                color: '#6b7280',
                formatter: (p: any) => formatNum(p.value),
              },
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
          tooltip: {
            trigger: 'axis',
            confine: true,
            formatter: (params: any) => {
              const p = Array.isArray(params) ? params[0] : params
              return `${p.axisValueLabel || p.name}<br/>${p.marker}${formatNum(p.value)}`
            },
          },
          grid: { left: 40, right: 16, top: 20, bottom: 36, containLabel: true },
          xAxis: {
            type: 'category',
            data: statusBars.map((s: any) => String(s.name)),
            axisLabel: { hideOverlap: true },
          },
          yAxis: { type: 'value', minInterval: 1, splitNumber: 4 },
          series: [
            {
              type: 'bar',
              data: statusBars.map((s: any) => Number(s.value) || 0),
              itemStyle: { color: '#10b981' },
              barMaxWidth: 36,
              label: {
                show: statusBars.length <= 8,
                position: 'top',
                fontSize: 10,
                formatter: (p: any) => formatNum(p.value),
              },
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
          tooltip: {
            trigger: 'axis',
            confine: true,
            formatter: (params: any) => {
              const p = Array.isArray(params) ? params[0] : params
              return `${p.axisValueLabel || p.name}<br/>${p.marker}${formatNum(p.value, 1)}`
            },
          },
          grid: { left: 40, right: 16, top: 20, bottom: 48, containLabel: true },
          xAxis: {
            type: 'category',
            data: resourceBars.map((r: any) => r.name),
            axisLabel: { rotate: 20, hideOverlap: true, fontSize: 11 },
          },
          yAxis: { type: 'value', splitNumber: 4 },
          series: [
            {
              type: 'bar',
              data: resourceBars.map((r: any) => Number(r.value) || 0),
              itemStyle: { color: '#8b5cf6' },
              barMaxWidth: 36,
              label: {
                show: true,
                position: 'top',
                fontSize: 10,
                color: '#6b7280',
                formatter: (p: any) => formatNum(p.value, 1),
              },
            },
          ],
        },
        true,
      )
    }
  }

  // Payment daily revenue（真实订单日收入；支付方式单独用饼图，避免与日期轴错位）
  const paymentLine = ensureChart(paymentLineRef.value)
  const daily = data.value.payment?.dailyRevenue || []
  const methods = data.value.payment?.paymentMethods || []
  if (paymentLine) {
    if (!daily.length && !methods.length) {
      paymentLine.setOption(emptyOption('暂无支付统计'), true)
    } else if (daily.length) {
      const densePay = daily.length > 10
      paymentLine.setOption(
        {
          tooltip: {
            trigger: 'axis',
            confine: true,
            formatter: (params: any) => {
              const p = Array.isArray(params) ? params[0] : params
              return `${p.axisValueLabel || p.name}<br/>${p.marker}日收入: ${formatNum(p.value, 2)}`
            },
          },
          legend: { data: ['日收入'], top: 0 },
          grid: {
            left: 48,
            right: 16,
            top: 36,
            bottom: densePay ? 48 : 36,
            containLabel: true,
          },
          xAxis: {
            type: 'category',
            data: daily.map((d: any) => d.date),
            axisLabel: {
              rotate: densePay ? 30 : 0,
              hideOverlap: true,
              fontSize: 11,
              formatter: (v: string) => {
                // 日期只显示 MM-DD，减少重叠
                const s = String(v)
                return s.length >= 10 ? s.slice(5) : s
              },
            },
          },
          yAxis: { type: 'value', name: '金额', splitNumber: 4 },
          series: [
            {
              name: '日收入',
              type: 'line',
              smooth: true,
              data: daily.map((d: any) => Number(d.revenue) || 0),
              areaStyle: { opacity: 0.12 },
              showSymbol: daily.length <= 10,
              symbolSize: 6,
              label: { show: false },
            },
          ],
        },
        true,
      )
    } else {
      paymentLine.setOption(
        {
          tooltip: {
            trigger: 'item',
            confine: true,
            formatter: (p: any) => `${p.name}<br/>${p.marker}${p.value}（${p.percent}%）`,
          },
          legend: { bottom: 0, type: 'scroll' },
          series: [
            {
              name: '支付方式',
              type: 'pie',
              radius: ['38%', '65%'],
              center: ['50%', '46%'],
              data: methods.map((m: any) => ({
                name: m.name,
                value: Number(m.value) || 0,
              })),
              label: { formatter: pieLabelFormatter, fontSize: 11 },
              labelLine: { length: 10, length2: 8 },
              avoidLabelOverlap: true,
            },
          ],
        },
        true,
      )
    }
  }

  // Model probe bars
  const modelBar = ensureChart(modelBarRef.value)
  if (modelBar) {
    if (!modelRows.value.length) {
      modelBar.setOption(emptyOption('暂无模型探测结果'), true)
    } else {
      const codes = modelRows.value.map((m: any) => String(m.modelCode || ''))
      const denseProbe = codes.length > 5
      modelBar.setOption(
        {
          tooltip: {
            trigger: 'axis',
            confine: true,
            formatter: (params: any) => {
              const p = Array.isArray(params) ? params[0] : params
              const row = modelRows.value[p.dataIndex] || {}
              const latency =
                typeof p.value === 'object' && p.value != null
                  ? p.value.value
                  : p.value
              return [
                `<div style="font-weight:600">${row.modelCode || p.name || ''}</div>`,
                `状态: ${row.status ?? '-'}`,
                `耗时: ${formatNum(latency)} ms`,
                row.timedOut ? '超时: 是' : '超时: 否',
                row.message ? `说明: ${row.message}` : '',
              ]
                .filter(Boolean)
                .join('<br/>')
            },
          },
          grid: {
            left: 48,
            right: 16,
            top: 20,
            bottom: denseProbe ? 64 : 44,
            containLabel: true,
          },
          xAxis: {
            type: 'category',
            data: codes.map((c) => shortModelLabel(c, denseProbe ? 10 : 14)),
            axisLabel: {
              rotate: denseProbe ? 35 : 15,
              interval: 0,
              hideOverlap: true,
              fontSize: 11,
            },
          },
          yAxis: { type: 'value', name: 'ms', splitNumber: 4 },
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
              barMaxWidth: 28,
              label: {
                show: codes.length <= 8,
                position: 'top',
                fontSize: 10,
                color: '#6b7280',
                formatter: (p: any) => {
                  const v =
                    typeof p.value === 'object' && p.value != null ? p.value.value : p.value
                  return formatNum(v)
                },
              },
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
    // 默认不探测模型；探测结果保存在 modelProbe，避免自动刷新冲掉
    data.value = (await getOpsMonitoring(range.value, false)) as Record<string, any>
    await nextTick()
    renderCharts()
  } catch (e) {
    showApiError(e, '加载监控数据失败')
  } finally {
    loading.value = false
  }
}

async function probeModels() {
  probing.value = true
  try {
    const result = (await probeOpsModels()) as Record<string, any>
    modelProbe.value = result
    lastProbeAt.value = new Date().toLocaleString()
    data.value = {
      ...data.value,
      models: result,
    }
    await nextTick()
    renderCharts()
    const up = modelRows.value.filter((m: any) => m.status === 'UP').length
    ElMessage.success(`探测完成：UP ${up} / ${modelRows.value.length}`)
  } catch (e) {
    showApiError(e, '模型探测失败')
  } finally {
    probing.value = false
  }
}

function onRangeChange() {
  load()
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
  flex-wrap: wrap;
  justify-content: flex-end;
}
.range-tag {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 999px;
  background: #eef2ff;
  color: #3730a3;
  font-size: 11px;
  font-weight: 600;
}
.probe-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.probe-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.probe-meta {
  color: #6b7280;
  font-size: 12px;
  font-weight: 400;
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
  height: 340px;
}
.hint {
  margin-top: 4px;
  color: #9ca3af;
  font-size: 12px;
}
</style>
