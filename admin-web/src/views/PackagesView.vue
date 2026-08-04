<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">套餐管理</h2>
      <div>
        <el-button type="primary" @click="openCreate">新增套餐</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe size="small">
      <el-table-column prop="id" label="ID" min-width="140">
        <template #default="{ row }"><span class="mono">{{ row.id }}</span></template>
      </el-table-column>
      <el-table-column prop="packageName" label="名称" min-width="120" />
      <el-table-column prop="packageType" label="类型" width="100" />
      <el-table-column prop="price" label="价格" width="90" />
      <el-table-column prop="validityDays" label="有效天" width="80" />
      <el-table-column prop="isEnabled" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.isEnabled === 1 ? 'success' : 'info'" size="small">
            {{ row.isEnabled === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="70" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="toggle(row)">
            {{ row.isEnabled === 1 ? '禁用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑套餐' : '新增套餐'" width="560px">
      <el-form label-width="100px">
        <el-form-item label="名称"><el-input v-model="form.packageName" /></el-form-item>
        <el-form-item label="类型"><el-input v-model="form.packageType" /></el-form-item>
        <el-form-item label="价格"><el-input-number v-model="form.price" :min="0" :step="0.01" /></el-form-item>
        <el-form-item label="有效天数"><el-input-number v-model="form.validityDays" :min="1" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="原因"><el-input v-model="form.reason" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { AdminPackage } from '@/types/api'
import { createPackage, listPackages, updatePackage, updatePackageStatus } from '@/api/admin'
import { showApiError } from '@/utils/request'

const loading = ref(false)
const saving = ref(false)
const rows = ref<AdminPackage[]>([])
const dialogVisible = ref(false)
const editingId = ref<string | null>(null)
const form = reactive({
  packageName: '',
  packageType: 'standard',
  price: 0,
  validityDays: 30,
  description: '',
  sortOrder: 0,
  reason: '',
  isEnabled: 1,
})

async function load() {
  loading.value = true
  try {
    rows.value = await listPackages()
  } catch (e) {
    showApiError(e)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    packageName: '',
    packageType: 'standard',
    price: 0,
    validityDays: 30,
    description: '',
    sortOrder: 0,
    reason: '',
    isEnabled: 1,
  })
  dialogVisible.value = true
}

function openEdit(row: AdminPackage) {
  editingId.value = row.id
  Object.assign(form, {
    packageName: row.packageName,
    packageType: row.packageType,
    price: Number(row.price || 0),
    validityDays: row.validityDays,
    description: row.description || '',
    sortOrder: row.sortOrder || 0,
    reason: '',
    isEnabled: row.isEnabled,
  })
  dialogVisible.value = true
}

async function save() {
  saving.value = true
  try {
    const payload = { ...form }
    if (editingId.value) {
      await updatePackage(editingId.value, payload)
    } else {
      await createPackage(payload)
    }
    ElMessage.success('已保存')
    dialogVisible.value = false
    load()
  } catch (e) {
    showApiError(e)
  } finally {
    saving.value = false
  }
}

async function toggle(row: AdminPackage) {
  try {
    const next = row.isEnabled === 1 ? 0 : 1
    const { value } = await ElMessageBox.prompt('操作原因', '切换套餐状态')
    await updatePackageStatus(row.id, next, value)
    ElMessage.success('状态已更新')
    load()
  } catch (e) {
    if (e !== 'cancel') showApiError(e)
  }
}

onMounted(load)
</script>
