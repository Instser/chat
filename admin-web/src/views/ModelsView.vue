<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">模型管理</h2>
      <div>
        <el-button type="primary" @click="openCreate">新增模型</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert
      class="model-tip"
      type="info"
      show-icon
      :closable="true"
      title="相同模型代码可配置多行（多套 Key/端点），聊天时按有效权重轮询使用；某行连续失败会自动降权，成功即恢复。"
    />

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
      <el-table-column label="权重" width="150">
        <template #default="{ row }">
          <span>{{ row.weight }}</span>
          <el-tag
            v-if="isDegraded(row)"
            class="weight-tag"
            type="warning"
            size="small"
            title="失败自动降权，成功恢复"
          >
            有效 {{ row.effectiveWeight }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="成功/失败" width="100">
        <template #default="{ row }">{{ row.successCount }}/{{ row.failCount }}</template>
      </el-table-column>
      <el-table-column label="连续失败" width="90">
        <template #default="{ row }">
          <span v-if="Number(row.consecutiveFails) > 0" class="warn-text">{{ row.consecutiveFails }}</span>
          <span v-else>{{ row.consecutiveFails }}</span>
        </template>
      </el-table-column>
      <el-table-column label="最近错误" width="200">
        <template #default="{ row }">
          <span v-if="row.lastError" class="err-cell" :title="row.lastError">{{ row.lastError }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="360" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="openCredential(row)">凭据</el-button>
          <el-button link type="primary" @click="toggleAvailability(row)">启停</el-button>
          <el-button link @click="runTest(row)">测试</el-button>
          <el-popconfirm
            title="确认重置该行模型的调用统计？"
            confirm-button-text="确定"
            cancel-button-text="取消"
            width="240"
            @confirm="resetStats(row)"
          >
            <template #reference>
              <el-button link type="warning">重置统计</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑模型' : '新增模型'" width="560px">
      <el-form label-width="100px">
        <el-form-item label="代码"><el-input v-model="form.modelCode" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.modelName" /></el-form-item>
        <el-form-item label="提供商"><el-input v-model="form.provider" /></el-form-item>
        <el-form-item label="权重"><el-input-number v-model="form.weight" :min="1" :max="100" /></el-form-item>
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
  resetModelStats,
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
  weight: 100,
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

/** 有效权重低于配置权重即为降权中 */
function isDegraded(row: AdminModel) {
  return Number(row.effectiveWeight) < Number(row.weight)
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    modelCode: '',
    modelName: '',
    provider: '',
    weight: 100,
    description: '',
    baseUrl: '',
    reason: '',
  })
  dialogVisible.value = true
}

function openEdit(row: AdminModel) {
  editingId.value = row.id
  Object.assign(form, {
    modelCode: row.modelCode,
    modelName: row.modelName,
    provider: row.provider || '',
    weight: Number(row.weight ?? 100),
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

/** 清零该行模型的调用统计（el-popconfirm 二次确认后触发） */
async function resetStats(row: AdminModel) {
  try {
    await resetModelStats(row.id)
    ElMessage.success('统计已重置')
    load()
  } catch (e) {
    showApiError(e)
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

<style scoped>
.model-tip {
  margin-bottom: 12px;
}

.weight-tag {
  margin-left: 6px;
}

.warn-text {
  color: #e6a23c;
  font-weight: 600;
}

.err-cell {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}
</style>
