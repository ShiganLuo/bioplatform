<template>
  <div class="execution-container">
    <!-- Search Bar -->
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="流程">
          <el-input
            v-model="searchForm.pipelineName"
            placeholder="请输入流程名称"
            clearable
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
            <el-option label="运行中" value="RUNNING" />
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
            <el-option label="等待中" value="PENDING" />
            <el-option label="已取消" value="CANCELLED" />
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
          <span>执行记录</span>
          <el-button @click="loadExecutions">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="executionList"
        style="width: 100%"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="pipelineName" label="流程名称" min-width="150" />
        <el-table-column prop="projectName" label="项目名称" min-width="150" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="progress" label="进度" width="170">
          <template #default="{ row }">
            <el-progress
              :percentage="row.progress || 0"
              :status="row.status === 'FAILED' ? 'exception' : row.status === 'SUCCESS' ? 'success' : undefined"
            />
            <div v-if="row.progressText" class="progress-text">{{ row.progressText }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="executor" label="执行人" width="100" />
        <el-table-column prop="startTime" label="开始时间" width="180" />
        <el-table-column prop="endTime" label="结束时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleViewDetail(row as Execution)">详情</el-button>
            <el-button
              v-if="row.status === 'RUNNING' || row.status === 'PENDING'"
              type="danger"
              link
              @click="handleCancel(row as Execution)"
            >
              取消
            </el-button>
            <el-button
              v-if="userStore.hasRole('ROLE_ADMIN')"
              type="danger"
              link
              @click="handleDelete(row as Execution)"
            >
              删除
            </el-button>
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
          @size-change="loadExecutions"
          @current-change="loadExecutions"
        />
      </div>
    </el-card>

    <!-- Detail Dialog -->
    <el-dialog
      v-model="detailDialogVisible"
      title="执行详情"
      width="700px"
    >
      <el-descriptions :column="2" border>
        <el-descriptions-item label="执行ID">{{ detailData.id }}</el-descriptions-item>
        <el-descriptions-item label="流程名称">{{ detailData.pipelineName }}</el-descriptions-item>
        <el-descriptions-item label="项目名称">{{ detailData.projectName }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(detailData.status)">
            {{ getStatusLabel(detailData.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="执行人">{{ detailData.executor }}</el-descriptions-item>
        <el-descriptions-item label="进度">
          <el-progress :percentage="detailData.progress || 0" />
          <span v-if="detailData.progressText" class="progress-text">{{ detailData.progressText }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ detailData.startTime }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ detailData.endTime || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider>执行参数</el-divider>
      <el-input
        v-model="detailData.params"
        type="textarea"
        :rows="4"
        readonly
      />

      <el-divider v-if="detailData.result">执行结果</el-divider>
      <el-input
        v-if="detailData.result"
        v-model="detailData.result"
        type="textarea"
        :rows="4"
        readonly
      />

      <el-divider v-if="detailData.errorMessage">错误信息</el-divider>
      <el-input
        v-if="detailData.errorMessage"
        v-model="detailData.errorMessage"
        type="textarea"
        :rows="4"
        readonly
        style="color: #f56c6c"
      />

      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { listExecutions, getExecution, cancelExecution, deleteExecution } from '@/api/executionApi'
import type { Execution } from '@/api/executionApi'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const loading = ref(false)
const executionList = ref<Execution[]>([])
const detailDialogVisible = ref(false)
const detailData = ref<Execution>({} as Execution)

const searchForm = reactive({
  pipelineName: '',
  status: ''
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const getStatusType = (status: string): 'success' | 'warning' | 'info' | 'danger' => {
  const map: Record<string, 'success' | 'warning' | 'info' | 'danger'> = {
    SUCCESS: 'success',
    RUNNING: 'warning',
    FAILED: 'danger',
    PENDING: 'info',
    CANCELLED: 'info'
  }
  return map[status] || 'info'
}

const getStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    SUCCESS: '成功',
    RUNNING: '运行中',
    FAILED: '失败',
    PENDING: '等待中',
    CANCELLED: '已取消'
  }
  return map[status] || status
}

const loadExecutions = async () => {
  loading.value = true
  try {
    const res = await listExecutions({
      page: pagination.page,
      size: pagination.size,
      ...searchForm
    })
    executionList.value = res.records
    pagination.total = res.total
  } catch (error) {
    console.error('Failed to load executions:', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  loadExecutions()
}

const resetSearch = () => {
  searchForm.pipelineName = ''
  searchForm.status = ''
  handleSearch()
}

const handleDelete = async (row: Execution) => {
  try {
    await ElMessageBox.confirm(
      `确认删除执行记录 #${row.id}?只删记录,产物文件保留。`,
      '删除执行记录',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteExecution(row.id!)
    ElMessage.success('执行记录已删除')
    loadExecutions()
  } catch (error) {
    console.error('Failed to delete execution:', error)
  }
}

const handleViewDetail = async (row: Execution) => {
  try {
    const res = await getExecution(row.id)
    detailData.value = res as any
    detailDialogVisible.value = true
  } catch (error) {
    ElMessage.error('获取详情失败')
  }
}

const handleCancel = async (row: Execution) => {
  try {
    await ElMessageBox.confirm(`确定要取消执行"${row.id}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await cancelExecution(row.id)
    ElMessage.success('取消成功')
    loadExecutions()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('取消失败')
    }
  }
}

onMounted(() => {
  loadExecutions()
})
</script>

<style scoped>
.execution-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.progress-text {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  line-height: 1.3;
  word-break: break-all;
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
