<template>
  <div class="page">
    <div class="page-header">
      <h2 class="page-title">日志查看</h2>
      <div>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-card shadow="never" class="block">
      <template #header>日志筛选</template>
      <el-form :inline="true" size="small">
        <el-form-item label="日志文件">
          <el-select v-model="filter.file" style="width: 220px" @change="load">
            <el-option
              v-for="f in files"
              :key="f.fileName"
              :label="`${f.fileName}${f.exists ? '' : ' (不存在)'}`"
              :value="f.fileName"
              :disabled="!f.exists"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Trace ID">
          <el-input
            v-model="filter.traceId"
            placeholder="按 traceId 筛选"
            clearable
            style="width: 200px"
            @keyup.enter="load"
          />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="filter.keyword"
            placeholder="关键词搜索（忽略大小写）"
            clearable
            style="width: 220px"
            @keyup.enter="load"
          />
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="filter.level" style="width: 120px" @change="load">
            <el-option label="全部" value="ALL" />
            <el-option label="ERROR" value="ERROR" />
            <el-option label="WARN" value="WARN" />
            <el-option label="INFO" value="INFO" />
            <el-option label="DEBUG" value="DEBUG" />
          </el-select>
        </el-form-item>
        <el-form-item label="行数">
          <el-select v-model="filter.lines" style="width: 110px" @change="load">
            <el-option :value="200" label="200 行" />
            <el-option :value="500" label="500 行" />
            <el-option :value="1000" label="1000 行" />
            <el-option :value="3000" label="3000 行" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
      <div class="stats" v-if="result">
        <span>文件: {{ result.fileName }}</span>
        <span>读取: {{ result.totalRead }} 行</span>
        <span>筛选后: {{ result.filtered }} 行</span>
      </div>
    </el-card>

    <el-card shadow="never" class="block">
      <template #header>
        <div class="card-header">
          <span>日志内容</span>
          <el-button size="small" @click="copyAll" :disabled="!result || result.lines.length === 0">
            复制全部
          </el-button>
        </div>
      </template>
      <div v-loading="loading" class="log-box">
        <pre v-if="result && result.lines.length > 0" class="mono log-content">{{
          result.lines.join('\n')
        }}</pre>
        <el-empty v-else-if="!loading" description="无匹配日志" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { LogFile, LogTail } from '@/types/api'
import { listLogFiles, tailLog } from '@/api/admin'
import { showApiError } from '@/utils/request'

const loading = ref(false)
const files = ref<LogFile[]>([])
const result = ref<LogTail | null>(null)

const filter = reactive({
  file: 'jinyu-chat-all.log',
  traceId: '',
  keyword: '',
  level: 'ALL',
  lines: 500,
})

async function loadFiles() {
  try {
    files.value = await listLogFiles()
  } catch (e) {
    showApiError(e, '加载日志文件列表失败')
  }
}

async function load() {
  loading.value = true
  try {
    result.value = await tailLog({
      file: filter.file,
      lines: filter.lines,
      traceId: filter.traceId || undefined,
      keyword: filter.keyword || undefined,
      level: filter.level === 'ALL' ? undefined : filter.level,
    })
  } catch (e) {
    showApiError(e, '加载日志失败')
  } finally {
    loading.value = false
  }
}

function resetFilter() {
  filter.traceId = ''
  filter.keyword = ''
  filter.level = 'ALL'
  filter.lines = 500
  load()
}

async function copyAll() {
  if (!result.value || result.value.lines.length === 0) return
  try {
    await navigator.clipboard.writeText(result.value.lines.join('\n'))
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.warning('复制失败，请手动选择')
  }
}

onMounted(async () => {
  await loadFiles()
  await load()
})
</script>

<style scoped>
.block {
  margin-bottom: 12px;
  border: 1px solid #e5e7eb;
}
.stats {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #6b7280;
  margin-top: 4px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.log-box {
  max-height: 600px;
  overflow: auto;
  background: #1a1a1a;
  border-radius: 6px;
  padding: 12px;
}
.log-content {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: #e5e7eb;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
