<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">审计中心</h2>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-tabs v-model="tab" @tab-change="onTabChange">
      <el-tab-pane label="管理操作" name="operations" />
      <el-tab-pane label="支付审计" name="payments" />
      <el-tab-pane label="OAuth 审计" name="oauth" />
    </el-tabs>

    <div class="filter-bar">
      <el-input v-model="query.operationType" placeholder="操作类型" clearable style="width: 160px" />
      <el-input v-model="query.orderNo" placeholder="订单号" clearable style="width: 180px" />
      <el-input v-model="query.traceId" placeholder="traceId" clearable style="width: 220px" />
      <el-input v-model="query.operatorId" placeholder="操作人/用户ID" clearable style="width: 160px" />
      <el-button type="primary" @click="search">查询</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe size="small">
      <el-table-column
        v-for="col in columns"
        :key="col.prop"
        :prop="col.prop"
        :label="col.label"
        :min-width="col.minWidth || 120"
        show-overflow-tooltip
      />
    </el-table>

    <div class="pager">
      <el-pagination
        background
        layout="total, prev, pager, next"
        :total="total"
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        @current-change="load"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { listOauthAudits, listOperationAudits, listPaymentAudits } from '@/api/admin'
import { showApiError } from '@/utils/request'

const tab = ref('operations')
const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 20,
  operationType: '',
  orderNo: '',
  traceId: '',
  operatorId: '',
})

const columns = computed(() => {
  if (tab.value === 'payments') {
    return [
      { prop: 'orderNo', label: '订单号', minWidth: 160 },
      { prop: 'operationType', label: '操作类型', minWidth: 110 },
      { prop: 'oldStatus', label: '原状态', minWidth: 80 },
      { prop: 'newStatus', label: '新状态', minWidth: 80 },
      { prop: 'operatorId', label: '操作人', minWidth: 120 },
      { prop: 'operationReason', label: '原因', minWidth: 140 },
      { prop: 'createTime', label: '时间', minWidth: 160 },
    ]
  }
  if (tab.value === 'oauth') {
    return [
      { prop: 'userId', label: '用户ID', minWidth: 140 },
      { prop: 'provider', label: '提供商', minWidth: 100 },
      { prop: 'action', label: '动作', minWidth: 100 },
      { prop: 'status', label: '状态', minWidth: 90 },
      { prop: 'clientIp', label: 'IP', minWidth: 120 },
      { prop: 'errorMessage', label: '错误', minWidth: 160 },
      { prop: 'createTime', label: '时间', minWidth: 160 },
    ]
  }
  return [
    { prop: 'operator', label: '操作人', minWidth: 110 },
    { prop: 'operatorId', label: '操作人ID', minWidth: 130 },
    { prop: 'operationType', label: '操作类型', minWidth: 140 },
    { prop: 'target', label: '目标', minWidth: 140 },
    { prop: 'result', label: '结果', minWidth: 90 },
    { prop: 'traceId', label: 'traceId', minWidth: 180 },
    { prop: 'createdTime', label: '时间', minWidth: 160 },
  ]
})

async function load() {
  loading.value = true
  try {
    const params: Record<string, unknown> = {
      page: query.page,
      size: query.size,
    }
    if (query.operationType) params.operationType = query.operationType
    if (query.orderNo) params.orderNo = query.orderNo
    if (query.traceId) params.traceId = query.traceId
    if (query.operatorId) params.operatorId = query.operatorId

    let data: any
    if (tab.value === 'payments') data = await listPaymentAudits(params)
    else if (tab.value === 'oauth') data = await listOauthAudits(params)
    else data = await listOperationAudits(params)

    rows.value = data.items || []
    total.value = data.total || 0
  } catch (e) {
    showApiError(e, '加载审计失败')
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function onTabChange() {
  query.page = 1
  load()
}

onMounted(load)
</script>

<style scoped>
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
