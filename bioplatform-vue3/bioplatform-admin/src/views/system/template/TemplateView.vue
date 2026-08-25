<template>
  <div class="template-container">
    <!-- Search -->
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="名称">
          <el-input v-model="searchForm.name" placeholder="搜索模板" clearable />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="searchForm.type" placeholder="全部" clearable>
            <el-option label="task" value="task" />
            <el-option label="pipeline" value="pipeline" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="searchForm.category" placeholder="全部" clearable>
            <el-option label="转录组" value="转录组" />
            <el-option label="变异检测" value="变异检测" />
            <el-option label="表观遗传学" value="表观遗传学" />
            <el-option label="蛋白质组" value="蛋白质组" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadTemplates">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Table -->
    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <span>工作流模板</span>
          <div>
            <el-button type="warning" @click="importDialogVisible = true">
              <el-icon><Download /></el-icon>
              从 Omics 导入
            </el-button>
            <el-button type="primary" @click="openCreate">
              <el-icon><Plus /></el-icon>
              新建模板
            </el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="templateList">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column prop="type" label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.type === 'pipeline' ? 'primary' : 'success'" size="small">
              {{ row.type }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column prop="snakemakePath" label="Snakefile" min-width="200" show-overflow-tooltip />
        <el-table-column label="配置" width="80">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="showConfigPreview(row as WorkflowTemplate)">查看</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row as WorkflowTemplate)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row as WorkflowTemplate)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next"
          @size-change="loadTemplates"
          @current-change="loadTemplates"
        />
      </div>
    </el-card>

    <!-- Config Preview Dialog -->
    <el-dialog v-model="previewVisible" :title="`模板配置: ${previewName}`" width="900px">
      <el-tabs v-model="previewTab">
        <el-tab-pane label="默认配置" name="config">
          <div class="config-sections">
            <div v-for="(section, key) in parsedConfig" :key="key" class="config-section">
              <h4 class="section-title">{{ key }}</h4>
              <div class="section-content">
                <!-- 简单值直接显示 -->
                <template v-if="!isObject(section)">
                  <span class="section-value">{{ formatValue(section) }}</span>
                </template>
                <!-- 对象展开为 key-value 表格 -->
                <table v-else class="config-table">
                  <tr v-for="(val, k) in flattenObject(section)" :key="k">
                    <td class="config-key">{{ k }}</td>
                    <td class="config-val">{{ formatValue(val) }}</td>
                  </tr>
                </table>
              </div>
            </div>
            <el-empty v-if="!parsedConfig || !Object.keys(parsedConfig).length" description="无配置数据" />
          </div>
        </el-tab-pane>
        <el-tab-pane label="Schema (原始)" name="schema">
          <div class="json-preview">
            <pre>{{ previewSchema }}</pre>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

    <!-- Create/Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑模板' : '新建模板'"
      width="900px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="模板名称" prop="name">
              <el-input v-model="formData.name" placeholder="如 RNAseq、STAR" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类型" prop="type">
              <el-select v-model="formData.type" style="width: 100%">
                <el-option label="task" value="task" />
                <el-option label="pipeline" value="pipeline" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="分类" prop="category">
              <el-input v-model="formData.category" placeholder="如 转录组、变异检测" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Snakefile路径" prop="snakemakePath">
              <el-input v-model="formData.snakemakePath" placeholder="如 subworkflow/RNAseq.smk" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="2" />
        </el-form-item>

        <el-divider content-position="left">
          默认配置 JSON
          <el-button size="small" style="margin-left: 12px" @click="formatConfig">格式化</el-button>
        </el-divider>
        <el-form-item prop="configTemplate" class="no-label">
          <div class="json-editor-area">
            <div v-if="configJsonError" class="json-error">{{ configJsonError }}</div>
            <el-input
              v-model="formData.configTemplate"
              type="textarea"
              :rows="14"
              :class="{ 'json-error-input': !!configJsonError }"
              style="font-family: 'Cascadia Code', 'Fira Code', 'Consolas', monospace; font-size: 13px"
              spellcheck="false"
              @input="validateConfig"
            />
          </div>
        </el-form-item>

        <el-divider content-position="left">
          Schema JSON
          <el-button size="small" style="margin-left: 12px" @click="formatSchema">格式化</el-button>
        </el-divider>
        <el-form-item prop="schemaJson" class="no-label">
          <div class="json-editor-area">
            <div v-if="schemaJsonError" class="json-error">{{ schemaJsonError }}</div>
            <el-input
              v-model="formData.schemaJson"
              type="textarea"
              :rows="14"
              :class="{ 'json-error-input': !!schemaJsonError }"
              style="font-family: 'Cascadia Code', 'Fira Code', 'Consolas', monospace; font-size: 13px"
              spellcheck="false"
              @input="validateSchema"
            />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" :disabled="!!configJsonError || !!schemaJsonError" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- Import Dialog -->
    <el-dialog v-model="importDialogVisible" title="从 Omics 仓库导入" width="500px">
      <el-form label-width="100px">
        <el-form-item label="Omics 目录">
          <el-input v-model="importDir" placeholder="Omics 仓库绝对路径，如 /home/luosg/Omics" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" @click="handleImport">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Download } from '@element-plus/icons-vue'
import {
  listTemplates, createTemplate, updateTemplate, deleteTemplate, importTemplates
} from '@/api/templateApi'
import type { WorkflowTemplate } from '@/api/templateApi'

const loading = ref(false)
const submitLoading = ref(false)
const importLoading = ref(false)
const dialogVisible = ref(false)
const importDialogVisible = ref(false)
const isEdit = ref(false)
const templateList = ref<WorkflowTemplate[]>([])
const formRef = ref<any>()
const importDir = ref('')

// Preview state
const previewVisible = ref(false)
const previewName = ref('')
const previewTab = ref('config')
const previewConfig = ref('')
const previewSchema = ref('')
const parsedConfig = ref<Record<string, any>>({})

// JSON validation
const configJsonError = ref('')
const schemaJsonError = ref('')

const searchForm = reactive({ name: '', type: '', category: '' })
const pagination = reactive({ page: 1, size: 10, total: 0 })

const formData = reactive({
  id: 0,
  name: '',
  description: '',
  type: 'pipeline',
  category: '',
  configTemplate: '',
  schemaJson: '',
  snakemakePath: '',
  icon: '',
  sortOrder: 0
})

const formRules = {
  name: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  snakemakePath: [{ required: true, message: '请输入 Snakefile 路径', trigger: 'blur' }],
  configTemplate: [{ required: true, message: '请输入默认配置 JSON', trigger: 'blur' }],
  schemaJson: [{ required: true, message: '请输入 Schema JSON', trigger: 'blur' }]
}

const loadTemplates = async () => {
  loading.value = true
  try {
    const res = await listTemplates({ page: pagination.page, size: pagination.size, ...searchForm })
    templateList.value = res.records
    pagination.total = res.total
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}

const resetSearch = () => {
  searchForm.name = ''; searchForm.type = ''; searchForm.category = ''
  pagination.page = 1; loadTemplates()
}

// --- Config Preview ---
function showConfigPreview(row: WorkflowTemplate) {
  previewName.value = row.name
  const raw = row.configTemplate
  const formatted = formatJsonSafe(raw)
  previewConfig.value = formatted
  previewSchema.value = formatJsonSafe(row.schemaJson)
  previewTab.value = 'config'
  // Parse config into sections
  try {
    const obj = typeof raw === 'string' ? JSON.parse(raw) : raw
    parsedConfig.value = obj || {}
  } catch {
    parsedConfig.value = {}
  }
  previewVisible.value = true
}

function formatJsonSafe(val: string | object): string {
  try {
    const obj = typeof val === 'string' ? JSON.parse(val) : val
    return JSON.stringify(obj, null, 2)
  } catch {
    return typeof val === 'string' ? val : JSON.stringify(val)
  }
}

function isObject(val: any): boolean {
  return val !== null && typeof val === 'object' && !Array.isArray(val)
}

/**
 * 将嵌套对象展平为 dot-notation 的 key-value 对
 * 如 { Params: { star: { alignEndsType: "Local" } } } → { "star.alignEndsType": "Local" }
 */
function flattenObject(obj: any, prefix = ''): Record<string, any> {
  const result: Record<string, any> = {}
  if (!obj || typeof obj !== 'object') return result
  for (const [key, val] of Object.entries(obj)) {
    const fullKey = prefix ? `${prefix}.${key}` : key
    if (isObject(val) && Object.keys(val as object).length > 0) {
      Object.assign(result, flattenObject(val, fullKey))
    } else if (Array.isArray(val)) {
      result[fullKey] = val.length ? val.join(', ') : '[]'
    } else {
      result[fullKey] = val
    }
  }
  return result
}

function formatValue(val: any): string {
  if (val === null || val === undefined) return '-'
  if (typeof val === 'boolean') return val ? 'true' : 'false'
  if (typeof val === 'object') return JSON.stringify(val)
  return String(val)
}

// --- JSON validation ---
function validateConfig() {
  try {
    if (formData.configTemplate.trim()) JSON.parse(formData.configTemplate)
    configJsonError.value = ''
  } catch (e: any) { configJsonError.value = e.message }
}

function validateSchema() {
  try {
    if (formData.schemaJson.trim()) JSON.parse(formData.schemaJson)
    schemaJsonError.value = ''
  } catch (e: any) { schemaJsonError.value = e.message }
}

function formatConfig() {
  try {
    formData.configTemplate = JSON.stringify(JSON.parse(formData.configTemplate), null, 2)
    configJsonError.value = ''
  } catch { /* ignore */ }
}

function formatSchema() {
  try {
    formData.schemaJson = JSON.stringify(JSON.parse(formData.schemaJson), null, 2)
    schemaJsonError.value = ''
  } catch { /* ignore */ }
}

// --- CRUD ---
const openCreate = () => {
  isEdit.value = false
  Object.assign(formData, {
    id: 0, name: '', description: '', type: 'pipeline', category: '',
    configTemplate: '{}', schemaJson: '{}', snakemakePath: '', icon: '', sortOrder: 0
  })
  configJsonError.value = ''
  schemaJsonError.value = ''
  dialogVisible.value = true
}

const openEdit = (row: WorkflowTemplate) => {
  isEdit.value = true
  Object.assign(formData, {
    id: row.id,
    name: row.name,
    description: row.description || '',
    type: row.type,
    category: row.category || '',
    configTemplate: formatJsonSafe(row.configTemplate),
    schemaJson: formatJsonSafe(row.schemaJson),
    snakemakePath: row.snakemakePath,
    icon: row.icon || '',
    sortOrder: row.sortOrder || 0
  })
  configJsonError.value = ''
  schemaJsonError.value = ''
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (configJsonError.value || schemaJsonError.value) {
    ElMessage.error('JSON 格式错误，请检查')
    return
  }
  submitLoading.value = true
  try {
    if (isEdit.value) {
      await updateTemplate({ ...formData, enabled: true })
      ElMessage.success('更新成功')
    } else {
      await createTemplate(formData)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadTemplates()
  } catch (e) {
    ElMessage.error(isEdit.value ? '更新失败' : '创建失败')
  } finally { submitLoading.value = false }
}

const handleDelete = async (row: WorkflowTemplate) => {
  try {
    await ElMessageBox.confirm(`确定删除模板"${row.name}"？`, '提示', { type: 'warning' })
    await deleteTemplate(row.id)
    ElMessage.success('删除成功')
    loadTemplates()
  } catch (e) { if (e !== 'cancel') ElMessage.error('删除失败') }
}

const handleImport = async () => {
  if (!importDir.value.trim()) {
    ElMessage.warning('请输入 Omics 目录路径')
    return
  }
  importLoading.value = true
  try {
    const count = await importTemplates(importDir.value.trim())
    ElMessage.success(`导入成功，共 ${count} 个模板`)
    importDialogVisible.value = false
    loadTemplates()
  } catch (e: any) {
    ElMessage.error(e?.message || '导入失败')
  } finally { importLoading.value = false }
}

onMounted(() => loadTemplates())
</script>

<style scoped>
.template-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.json-preview {
  max-height: 60vh;
  overflow: auto;
  background: #1e1e1e;
  border-radius: 8px;
  padding: 16px;
}
.json-preview pre {
  margin: 0;
  font-family: 'Cascadia Code', 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: #d4d4d4;
  white-space: pre-wrap;
  word-break: break-all;
}
.config-sections {
  max-height: 65vh;
  overflow-y: auto;
  padding: 4px;
}
.config-section {
  margin-bottom: 16px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
}
.section-title {
  margin: 0;
  padding: 8px 16px;
  background: #f5f7fa;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  border-bottom: 1px solid #e4e7ed;
}
.section-content {
  padding: 8px 16px;
}
.config-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.config-table tr:not(:last-child) td {
  border-bottom: 1px solid #f0f0f0;
}
.config-table td {
  padding: 6px 8px;
  vertical-align: top;
}
.config-key {
  width: 40%;
  color: #606266;
  font-family: 'Cascadia Code', 'Fira Code', 'Consolas', monospace;
  font-size: 12px;
  word-break: break-all;
}
.config-val {
  color: #303133;
  font-family: 'Cascadia Code', 'Fira Code', 'Consolas', monospace;
  font-size: 12px;
  word-break: break-all;
}
.section-value {
  color: #909399;
  font-size: 13px;
}
.json-editor-area {
  width: 100%;
  position: relative;
}
.json-error {
  font-size: 12px;
  color: #f56c6c;
  margin-bottom: 4px;
}
.json-error-input :deep(textarea) {
  border-color: #f56c6c !important;
}
.no-label {
  margin-bottom: 0;
}
</style>
