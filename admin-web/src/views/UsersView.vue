<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">用户管理</h2>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="filter-bar">
      <el-input v-model="query.keyword" placeholder="用户名/邮箱/ID" clearable style="width: 220px" />
      <el-select v-model="query.role" clearable placeholder="角色" style="width: 120px">
        <el-option label="user" value="user" />
        <el-option label="admin" value="admin" />
      </el-select>
      <el-select v-model="query.status" clearable placeholder="状态" style="width: 120px">
        <el-option label="启用" :value="1" />
        <el-option label="禁用" :value="0" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe size="small">
      <el-table-column prop="id" label="ID" min-width="150">
        <template #default="{ row }">
          <span class="mono">{{ row.id }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="email" label="邮箱" min-width="160" />
      <el-table-column prop="role" label="角色" width="90" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="packageId" label="套餐ID" min-width="120" />
      <el-table-column prop="chatCountRemaining" label="剩余次数" width="100" />
      <el-table-column prop="createdTime" label="注册时间" min-width="160" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="toggleStatus(row)">
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-button link type="primary" @click="changeRole(row)">改角色</el-button>
          <el-button link @click="showDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        background
        layout="total, prev, pager, next, sizes"
        :total="total"
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="load"
        @size-change="load"
      />
    </div>

    <el-drawer v-model="detailVisible" title="用户详情" size="40%">
      <pre class="mono json">{{ detailJson }}</pre>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { AdminUserSummary } from '@/types/api'
import { getUser, listUsers, updateUserRole, updateUserStatus } from '@/api/admin'
import { showApiError } from '@/utils/request'

const loading = ref(false)
const rows = ref<AdminUserSummary[]>([])
const total = ref(0)
const detailVisible = ref(false)
const detailJson = ref('')

const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  role: undefined as string | undefined,
  status: undefined as number | undefined,
})

async function load() {
  loading.value = true
  try {
    const data = await listUsers({ ...query })
    rows.value = data.items || []
    total.value = data.total || 0
  } catch (e) {
    showApiError(e, '加载用户失败')
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function reset() {
  query.keyword = ''
  query.role = undefined
  query.status = undefined
  search()
}

async function toggleStatus(row: AdminUserSummary) {
  const next = row.status === 1 ? 0 : 1
  try {
    const { value } = await ElMessageBox.prompt(`请输入${next === 1 ? '启用' : '禁用'}原因`, '确认操作', {
      inputPlaceholder: 'reason',
      confirmButtonText: '确认',
      cancelButtonText: '取消',
    })
    await updateUserStatus(row.id, next, value)
    ElMessage.success('状态已更新')
    load()
  } catch (e) {
    if (e !== 'cancel') showApiError(e)
  }
}

async function changeRole(row: AdminUserSummary) {
  try {
    const { value } = await ElMessageBox.prompt('输入新角色 user/admin，并在下一框填原因前先确认角色', '修改角色', {
      inputValue: row.role === 'admin' ? 'user' : 'admin',
      inputPattern: /^(user|admin)$/,
      inputErrorMessage: '只能是 user 或 admin',
    })
    const reasonBox = await ElMessageBox.prompt('操作原因', '修改角色', {
      inputPlaceholder: 'reason',
    })
    await updateUserRole(row.id, value, reasonBox.value)
    ElMessage.success('角色已更新')
    load()
  } catch (e) {
    if (e !== 'cancel') showApiError(e)
  }
}

async function showDetail(row: AdminUserSummary) {
  try {
    const data = await getUser(row.id)
    detailJson.value = JSON.stringify(data, null, 2)
    detailVisible.value = true
  } catch (e) {
    showApiError(e)
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
