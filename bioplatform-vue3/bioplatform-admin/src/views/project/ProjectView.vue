<template>
  <div class="project-container">
    <!-- Search Bar -->
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="项目名称">
          <el-input v-model="searchForm.name" placeholder="请输入项目名称" clearable />
        </el-form-item>
        <el-form-item label="物种">
          <el-input v-model="searchForm.organism" placeholder="请输入物种" clearable />
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
          <span>项目列表</span>
          <el-button type="primary" @click="handleCreate">
            <el-icon><Plus /></el-icon>
            新建项目
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="projectList" style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="项目名称" min-width="150">
          <template #default="{ row }">
            <el-button type="primary" link @click="router.push(`/projects/${row.id}`)">{{ row.name }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="organism" label="物种" width="120" />
        <el-table-column prop="genomeVersion" label="基因组版本" width="120" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isPrivate" label="可见性" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isPrivate ? 'warning' : 'success'" size="small">
              {{ row.isPrivate ? '私有' : '公开' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row as Project)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row as Project)">删除</el-button>
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
          @size-change="loadProjects"
          @current-change="loadProjects"
        />
      </div>
    </el-card>

    <!-- Create/Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑项目' : '新建项目'"
      width="600px"
      @close="handleDialogClose"
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入项目描述" />
        </el-form-item>
        <el-form-item label="物种" prop="organism">
          <el-select
            v-model="formData.organismArray"
            filterable
            allow-create
            multiple
            placeholder="选择或输入物种"
            style="width: 100%"
          >
            <el-option
              v-for="item in organismOptions"
              :key="item"
              :label="item"
              :value="item"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="基因组版本" prop="genomeVersion">
          <el-select
            v-model="formData.genomeVersion"
            filterable
            allow-create
            placeholder="选择或输入基因组版本"
            style="width: 100%"
          >
            <el-option
              v-for="item in genomeVersionOptions"
              :key="item"
              :label="item"
              :value="item"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="私有项目">
          <el-switch v-model="formData.isPrivate" />
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker
            v-model="formData.createdAt"
            type="datetime"
            :placeholder="isEdit ? '不修改请留空' : '默认当前时间'"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
            :default-time="new Date(2000, 1, 1, 9, 0, 0)"
            style="width: 100%"
          />
          <div style="font-size: 12px; color: #909399; margin-top: 4px;">{{ isEdit ? '留空则不修改原创建时间' : '留空则使用当前时间，补录历史项目时可手动指定' }}</div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="handleCancel">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/theme-chalk/el-message-box.css'
import 'element-plus/theme-chalk/el-message.css'
import { Plus } from '@element-plus/icons-vue'
import { listProjects, createProject, updateProject, deleteProject, getOrganisms, getGenomeVersions } from '@/api/projectApi'
import type { Project } from '@/api/projectApi'

const loading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const router = useRouter()
const isEdit = ref(false)
const projectList = ref<Project[]>([])
const formRef = ref<any>()
const organismOptions = ref<string[]>([])
const genomeVersionOptions = ref<string[]>([])

const searchForm = reactive({
  name: '',
  organism: ''
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
  organism: '',
  organismArray: [] as string[],
  genomeVersion: '',
  isPrivate: false,
  createdAt: '' as string
})

const formRules: Record<string, any[]> = {
  name: [
    { required: true, message: '请输入项目名称', trigger: 'blur' }
  ]
}

// 草稿自动保存 (localStorage)
const DRAFT_KEY = 'project_create_draft'

const saveDraft = () => {
  if (isEdit.value) return // 编辑模式不保存草稿
  const draft = {
    name: formData.name,
    description: formData.description,
    organism: formData.organismArray.join(','),
    genomeVersion: formData.genomeVersion,
    isPrivate: formData.isPrivate,
    createdAt: formData.createdAt
  }
  // 只在有实际内容时保存
  if (draft.name || draft.description || draft.organism || draft.genomeVersion) {
    localStorage.setItem(DRAFT_KEY, JSON.stringify(draft))
  }
}

const restoreDraft = (): boolean => {
  const raw = localStorage.getItem(DRAFT_KEY)
  if (!raw) return false
  try {
    const draft = JSON.parse(raw)
    formData.name = draft.name || ''
    formData.description = draft.description || ''
    formData.organism = draft.organism || ''
    formData.organismArray = draft.organism ? draft.organism.split(',').filter(Boolean) : []
    formData.genomeVersion = draft.genomeVersion || ''
    formData.isPrivate = draft.isPrivate || false
    formData.createdAt = draft.createdAt || ''
    return true
  } catch {
    return false
  }
}

const clearDraft = () => {
  localStorage.removeItem(DRAFT_KEY)
}

const submitted = ref(false)

const handleCancel = () => {
  if (!isEdit.value && !submitted.value) saveDraft() // 取消时保存草稿（提交后不保存）
  dialogVisible.value = false
}

const handleDialogClose = () => {
  if (!isEdit.value && !submitted.value) saveDraft()
}

const getStatusType = (status: number): 'success' | 'warning' | 'info' | 'danger' => {
  const map: Record<number, 'success' | 'warning' | 'info' | 'danger'> = {
    0: 'info',    // 草稿
    1: 'success', // 活跃
    2: 'warning'  // 归档
  }
  return map[status] || 'info'
}

const getStatusLabel = (status: number) => {
  const map: Record<number, string> = {
    0: '草稿',
    1: '活跃',
    2: '归档'
  }
  return map[status] || '未知'
}

const loadProjects = async () => {
  loading.value = true
  try {
    const res = await listProjects({
      page: pagination.page,
      size: pagination.size,
      ...searchForm
    })
    projectList.value = res.records
    pagination.total = res.total
  } catch (error) {
    console.error('Failed to load projects:', error)
  } finally {
    loading.value = false
  }
}

const loadOptions = async () => {
  try {
    const [organisms, versions] = await Promise.all([getOrganisms(), getGenomeVersions()])
    organismOptions.value = (organisms as any) || []
    genomeVersionOptions.value = (versions as any) || []
  } catch (e) {
    console.error('Failed to load options:', e)
  }
}

const handleSearch = () => {
  pagination.page = 1
  loadProjects()
}

const resetSearch = () => {
  searchForm.name = ''
  searchForm.organism = ''
  handleSearch()
}

const handleCreate = () => {
  isEdit.value = false
  submitted.value = false
  formData.id = 0
  // 先清空，再尝试恢复草稿
  formData.name = ''
  formData.description = ''
  formData.organism = ''
  formData.organismArray = []
  formData.genomeVersion = ''
  formData.isPrivate = false
  formData.createdAt = ''
  restoreDraft()
  if (formData.name || formData.description || formData.organism || formData.genomeVersion) {
    ElMessage({ message: '已恢复上次未提交的草稿', type: 'info', duration: 2000 })
  }
  dialogVisible.value = true
}

const handleEdit = (row: Project) => {
  isEdit.value = true
  formData.id = row.id
  formData.name = row.name
  formData.description = row.description || ''
  formData.organism = row.organism || ''
  formData.organismArray = row.organism ? row.organism.split(',').filter(Boolean) : []
  formData.genomeVersion = row.genomeVersion || ''
  formData.isPrivate = row.isPrivate || false
  formData.createdAt = ''
  dialogVisible.value = true
}

const handleDelete = async (row: Project) => {
  try {
    await ElMessageBox.confirm(`确定要删除项目"${row.name}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteProject(row.id)
    ElMessage.success('删除成功')
    loadProjects()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid: boolean) => {
    if (!valid) return
    submitLoading.value = true
    try {
      // 将organismArray转换为逗号分隔的字符串
      const submitData = {
        ...formData,
        organism: formData.organismArray.join(',')
      }

      if (isEdit.value) {
        const updateData = { ...submitData }
        if (!updateData.createdAt) {
          (updateData as any).createdAt = undefined
        }
        await updateProject(formData.id, updateData)
        ElMessage.success('更新成功')
      } else {
        await createProject(submitData.createdAt ? submitData : { ...submitData, createdAt: undefined })
        ElMessage.success('创建成功')
        submitted.value = true
        clearDraft()
      }
      dialogVisible.value = false
      loadProjects()
    } catch (error) {
      ElMessage.error(isEdit.value ? '更新失败' : '创建失败')
    } finally {
      submitLoading.value = false
    }
  })
}

onMounted(() => {
  loadProjects()
  loadOptions()
})
</script>

<style scoped>
.project-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.search-card {
  margin-bottom: 0;
}

.search-form {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
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
