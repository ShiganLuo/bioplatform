<template>
  <div class="worker-view">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>计算节点管理</span>
          <div style="display:flex;gap:8px;align-items:center;">
            <el-tag :type="onlineCount > 0 ? 'success' : 'danger'" effect="light">
              在线: {{ onlineCount }} / {{ workers.length }}
            </el-tag>
            <el-button type="primary" size="small" @click="showAddDialog = true">
              <el-icon><Plus /></el-icon> 添加节点
            </el-button>
            <el-button size="small" circle @click="loadWorkers">
              <el-icon><Refresh /></el-icon>
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="workers" style="width: 100%">
        <el-table-column prop="id" label="节点ID" width="160" />
        <el-table-column prop="hostname" label="主机名" width="140" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.healthy ? 'success' : 'danger'" effect="light" round size="small">
              {{ row.healthy ? '在线' : '离线' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="CPU" width="70">
          <template #default="{ row }">{{ row.cpuCores || '-' }}</template>
        </el-table-column>
        <el-table-column label="可用内存" width="100">
          <template #default="{ row }">{{ row.freeMemoryMB ? row.freeMemoryMB + ' MB' : '-' }}</template>
        </el-table-column>
        <el-table-column prop="url" label="地址" min-width="200" />
        <el-table-column label="启用" width="70">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" @change="(val: boolean) => toggleEnabled(row, val)" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="testNode(row)">测试连接</el-button>
            <el-button size="small" type="danger" link @click="removeNode(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="workers.length === 0" style="text-align:center;padding:40px;color:#909399;">
        <p>暂无计算节点</p>
        <p style="font-size:13px;margin-top:8px;">点击"添加节点"注册内网计算服务器</p>
      </div>
    </el-card>

    <!-- 存储模式说明 -->
    <el-card style="margin-top:16px;">
      <template #header><span>存储模式配置</span></template>
      <el-alert type="info" :closable="false" style="margin-bottom:16px;">
        存储模式决定文件如何在 Gateway 和计算节点之间共享，在 application.yml 中配置。
      </el-alert>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-card shadow="never" class="mode-card">
            <h4>共享存储模式（推荐）</h4>
            <p>所有服务器挂载同一个 NFS/NAS 目录，文件直接读写共享路径，无需网络传输。</p>
            <pre class="config-block">bioplatform:
  storage:
    type: shared
    shared-path: /data/shared/bioplatform</pre>
            <p class="mode-note">适用：内网服务器可挂载同一 NFS</p>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never" class="mode-card">
            <h4>分布式存储模式</h4>
            <p>文件存在内网 Worker 服务器上，Gateway 通过 HTTP 中转文件读写。</p>
            <pre class="config-block">bioplatform:
  storage:
    type: worker</pre>
            <p class="mode-note">适用：内网服务器完全隔离，无共享存储</p>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <!-- 添加节点对话框 -->
    <el-dialog v-model="showAddDialog" title="添加计算节点" width="480px">
      <el-form :model="addForm" label-width="80px">
        <el-form-item label="节点地址" required>
          <el-input v-model="addForm.url" placeholder="http://localhost:18081" />
          <p style="font-size:12px;color:#909399;margin-top:4px;">
            内网服务器通过 SSH 隧道映射到公网后的地址，如 http://localhost:18081
          </p>
        </el-form-item>
        <el-form-item label="主机名">
          <el-input v-model="addForm.hostname" placeholder="可选，如 gpu-server-01" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="addForm.remark" placeholder="可选，如 16核64G / 生信计算专用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button @click="testAndAdd" :loading="testing">测试并添加</el-button>
        <el-button type="primary" @click="addNode" :loading="adding">直接添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/utils/http/axios'

interface WorkerInfo {
  id: string
  url: string
  hostname: string
  cpuCores: number
  freeMemoryMB: number
  healthy: boolean
  status: number
  lastHeartbeat: number
}

const workers = ref<WorkerInfo[]>([])
const showAddDialog = ref(false)
const adding = ref(false)
const testing = ref(false)
const addForm = ref({ url: '', hostname: '', remark: '' })

const onlineCount = computed(() => workers.value.filter(w => w.healthy).length)

async function loadWorkers() {
  try {
    const res = await http.get('/api/admin/workers') as any
    workers.value = Array.isArray(res) ? res : []
  } catch {
    workers.value = []
  }
}

async function addNode() {
  if (!addForm.value.url.trim()) {
    ElMessage.warning('请输入节点地址')
    return
  }
  adding.value = true
  try {
    await http.post('/api/admin/workers', {
      url: addForm.value.url.trim(),
      hostname: addForm.value.hostname.trim() || undefined
    })
    ElMessage.success('节点添加成功')
    showAddDialog.value = false
    addForm.value = { url: '', hostname: '', remark: '' }
    await loadWorkers()
  } catch (e: any) {
    ElMessage.error(e?.message || '添加失败')
  } finally {
    adding.value = false
  }
}

async function testAndAdd() {
  if (!addForm.value.url.trim()) {
    ElMessage.warning('请输入节点地址')
    return
  }
  testing.value = true
  try {
    const res = await http.post('/api/admin/workers/test', { url: addForm.value.url.trim() }) as any
    if (res?.connected) {
      ElMessage.success('连接成功，正在添加...')
      await addNode()
    } else {
      ElMessage.warning('连接失败: ' + (res?.message || '无法连接'))
    }
  } catch (e: any) {
    ElMessage.error('测试连接失败')
  } finally {
    testing.value = false
  }
}

async function removeNode(node: WorkerInfo) {
  try {
    await ElMessageBox.confirm(`确定删除节点 ${node.id}（${node.url}）？`, '确认删除', { type: 'warning' })
    await http.del(`/api/admin/workers/${node.id}`)
    ElMessage.success('已删除')
    await loadWorkers()
  } catch {}
}

async function toggleEnabled(node: WorkerInfo, enabled: boolean) {
  try {
    await http.put(`/api/admin/workers/${node.id}/status`, { enabled })
    ElMessage.success(enabled ? '已启用' : '已禁用')
    await loadWorkers()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function testNode(node: WorkerInfo) {
  try {
    const res = await http.post('/api/admin/workers/test', { url: node.url }) as any
    if (res?.connected) {
      ElMessage.success(`${node.id} 连接正常`)
    } else {
      ElMessage.warning(`${node.id} 连接失败`)
    }
  } catch {
    ElMessage.error('测试失败')
  }
}

onMounted(loadWorkers)
</script>

<style scoped>
.worker-view { padding: 0; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.mode-card { margin-bottom: 0; }
.mode-card h4 { margin: 0 0 8px; color: #303133; font-size: 15px; }
.mode-card p { color: #606266; font-size: 13px; line-height: 1.6; margin: 0 0 8px; }
.mode-note { color: #909399 !important; font-size: 12px !important; margin-top: 4px !important; }
.config-block {
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 10px 14px;
  font-size: 12px;
  font-family: monospace;
  line-height: 1.5;
  overflow-x: auto;
  margin: 8px 0;
}
</style>
