<template>
  <div class="pipeline-container">
    <!-- Search Bar -->
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="流程名称">
          <el-input
            v-model="searchForm.name"
            placeholder="请输入流程名称"
            clearable
          />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="searchForm.category" placeholder="请选择分类" clearable>
            <el-option label="预处理" value="preprocessing" />
            <el-option label="比对" value="alignment" />
            <el-option label="变异检测" value="variant_calling" />
            <el-option label="表达分析" value="expression" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
            <el-option label="启用" value="active" />
            <el-option label="禁用" value="inactive" />
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

      <el-table
        v-loading="loading"
        :data="pipelineList"
        style="width: 100%"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="流程名称" min-width="150" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="120">
          <template #default="{ row }">
            <el-tag>{{ getCategoryLabel(row.category) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'">
              {{ row.status === 'active' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="success" link @click="handleExecute(row)">执行</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadPipelines"
          @current-change="loadPipelines"
        />
      </div>
    </el-card>

    <!-- Create/Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑流程' : '新建流程'"
      width="700px"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="流程名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入流程名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            placeholder="请输入流程描述"
          />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="formData.category" placeholder="请选择分类">
            <el-option label="预处理" value="preprocessing" />
            <el-option label="比对" value="alignment" />
            <el-option label="变异检测" value="variant_calling" />
            <el-option label="表达分析" value="expression" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本" prop="version">
          <el-input v-model="formData.version" placeholder="例如: 1.0.0" />
        </el-form-item>
        <el-form-item label="配置" prop="config">
          <el-input
            v-model="formData.config"
            type="textarea"
            :rows="8"
            placeholder="请输入 JSON 配置"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- Execute Dialog -->
    <el-dialog
      v-model="executeDialogVisible"
      title="执行流程"
      width="500px"
    >
      <el-form :model="executeParams" label-width="100px">
        <el-form-item label="选择项目">
          <el-select v-model="executeParams.projectId" placeholder="请选择项目">
            <el-option label="项目A" :value="1" />
            <el-option label="项目B" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="参数配置">
          <el-input
            v-model="executeParams.params"
            type="textarea"
            :rows="6"
            placeholder="请输入执行参数 (JSON)"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="executeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="executeLoading" @click="confirmExecute">
          执行
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { listPipelines, createPipeline, updatePipeline, deletePipeline, executePipeline } from '@/api/pipelineApi'
import type { Pipeline } from '@/api/pipelineApi'

const loading = ref(false)
const submitLoading = ref(false)
const executeLoading = ref(false)
const dialogVisible = ref(false)
const executeDialogVisible = ref(false)
const isEdit = ref(false)
const pipelineList = ref<Pipeline[]>([])
const formRef = ref<FormInstance>()

const searchForm = reactive({
  name: '',
  category: '',
  status: ''
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const formData = reactive({
  id: 0,
  name: '',
  description: '',
  category: '',
  version: '',
  config: ''
})

const executeParams = reactive({
  pipelineId: 0,
  projectId: null as number | null,
  params: ''
})

const formRules: FormRules = {
  name: [
    { required: true, message: '请输入流程名称', trigger: 'blur' }
  ],
  category: [
    { required: true, message: '请选择分类', trigger: 'change' }
  ]
}

const getCategoryLabel = (category: string) => {
  const map: Record<string, string> = {
    preprocessing: '预处理',
    alignment: '比对',
    variant_calling: '变异检测',
    expression: '表达分析',
    other: '其他'
  }
  return map[category] || category
}

const loadPipelines = async () => {
  loading.value = true
  try {
    const res = await listPipelines({
      page: pagination.page,
      size: pagination.size,
      ...searchForm
    })
    pipelineList.value = res.records
    pagination.total = res.total
  } catch (error) {
    console.error('Failed to load pipelines:', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  loadPipelines()
}

const resetSearch = () => {
  searchForm.name = ''
  searchForm.category = ''
  searchForm.status = ''
  handleSearch()
}

const handleCreate = () => {
  isEdit.value = false
  formData.id = 0
  formData.name = ''
  formData.description = ''
  formData.category = ''
  formData.version = ''
  formData.config = ''
  dialogVisible.value = true
}

const handleEdit = (row: Pipeline) => {
  isEdit.value = true
  formData.id = row.id
  formData.name = row.name
  formData.description = row.description
  formData.category = row.category
  formData.version = row.version
  formData.config = row.config
  dialogVisible.value = true
}

const handleExecute = (row: Pipeline) => {
  executeParams.pipelineId = row.id
  executeParams.projectId = null
  executeParams.params = ''
  executeDialogVisible.value = true
}

const handleDelete = async (row: Pipeline) => {
  try {
    await ElMessageBox.confirm(`确定要删除流程"${row.name}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deletePipeline(row.id)
    ElMessage.success('删除成功')
    loadPipelines()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitLoading.value = true
    try {
      if (isEdit.value) {
        await updatePipeline(formData.id, formData)
        ElMessage.success('更新成功')
      } else {
        await createPipeline(formData)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      loadPipelines()
    } catch (error) {
      ElMessage.error(isEdit.value ? '更新失败' : '创建失败')
    } finally {
      submitLoading.value = false
    }
  })
}

const confirmExecute = async () => {
  if (!executeParams.projectId) {
    ElMessage.warning('请选择项目')
    return
  }

  executeLoading.value = true
  try {
    let params = {}
    if (executeParams.params) {
      try {
        params = JSON.parse(executeParams.params)
      } catch {
        ElMessage.error('参数格式错误，请输入有效的 JSON')
        executeLoading.value = false
        return
      }
    }
    await executePipeline(executeParams.pipelineId, params)
    ElMessage.success('执行已启动')
    executeDialogVisible.value = false
  } catch (error) {
    ElMessage.error('执行启动失败')
  } finally {
    executeLoading.value = false
  }
}

onMounted(() => {
  loadPipelines()
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
</style>
