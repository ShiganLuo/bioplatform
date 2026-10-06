<template>
  <div class="log-container">
    <!-- Search Bar -->
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="用户名">
          <el-input
            v-model="searchForm.username"
            placeholder="操作者"
            clearable
            style="width: 130px"
          />
        </el-form-item>
        <el-form-item label="模块">
          <el-select
            v-model="searchForm.module"
            placeholder="全部模块"
            clearable
            style="width: 150px"
          >
            <el-option v-for="m in moduleOptions" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="searchForm.status"
            placeholder="全部"
            clearable
            style="width: 110px"
          >
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAIL" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="操作/参数/接口"
            clearable
            style="width: 170px"
          />
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="searchForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
          />
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
          <div class="header-left">
            <span>操作日志</span>
            <el-button
              size="small"
              :type="searchForm.status === 'FAIL' ? 'danger' : 'default'"
              @click="toggleFailOnly"
            >
              只看失败
            </el-button>
            <el-button size="small" @click="toggleAuthOnly">
              {{ searchForm.module === '认证' ? '查看全部模块' : '登录日志' }}
            </el-button>
          </div>
          <el-button size="small" @click="loadLogs">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="logList" style="width: 100%">
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="用户名" width="110">
          <template #default="{ row }">{{ row.username || '-' }}</template>
        </el-table-column>
        <el-table-column label="模块" width="120">
          <template #default="{ row }">{{ row.module || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="140">
          <template #default="{ row }">{{ row.operation || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'FAIL' ? 'danger' : 'success'">
              {{ row.status === 'FAIL' ? '失败' : '成功' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="IP 地址" width="140">
          <template #default="{ row }">{{ row.ip || '-' }}</template>
        </el-table-column>
        <el-table-column label="接口" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.method || '-' }}</template>
        </el-table-column>
        <el-table-column label="结果" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.result || '-' }}</template>
        </el-table-column>
        <el-table-column label="详情" width="70" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
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
          @size-change="loadLogs"
          @current-change="loadLogs"
        />
      </div>
    </el-card>

    <!-- Detail Drawer -->
    <el-drawer v-model="drawerVisible" title="操作详情" size="42%">
      <el-descriptions v-if="currentLog" :column="1" border>
        <el-descriptions-item label="时间">
          {{ formatTime(currentLog.createdAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="用户名">
          {{ currentLog.username || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="模块">
          {{ currentLog.module || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="操作">
          {{ currentLog.operation || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="currentLog.status === 'FAIL' ? 'danger' : 'success'">
            {{ currentLog.status === 'FAIL' ? '失败' : '成功' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="接口">
          {{ currentLog.method || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="IP 地址">
          {{ currentLog.ip || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="结果">
          {{ currentLog.result || '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <div class="detail-block" v-if="currentLog">
        <div class="detail-title">请求参数（敏感字段已脱敏）</div>
        <pre class="detail-pre">{{ formatParams(currentLog.params) }}</pre>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getSystemLogs } from '@/api/systemApi'

interface LogItem {
  id: number
  userId: number | null
  username: string | null
  module: string | null
  operation: string
  method: string | null
  params: string | null
  result: string | null
  status: string
  ip: string | null
  createdAt: string
}

const moduleOptions = [
  '认证',
  '用户管理',
  '项目管理',
  '流水线管理',
  '执行管理',
  '模板管理',
  '数据文件管理',
  '样本信息',
  'AI Agent管理',
  '系统管理',
  '计算节点',
  '反馈管理'
]

const loading = ref(false)
const logList = ref<LogItem[]>([])
const drawerVisible = ref(false)
const currentLog = ref<LogItem | null>(null)

const searchForm = reactive({
  username: '',
  module: '',
  status: '',
  keyword: '',
  dateRange: null as [string, string] | null
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const formatTime = (value?: string) => {
  if (!value) return '-'
  return value.replace('T', ' ').substring(0, 19)
}

const formatParams = (value?: string) => {
  if (!value) return '无'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

const loadLogs = async () => {
  loading.value = true
  try {
    const params: any = {
      page: pagination.page,
      size: pagination.size
    }
    if (searchForm.username) params.username = searchForm.username
    if (searchForm.module) params.module = searchForm.module
    if (searchForm.status) params.status = searchForm.status
    if (searchForm.keyword) params.keyword = searchForm.keyword
    if (searchForm.dateRange) {
      params.startDate = searchForm.dateRange[0]
      params.endDate = searchForm.dateRange[1]
    }

    const res = await getSystemLogs(params)
    logList.value = (res as any).records || []
    pagination.total = (res as any).total || 0
  } catch (error) {
    console.error('Failed to load logs:', error)
    ElMessage.error('日志加载失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  loadLogs()
}

const resetSearch = () => {
  searchForm.username = ''
  searchForm.module = ''
  searchForm.status = ''
  searchForm.keyword = ''
  searchForm.dateRange = null
  handleSearch()
}

const toggleFailOnly = () => {
  searchForm.status = searchForm.status === 'FAIL' ? '' : 'FAIL'
  handleSearch()
}

const toggleAuthOnly = () => {
  searchForm.module = searchForm.module === '认证' ? '' : '认证'
  handleSearch()
}

const openDetail = (row: LogItem) => {
  currentLog.value = row
  drawerVisible.value = true
}

onMounted(() => {
  loadLogs()
})
</script>

<style scoped>
.log-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.detail-block {
  margin-top: 16px;
}

.detail-title {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}

.detail-pre {
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 12px;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 320px;
  overflow: auto;
}
</style>
