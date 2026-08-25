<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">模型管理</h2>
      <div>
        <el-button type="primary" @click="openCreate">新增模型</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-table :data="rows" v-loading="loading" border stripe size="small">
      <el-table-column prop="modelCode" label="代码" min-width="140" />
      <el-table-column prop="modelName" label="名称" min-width="140" />
      <el-table-column prop="provider" label="提供商" width="100" />
      <el-table-column prop="isAvailable" label="配置启用" width="100">
        <template #default="{ row }">
          <el-tag :type="row.isAvailable === 1 ? 'success' : 'info'" size="small">
            {{ row.isAvailable === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="isVisible" label="前台可见" width="100">
        <template #default="{ row }">
          <el-tag :type="row.isVisible === 1 ? 'success' : 'info'" size="small">
            {{ row.isVisible === 1 ? '可见' : '隐藏' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="API Key" min-width="140">
        <template #default="{ row }">
          <span class="mono">{{ row.apiKeyConfigured ? row.apiKeyMasked || '已配置' : '未配置' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="openCredential(row)">凭据</el-button>
          <el-button link type="primary" @click="toggleAvailability(row)">启停</el-button>
          <el-button link @click="runTest(row)">测试</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑模型' : '新增模型'" width="560px">
      <el-form label-width="100px">
        <el-form-item label="代码"><el-input v-model="form.modelCode" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.modelName" /></el-form-item>
        <el-form-item label="提供商"><el-input v-model="form.provider" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
        <el-form-item label="baseUrl"><el-input v-model="form.baseUrl" /></el-form-item>
        <el-form-item label="原因"><el-input v-model="form.reason" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="credVisible" title="更新模型凭据" width="480px">
      <el-form label-width="90px">
        <el-form-item label="baseUrl"><el-input v-model="cred.baseUrl" /></el-form-item>
        <el-form-item label="API Key"><el-input v-model="cred.apiKey" type="password" show-password /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="credVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveCredential">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { AdminModel } from '@/types/api'
import {
  createModel,
  listModels,
  testModel,
  updateModel,
  updateModelAvailability,
  updateModelCredential,
} from '@/api/admin'
import { showApiError } from '@/utils/request'

const loading = ref(false)
const saving = ref(false)
const rows = ref<AdminModel[]>([])
const dialogVisible = ref(false)
const credVisible = ref(false)
const editingId = ref<string | null>(null)
const credId = ref<string | null>(null)

const form = reactive({
  modelCode: '',
  modelName: '',
  provider: '',
  description: '',
  baseUrl: '',
  reason: '',
})
const cred = reactive({ baseUrl: '', apiKey: '' })

async function load() {
  loading.value = true
  try {
    rows.value = await listModels()
  } catch (e) {
    showApiError(e)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { modelCode: '', modelName: '', provider: '', description: '', baseUrl: '', reason: '' })
  dialogVisible.value = true
}

function openEdit(row: AdminModel) {
  editingId.value = row.id
  Object.assign(form, {
    modelCode: row.modelCode,
    modelName: row.modelName,
    provider: row.provider || '',
    description: row.description || '',
    baseUrl: row.baseUrl || '',
    reason: '',
  })
  dialogVisible.value = true
}

function openCredential(row: AdminModel) {
  credId.value = row.id
  Object.assign(cred, { baseUrl: row.baseUrl || '', apiKey: '' })
  credVisible.value = true
}

async function save() {
  saving.value = true
  try {
    if (editingId.value) await updateModel(editingId.value, { ...form })
    else await createModel({ ...form })
    ElMessage.success('已保存')
    dialogVisible.value = false
    load()
  } catch (e) {
    showApiError(e)
  } finally {
    saving.value = false
  }
}

async function saveCredential() {
  if (!credId.value) return
  saving.value = true
  try {
    await updateModelCredential(credId.value, { ...cred })
    ElMessage.success('凭据已更新')
    cred.apiKey = ''
    credVisible.value = false
    load()
  } catch (e) {
    showApiError(e)
  } finally {
    saving.value = false
  }
}

async function toggleAvailability(row: AdminModel) {
  try {
    const next = row.isAvailable === 1 ? 0 : 1
    const { value } = await ElMessageBox.prompt(
      '这是数据库配置开关（是否允许业务调用），不是实时连通结果。\n操作原因：',
      '切换配置启用状态',
    )
    await updateModelAvailability(row.id, next, value)
    ElMessage.success('配置启用状态已更新')
    load()
  } catch (e) {
    if (e !== 'cancel') showApiError(e)
  }
}

async function runTest(row: AdminModel) {
  try {
    const result = await testModel(row.id)
    const status = (result as any)?.status || 'UNKNOWN'
    const latency = (result as any)?.latencyMs
    const message = (result as any)?.message || ''
    const contentOk = (result as any)?.contentOk
    const reply = (result as any)?.replyPreview
    const note =
      '说明：列表「配置启用」= 数据库开关；本次「测试」= 真实可聊探测（要求模型只返回 1）。\n\n' +
      `status: ${status}\n` +
      `contentOk: ${contentOk ?? '-'}\n` +
      `replyPreview: ${reply ?? '-'}\n` +
      `latencyMs: ${latency ?? '-'}\n` +
      `message: ${message}\n\n` +
      JSON.stringify(result, null, 2)
    ElMessageBox.alert(note, `真实可聊探测: ${row.modelCode}`, { customClass: 'mono' })
  } catch (e) {
    showApiError(e)
  }
}

onMounted(load)
</script>
