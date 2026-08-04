<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">订单与支付</h2>
      <div>
        <el-button type="warning" @click="batchSync">批量同步</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-input v-model="query.orderNo" placeholder="订单号" clearable style="width: 200px" />
      <el-input v-model="query.userId" placeholder="用户ID" clearable style="width: 180px" />
      <el-select v-model="query.paymentStatus" clearable placeholder="支付状态" style="width: 140px">
        <el-option label="未支付" :value="0" />
        <el-option label="已支付" :value="1" />
        <el-option label="已取消" :value="2" />
        <el-option label="已退款" :value="3" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe size="small">
      <el-table-column prop="orderNo" label="订单号" min-width="170">
        <template #default="{ row }"><span class="mono">{{ row.orderNo }}</span></template>
      </el-table-column>
      <el-table-column prop="username" label="用户" width="120" />
      <el-table-column prop="amount" label="金额" width="90" />
      <el-table-column prop="currency" label="币种" width="70" />
      <el-table-column prop="paymentMethod" label="渠道" width="100" />
      <el-table-column prop="paymentStatus" label="状态" width="90">
        <template #default="{ row }">{{ statusText(row.paymentStatus) }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="160" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDetail(row.orderNo)">详情</el-button>
          <el-button link type="primary" @click="syncOne(row.orderNo)">同步</el-button>
          <el-button link type="danger" @click="cancelOne(row.orderNo)">取消</el-button>
        </template>
      </el-table-column>
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

    <el-drawer v-model="detailVisible" title="订单详情" size="45%">
      <pre class="mono json">{{ detailJson }}</pre>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { AdminOrder } from '@/types/api'
import { batchSyncOrders, cancelOrder, getOrder, listOrders, syncOrder } from '@/api/admin'
import { showApiError } from '@/utils/request'

const loading = ref(false)
const rows = ref<AdminOrder[]>([])
const total = ref(0)
const detailVisible = ref(false)
const detailJson = ref('')
const query = reactive({
  page: 1,
  size: 20,
  orderNo: '',
  userId: '',
  paymentStatus: undefined as number | undefined,
})

function statusText(status: number) {
  return ({ 0: '未支付', 1: '已支付', 2: '已取消', 3: '已退款' } as Record<number, string>)[status] || String(status)
}

async function load() {
  loading.value = true
  try {
    const data = await listOrders({ ...query })
    rows.value = data.items || []
    total.value = data.total || 0
  } catch (e) {
    showApiError(e)
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

async function showDetail(orderNo: string) {
  try {
    const data = await getOrder(orderNo)
    detailJson.value = JSON.stringify(data, null, 2)
    detailVisible.value = true
  } catch (e) {
    showApiError(e)
  }
}

async function syncOne(orderNo: string) {
  try {
    const { value } = await ElMessageBox.prompt('同步原因', '同步订单')
    await syncOrder(orderNo, value)
    ElMessage.success('同步完成')
    load()
  } catch (e) {
    if (e !== 'cancel') showApiError(e)
  }
}

async function cancelOne(orderNo: string) {
  try {
    const { value } = await ElMessageBox.prompt('取消原因', '取消订单')
    await cancelOrder(orderNo, value)
    ElMessage.success('已取消')
    load()
  } catch (e) {
    if (e !== 'cancel') showApiError(e)
  }
}

async function batchSync() {
  try {
    const { value } = await ElMessageBox.prompt('批量同步原因（默认最多 50 笔）', '批量同步')
    const result = await batchSyncOrders(50, value)
    ElMessage.success(`批量同步完成: ${JSON.stringify(result)}`)
    load()
  } catch (e) {
    if (e !== 'cancel') showApiError(e)
  }
}

onMounted(load)
</script>

<style scoped>
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.json {
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
}
</style>
