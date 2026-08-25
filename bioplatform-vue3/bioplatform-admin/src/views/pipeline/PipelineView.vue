<template>
  <div class="pipeline-container">
    <!-- Search Bar -->
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="流程名称">
          <el-input v-model="searchForm.name" placeholder="请输入流程名称" clearable />
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
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Table -->
    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <span>流程列表</span>
          <el-button type="primary" @click="handleCreate">
            <el-icon><Plus /></el-icon>
            新建流程
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="pipelineList" style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="流程名称" min-width="150" />
        <el-table-column prop="type" label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.type === 'pipeline' ? 'primary' : 'success'" size="small">
              {{ row.type }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column label="所属项目" width="120">
          <template #default="{ row }">
            <span v-if="getProjectName(row.projectId)">{{ getProjectName(row.projectId) }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row as Pipeline)">编辑</el-button>
            <el-button type="success" link @click="handleExecute(row as Pipeline)">执行</el-button>
            <el-button type="danger" link @click="handleDelete(row as Pipeline)">删除</el-button>
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
          @size-change="loadPipelines"
          @current-change="loadPipelines"
        />
      </div>
    </el-card>

    <!-- Step 1: Type + Template Selection Dialog -->
    <el-dialog v-model="createDialogVisible" title="新建流程" width="600px">
      <el-steps :active="createStep" finish-status="success" align-center style="margin-bottom: 24px">
        <el-step title="选择类型" />
        <el-step title="选择模板" />
        <el-step title="编辑配置" />
      </el-steps>

      <!-- Step 0: Type Selection -->
      <div v-if="createStep === 0" class="type-selector">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-card
              shadow="hover"
              class="type-card"
              :class="{ active: selectedType === 'task' }"
              @click="selectedType = 'task'"
            >
              <el-icon :size="36" color="#67c23a"><Monitor /></el-icon>
              <h3>task</h3>
              <p>对应一个 Snakemake 模块，如 STAR 比对、FastQC 质控。可配置独立环境。</p>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card
              shadow="hover"
              class="type-card"
              :class="{ active: selectedType === 'pipeline' }"
              @click="selectedType = 'pipeline'"
            >
              <el-icon :size="36" color="#409eff"><Connection /></el-icon>
              <h3>pipeline</h3>
              <p>对应完整的 Snakemake 子工作流，如 RNAseq 全流程、体细胞突变分析。</p>
            </el-card>
          </el-col>
        </el-row>
        <div style="text-align: right; margin-top: 16px">
          <el-button @click="createDialogVisible = false">取消</el-button>
          <el-button type="primary" :disabled="!selectedType" @click="createStep = 1">下一步</el-button>
        </div>
      </div>

      <!-- Step 1: Template Selection -->
      <div v-if="createStep === 1">
        <div v-loading="templateLoading" class="template-grid">
          <el-empty v-if="!templateList.length" description="暂无模板，请先在系统管理中导入" />
          <el-radio-group v-model="selectedTemplateId" class="template-radio-group">
            <el-radio-button
              v-for="tpl in templateList"
              :key="tpl.id"
              :value="tpl.id"
              class="template-option"
            >
              <div class="template-option-content">
                <strong>{{ tpl.name }}</strong>
                <span class="template-desc">{{ tpl.description }}</span>
                <el-tag size="small" type="info">{{ tpl.category }}</el-tag>
              </div>
            </el-radio-button>
          </el-radio-group>
        </div>
        <div style="text-align: right; margin-top: 16px">
          <el-button @click="createStep = 0">上一步</el-button>
          <el-button type="primary" :disabled="!selectedTemplateId" @click="loadTemplateAndNext">下一步</el-button>
        </div>
      </div>

      <!-- Step 2: JSON Editor -->
      <div v-if="createStep === 2">
        <el-form :model="formData" label-width="100px">
          <el-form-item label="流程名称">
            <el-input v-model="formData.name" placeholder="请输入流程名称" />
          </el-form-item>
          <el-form-item label="所属项目">
            <el-select v-model="formData.projectId" placeholder="请选择项目（可选）" clearable style="width: 100%">
              <el-option v-for="p in projectList" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="formData.description" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item label="超时(秒)">
            <el-input-number v-model="formData.timeout" :min="0" :max="86400" />
          </el-form-item>

          <el-divider content-position="left">
            配置 JSON
            <el-button size="small" style="margin-left: 12px" @click="formatJson">
              格式化
            </el-button>
          </el-divider>

          <div class="json-editor-wrapper">
            <div v-if="jsonError" class="json-error">{{ jsonError }}</div>
            <el-input
              v-model="jsonText"
              type="textarea"
              :rows="24"
              :class="{ 'json-error-input': !!jsonError }"
              style="font-family: 'Cascadia Code', 'Fira Code', 'Consolas', monospace; font-size: 13px; line-height: 1.5"
              spellcheck="false"
              @input="validateJson"
            />
          </div>
        </el-form>
        <div style="text-align: right; margin-top: 16px">
          <el-button @click="createStep = 1">上一步</el-button>
          <el-button type="primary" :loading="submitLoading" :disabled="!!jsonError" @click="handleSubmit">创建</el-button>
        </div>
      </div>
    </el-dialog>

    <!-- Edit Dialog -->
    <el-dialog v-model="editDialogVisible" title="编辑流程" width="800px">
      <el-form :model="editData" label-width="100px">
        <el-form-item label="流程名称">
          <el-input v-model="editData.name" />
        </el-form-item>
        <el-form-item label="所属项目">
          <el-select v-model="editData.projectId" placeholder="请选择项目（可选）" clearable style="width: 100%">
            <el-option v-for="p in projectList" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editData.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-divider content-position="left">
          配置 JSON
          <el-button size="small" style="margin-left: 12px" @click="formatEditJson">格式化</el-button>
        </el-divider>
        <div class="json-editor-wrapper">
          <div v-if="editJsonError" class="json-error">{{ editJsonError }}</div>
          <el-input
            v-model="editJsonText"
            type="textarea"
            :rows="20"
            :class="{ 'json-error-input': !!editJsonError }"
            style="font-family: 'Cascadia Code', 'Fira Code', 'Consolas', monospace; font-size: 13px; line-height: 1.5"
            spellcheck="false"
            @input="validateEditJson"
          />
        </div>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" :disabled="!!editJsonError" @click="handleUpdate">保存</el-button>
      </template>
    </el-dialog>

    <!-- Execute Dialog -->
    <el-dialog v-model="executeDialogVisible" title="执行流程" width="500px">
      <el-form :model="executeParams" label-width="100px">
        <el-form-item label="选择项目">
          <el-select v-model="executeParams.projectId" placeholder="请选择项目">
            <el-option v-for="p in projectList" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="executeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="executeLoading" @click="confirmExecute">执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Monitor, Connection } from '@element-plus/icons-vue'
import { listPipelines, createPipeline, updatePipeline, deletePipeline, executePipeline } from '@/api/pipelineApi'
import type { Pipeline } from '@/api/pipelineApi'
import { listTemplates, getTemplate } from '@/api/templateApi'
import type { WorkflowTemplate } from '@/api/templateApi'
import { listProjects } from '@/api/projectApi'
import type { Project } from '@/api/projectApi'

const loading = ref(false)
const submitLoading = ref(false)
const executeLoading = ref(false)
const templateLoading = ref(false)
const createDialogVisible = ref(false)
const editDialogVisible = ref(false)
const executeDialogVisible = ref(false)
const pipelineList = ref<Pipeline[]>([])
const templateList = ref<WorkflowTemplate[]>([])
const projectList = ref<Project[]>([])

// Create flow state
const createStep = ref(0)
const selectedType = ref('')
const selectedTemplateId = ref<number | undefined>(undefined)

// JSON editor state (create)
const jsonText = ref('')
const jsonError = ref('')

// JSON editor state (edit)
const editJsonText = ref('')
const editJsonError = ref('')

const searchForm = reactive({ name: '', type: '', category: '' })
const pagination = reactive({ page: 1, size: 10, total: 0 })

const formData = reactive({
  name: '',
  description: '',
  timeout: 3600,
  projectId: undefined as number | undefined
})

const editData = reactive({
  id: 0,
  name: '',
  description: '',
  type: '',
  templateId: undefined as number | undefined,
  projectId: undefined as number | undefined
})

const executeParams = reactive({
  pipelineId: 0,
  projectId: undefined as number | undefined
})

// --- Data loading ---
const loadPipelines = async () => {
  loading.value = true
  try {
    const res = await listPipelines({ page: pagination.page, size: pagination.size, ...searchForm })
    pipelineList.value = res.records
    pagination.total = res.total
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}

const handleSearch = () => { pagination.page = 1; loadPipelines() }
const resetSearch = () => { searchForm.name = ''; searchForm.type = ''; searchForm.category = ''; handleSearch() }

const loadProjectList = async () => {
  try {
    const res = await listProjects({ page: 1, size: 100 })
    projectList.value = res.records
  } catch (e) { console.error(e) }
}

const getProjectName = (projectId: number | undefined) => {
  if (!projectId) return ''
  const p = projectList.value.find(p => p.id === projectId)
  return p ? p.name : ''
}

// --- JSON helpers ---
function validateJson() {
  try {
    JSON.parse(jsonText.value)
    jsonError.value = ''
  } catch (e: any) {
    jsonError.value = e.message
  }
}

function validateEditJson() {
  try {
    JSON.parse(editJsonText.value)
    editJsonError.value = ''
  } catch (e: any) {
    editJsonError.value = e.message
  }
}

function formatJson() {
  try {
    jsonText.value = JSON.stringify(JSON.parse(jsonText.value), null, 2)
    jsonError.value = ''
  } catch { /* ignore */ }
}

function formatEditJson() {
  try {
    editJsonText.value = JSON.stringify(JSON.parse(editJsonText.value), null, 2)
    editJsonError.value = ''
  } catch { /* ignore */ }
}

// --- Create flow ---
const handleCreate = () => {
  createStep.value = 0
  selectedType.value = ''
  selectedTemplateId.value = undefined
  jsonText.value = ''
  jsonError.value = ''
  formData.name = ''
  formData.description = ''
  formData.timeout = 3600
  formData.projectId = undefined
  createDialogVisible.value = true
}

const loadTemplatesForType = async () => {
  templateLoading.value = true
  try {
    const res = await listTemplates({ page: 1, size: 50, type: selectedType.value })
    templateList.value = res.records
  } catch (e) { console.error(e) }
  finally { templateLoading.value = false }
}

watch(selectedType, (val) => {
  if (val) loadTemplatesForType()
})

const loadTemplateAndNext = async () => {
  if (!selectedTemplateId.value) return
  try {
    const tpl = await getTemplate(selectedTemplateId.value)

    // 从 configTemplate 加载默认值，过滤掉系统自动填充的字段
    const config = typeof tpl.configTemplate === 'string' ? JSON.parse(tpl.configTemplate) : tpl.configTemplate
    const SYSTEM_FIELDS = ['ROOT_DIR', 'indir', 'outdir', 'logdir', 'raw_files', 'outfiles']
    const filtered: Record<string, any> = {}
    if (config) {
      for (const [key, val] of Object.entries(config)) {
        if (!SYSTEM_FIELDS.includes(key)) {
          filtered[key] = val
        }
      }
    }
    jsonText.value = JSON.stringify(filtered, null, 2)
    jsonError.value = ''

    if (!formData.name) formData.name = tpl.name
    createStep.value = 2
  } catch (e) {
    ElMessage.error('加载模板失败')
  }
}

const handleSubmit = async () => {
  if (jsonError.value) {
    ElMessage.error('JSON 格式错误，请检查')
    return
  }
  if (!formData.name.trim()) {
    ElMessage.warning('请输入流程名称')
    return
  }
  submitLoading.value = true
  try {
    await createPipeline({
      name: formData.name,
      type: selectedType.value,
      templateId: selectedTemplateId.value!,
      projectId: formData.projectId,
      description: formData.description,
      configJson: jsonText.value.trim() || '{}',
      metaContent: '',
      metaType: 'text',
      extraParams: ''
    })
    ElMessage.success('创建成功')
    createDialogVisible.value = false
    loadPipelines()
  } catch (e) {
    ElMessage.error('创建失败')
  } finally {
    submitLoading.value = false
  }
}

// --- Edit ---
const handleEdit = (row: Pipeline) => {
  editData.id = row.id
  editData.name = row.name
  editData.description = row.description || ''
  editData.type = row.type
  editData.templateId = row.templateId
  editData.projectId = row.projectId
  editJsonText.value = typeof row.configJson === 'string'
    ? row.configJson
    : JSON.stringify(row.configJson, null, 2)
  editJsonError.value = ''
  editDialogVisible.value = true
}

const handleUpdate = async () => {
  if (editJsonError.value) {
    ElMessage.error('JSON 格式错误，请检查')
    return
  }
  submitLoading.value = true
  try {
    await updatePipeline(editData.id, {
      name: editData.name,
      description: editData.description,
      type: editData.type,
      templateId: editData.templateId,
      projectId: editData.projectId,
      configJson: editJsonText.value.trim() || '{}',
      metaContent: '',
      metaType: 'text',
      extraParams: ''
    })
    ElMessage.success('更新成功')
    editDialogVisible.value = false
    loadPipelines()
  } catch (e) {
    ElMessage.error('更新失败')
  } finally {
    submitLoading.value = false
  }
}

// --- Execute ---
const handleExecute = (row: Pipeline) => {
  executeParams.pipelineId = row.id
  executeParams.projectId = undefined
  executeDialogVisible.value = true
}

const confirmExecute = async () => {
  if (!executeParams.projectId) {
    ElMessage.warning('请选择项目')
    return
  }
  executeLoading.value = true
  try {
    await executePipeline(executeParams.pipelineId, { projectId: executeParams.projectId })
    ElMessage.success('执行已启动')
    executeDialogVisible.value = false
  } catch (e) {
    ElMessage.error('执行启动失败')
  } finally {
    executeLoading.value = false
  }
}

// --- Delete ---
const handleDelete = async (row: Pipeline) => {
  try {
    await ElMessageBox.confirm(`确定要删除流程"${row.name}"吗？`, '提示', { type: 'warning' })
    await deletePipeline(row.id)
    ElMessage.success('删除成功')
    loadPipelines()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

onMounted(() => {
  loadPipelines()
  loadProjectList()
})
</script>

<style scoped>
.pipeline-container {
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

.text-muted {
  color: #c0c4cc;
}
.type-selector {
  padding: 8px 0;
}
.type-card {
  cursor: pointer;
  text-align: center;
  padding: 16px;
  transition: all 0.2s;
  border: 2px solid transparent;
}
.type-card:hover {
  border-color: #409eff;
}
.type-card.active {
  border-color: #409eff;
  background: #ecf5ff;
}
.type-card h3 {
  margin: 12px 0 8px;
  font-size: 16px;
}
.type-card p {
  margin: 0;
  font-size: 13px;
  color: #909399;
  line-height: 1.5;
}
.template-radio-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  width: 100%;
}
.template-option {
  width: calc(50% - 4px);
}
.template-option-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 8px;
}
.template-desc {
  font-size: 12px;
  color: #909399;
  text-align: center;
}
.json-editor-wrapper {
  position: relative;
}
.json-error {
  position: absolute;
  top: -20px;
  right: 0;
  font-size: 12px;
  color: #f56c6c;
}
.json-error-input :deep(textarea) {
  border-color: #f56c6c !important;
}
</style>
