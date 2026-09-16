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
      title="每行模型（每个级别）可配置独立的密钥池：点「密钥池」添加多个 Key，聊天时仅在该行的 Key 列表内轮询；不同模型行之间互不影响。"
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
      <el-table-column label="操作" width="430" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="openCredential(row)">凭据</el-button>
          <el-button link type="primary" @click="openKeys(row)">密钥池</el-button>
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

    <el-dialog v-model="keysVisible" :title="`密钥池管理 · ${keysModelCode}`" width="900px">
      <el-alert
        class="model-tip"
        type="info"
        show-icon
        :closable="false"
        title="本模型的多个 API Key 按有效权重在行内轮询使用；某个 Key 连续失败会自动降权（有效权重 = max(1, 权重 − 20 × 连续失败数)），成功即恢复。未配置密钥池时使用凭据中的单一 Key。"
      />

      <div class="key-add">
        <el-input
          v-model="newKey.apiKey"
          class="key-add-input"
          type="password"
          show-password
          placeholder="API Key"
          @keyup.enter="addKey"
        />
        <el-input v-model="newKey.remark" class="key-add-remark" placeholder="备注（可选）" @keyup.enter="addKey" />
        <el-input-number v-model="newKey.weight" :min="1" :max="100" />
        <el-button type="primary" :loading="addingKey" @click="addKey">添加</el-button>
      </div>

      <el-table :data="keyRows" v-loading="keysLoading" border stripe size="small" max-height="420">
        <el-table-column label="API Key" min-width="180">
          <template #default="{ row }"><span class="mono">{{ row.apiKeyMasked }}</span></template>
        </el-table-column>
        <el-table-column label="备注" min-width="120">
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="权重" width="130">
          <template #default="{ row }">
            <el-input-number
              :model-value="Number(row.weight)"
              :min="1"
              :max="100"
              size="small"
              controls-position="right"
              @change="(val: number | undefined, old: number | undefined) => changeKeyWeight(row, val, old)"
            />
          </template>
        </el-table-column>
        <el-table-column label="有效权重" width="100">
          <template #default="{ row }">
            <el-tag v-if="isKeyDegraded(row)" type="warning" size="small" title="连续失败降权中，成功即恢复">
              {{ row.effectiveWeight }}
            </el-tag>
            <span v-else>{{ row.effectiveWeight }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              size="small"
              @change="(val: string | number | boolean) => toggleKeyStatus(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="成功/失败" width="90">
          <template #default="{ row }">{{ row.successCount }}/{{ row.failCount }}</template>
        </el-table-column>
        <el-table-column label="连续失败" width="90">
          <template #default="{ row }">
            <span v-if="Number(row.consecutiveFails) > 0" class="warn-text">{{ row.consecutiveFails }}</span>
            <span v-else>{{ row.consecutiveFails }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最近错误" min-width="160">
          <template #default="{ row }">
            <span v-if="row.lastError" class="err-cell" :title="row.lastError">{{ row.lastError }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="最近使用" min-width="160">
          <template #default="{ row }">{{ row.lastUsedTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-popconfirm
              title="确认重置该密钥的调用统计？"
              confirm-button-text="确定"
              cancel-button-text="取消"
              width="240"
              @confirm="resetKeyStats(row)"
            >
              <template #reference>
                <el-button link type="warning">重置统计</el-button>
              </template>
            </el-popconfirm>
            <el-popconfirm
              title="确认删除该密钥？"
              confirm-button-text="确定"
              cancel-button-text="取消"
              width="220"
              @confirm="removeKey(row)"
            >
              <template #reference>
                <el-button link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
        <template #empty>
          <span class="key-empty">暂无密钥，添加后将按权重轮询使用；未添加时使用模型凭据中的单一 Key</span>
        </template>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { AdminModel, AdminModelKey } from '@/types/api'
import {
  addModelKey,
  createModel,
  deleteModelKey,
  listModelKeys,
  listModels,
  resetModelKeyStats,
  resetModelStats,
  testModel,
  updateModel,
  updateModelAvailability,
  updateModelCredential,
  updateModelKey,
} from '@/api/admin'
import { showApiError } from '@/utils/request'

const loading = ref(false)
const saving = ref(false)
const rows = ref<AdminModel[]>([])
const dialogVisible = ref(false)
const credVisible = ref(false)
const editingId = ref<string | null>(null)
const credId = ref<string | null>(null)

// ===== 密钥池 =====
const keysVisible = ref(false)
const keysLoading = ref(false)
const addingKey = ref(false)
const keysModelId = ref<string | null>(null)
const keysModelCode = ref('')
const keyRows = ref<AdminModelKey[]>([])
/** 权重行内改动提交中，用于去重 el-input-number 的 change 事件（失焦+按钮易重复触发） */
const keyWeightSubmitting = ref(false)
const newKey = reactive({ apiKey: '', remark: '', weight: 100 })

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

// ===== 密钥池 =====

function openKeys(row: AdminModel) {
  keysModelId.value = row.id
  keysModelCode.value = row.modelCode
  Object.assign(newKey, { apiKey: '', remark: '', weight: 100 })
  keysVisible.value = true
  loadKeys()
}

async function loadKeys() {
  if (!keysModelId.value) return
  keysLoading.value = true
  try {
    keyRows.value = await listModelKeys(keysModelId.value)
  } catch (e) {
    showApiError(e)
  } finally {
    keysLoading.value = false
  }
}

/** 有效权重低于配置权重即为降权中 */
function isKeyDegraded(row: AdminModelKey) {
  return Number(row.effectiveWeight) < Number(row.weight)
}

async function addKey() {
  if (!keysModelId.value) return
  const apiKey = newKey.apiKey.trim()
  if (!apiKey) {
    ElMessage.warning('请填写 API Key')
    return
  }
  addingKey.value = true
  try {
    await addModelKey(keysModelId.value, { apiKey, weight: newKey.weight, remark: newKey.remark.trim() || undefined })
    ElMessage.success('密钥已添加')
    Object.assign(newKey, { apiKey: '', remark: '', weight: 100 })
    loadKeys()
  } catch (e) {
    showApiError(e)
  } finally {
    addingKey.value = false
  }
}

/** 行内改权重后提交；失败回滚（重新加载列表） */
async function changeKeyWeight(row: AdminModelKey, val?: number, old?: number) {
  const next = Number(val)
  if (!Number.isFinite(next) || next === Number(old)) return
  if (keyWeightSubmitting.value) return
  keyWeightSubmitting.value = true
  try {
    await updateModelKey(row.id, { weight: next })
    ElMessage.success('权重已更新')
    loadKeys()
  } catch (e) {
    showApiError(e)
    loadKeys()
  } finally {
    keyWeightSubmitting.value = false
  }
}

async function toggleKeyStatus(row: AdminModelKey, val: string | number | boolean) {
  const next = val ? 1 : 0
  if (next === row.status) return
  try {
    await updateModelKey(row.id, { status: next })
    ElMessage.success('状态已更新')
    loadKeys()
  } catch (e) {
    showApiError(e)
    loadKeys()
  }
}

async function resetKeyStats(row: AdminModelKey) {
  try {
    await resetModelKeyStats(row.id)
    ElMessage.success('统计已重置')
    loadKeys()
  } catch (e) {
    showApiError(e)
  }
}

async function removeKey(row: AdminModelKey) {
  try {
    await deleteModelKey(row.id)
    ElMessage.success('密钥已删除')
    loadKeys()
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

.key-add {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.key-add-input {
  width: 260px;
}

.key-add-remark {
  width: 180px;
}

.key-empty {
  color: #909399;
  font-size: 13px;
}
</style>
