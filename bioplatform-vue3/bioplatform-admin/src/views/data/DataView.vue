<template>
  <div class="data-container">
    <!-- Search Bar -->
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="项目">
          <el-select v-model="searchForm.projectId" placeholder="请选择项目" clearable>
            <el-option
              v-for="project in projectList"
              :key="project.id"
              :label="project.name"
              :value="project.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="文件名">
          <el-input
            v-model="searchForm.fileName"
            placeholder="请输入文件名"
            clearable
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
          <span>文件列表</span>
          <div class="header-actions">
            <el-button type="primary" @click="openUploadDialog('file')">
              <el-icon><Upload /></el-icon>
              上传文件
            </el-button>
            <el-button type="success" @click="openUploadDialog('folder')">
              <el-icon><FolderAdd /></el-icon>
              上传文件夹
            </el-button>
            <el-button type="warning" @click="openImportDialog">
              <el-icon><FolderOpened /></el-icon>
              导入本地文件
            </el-button>
            <el-button @click="openRsyncDialog">
              <el-icon><Connection /></el-icon>
              rsync 传输
            </el-button>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="fileList"
        style="width: 100%"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="fileName" label="文件名" min-width="200" />
        <el-table-column prop="projectName" label="所属项目" width="150" />
        <el-table-column prop="fileSize" label="文件大小" width="120">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="fileType" label="文件类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.fileType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="uploader" label="上传者" width="100" />
        <el-table-column prop="createTime" label="上传时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleDownload(row)">下载</el-button>
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
          @size-change="loadFiles"
          @current-change="loadFiles"
        />
      </div>
    </el-card>

    <!-- Upload Dialog -->
    <el-dialog
      v-model="uploadDialogVisible"
      :title="uploadMode === 'file' ? '上传文件' : '上传文件夹'"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form :model="uploadForm" label-width="100px">
        <el-form-item label="选择项目">
          <el-select v-model="uploadForm.projectId" placeholder="请选择项目">
            <el-option
              v-for="project in projectList"
              :key="project.id"
              :label="project.name"
              :value="project.id"
            />
          </el-select>
        </el-form-item>

        <!-- 单文件上传 -->
        <el-form-item v-if="uploadMode === 'file'" label="选择文件">
          <el-upload
            ref="uploadRef"
            drag
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-exceed="handleExceed"
          >
            <el-icon class="el-icon--upload"><Upload /></el-icon>
            <div class="el-upload__text">
              将文件拖到此处，或<em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                支持 FASTQ, BAM, VCF, BED 等格式文件
              </div>
            </template>
          </el-upload>
        </el-form-item>

        <!-- 文件夹上传 -->
        <el-form-item v-if="uploadMode === 'folder'" label="选择文件夹">
          <div class="folder-upload-area">
            <input
              ref="folderInputRef"
              type="file"
              webkitdirectory
              multiple
              style="display: none"
              @change="handleFolderChange"
            />
            <div v-if="!folderFiles.length" class="folder-dropzone" @click="triggerFolderInput">
              <el-icon :size="48" color="#c0c4cc"><FolderAdd /></el-icon>
              <p>点击选择文件夹</p>
              <p class="folder-tip">将保留完整的目录结构</p>
            </div>
            <div v-else class="folder-preview">
              <div class="folder-header">
                <span class="folder-name">{{ folderName }}</span>
                <span class="folder-count">{{ folderFiles.length }} 个文件，{{ formatFileSize(totalFolderSize) }}</span>
                <el-button type="danger" link @click="clearFolder">清除</el-button>
              </div>
              <el-tree
                :data="folderTree"
                :props="{ label: 'name', children: 'children' }"
                default-expand-all
                class="folder-tree"
              >
                <template #default="{ node, data }">
                  <span class="tree-node">
                    <el-icon v-if="data.isDirectory" :size="14" color="#e6a23c"><Folder /></el-icon>
                    <el-icon v-else :size="14" color="#409eff"><Document /></el-icon>
                    <span>{{ node.label }}</span>
                    <span v-if="!data.isDirectory" class="tree-size">{{ formatFileSize(data.size) }}</span>
                  </span>
                </template>
              </el-tree>
            </div>
          </div>
        </el-form-item>

        <!-- 上传进度 -->
        <el-form-item v-if="uploadProgress.total > 0" label="上传进度">
          <div class="upload-progress">
            <el-progress
              :percentage="uploadProgress.percent"
              :status="uploadProgress.status"
              :stroke-width="10"
            />
            <p class="progress-text">
              <template v-if="uploadProgress.total > 1">
                {{ uploadProgress.current }} / {{ uploadProgress.total }} 个文件
              </template>
              <span v-if="uploadProgress.currentFile" class="progress-file">{{ uploadProgress.currentFile }}</span>
            </p>
          </div>
        </el-form-item>

        <!-- 存储空间信息 -->
        <el-form-item v-if="storageInfo" label="存储空间">
          <div class="storage-info">
            <div class="storage-row">
              <span class="storage-label">个人配额：</span>
              <el-progress
                :percentage="Math.round((storageInfo.userUsed / storageInfo.userQuota) * 100)"
                :color="storageInfo.canUpload ? '#67c23a' : '#f56c6c'"
                :stroke-width="8"
                style="flex: 1"
              />
              <span class="storage-value">{{ formatFileSize(storageInfo.userUsed) }} / {{ formatFileSize(storageInfo.userQuota) }}</span>
            </div>
            <div class="storage-row">
              <span class="storage-label">磁盘空间：</span>
              <el-progress
                :percentage="Math.round((storageInfo.diskUsed / storageInfo.diskTotal) * 100)"
                :color="storageInfo.diskFree > 1073741824 ? '#67c23a' : '#f56c6c'"
                :stroke-width="8"
                style="flex: 1"
              />
              <span class="storage-value">剩余 {{ formatFileSize(storageInfo.diskFree) }}</span>
            </div>
            <el-alert
              v-if="!storageInfo.canUpload"
              :title="storageInfo.reason || '无法上传'"
              type="error"
              show-icon
              :closable="false"
              style="margin-top: 8px"
            />
            <p v-if="pendingFileSize > 0" class="pending-size">
              待上传：{{ formatFileSize(pendingFileSize) }}
              <span v-if="storageInfo.canUpload" style="color: #67c23a">✓ 空间充足</span>
              <span v-else style="color: #f56c6c">✗ 空间不足</span>
            </p>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="uploadLoading"
          :disabled="(uploadMode === 'folder' && !folderFiles.length) || (storageInfo && !storageInfo.canUpload)"
          @click="handleUpload"
        >
          {{ uploadMode === 'file' ? '上传' : '开始上传' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 导入本地文件对话框 -->
    <el-dialog
      v-model="importDialogVisible"
      title="导入服务器本地文件"
      width="550px"
      :close-on-click-modal="false"
    >
      <el-alert
        type="info"
        :closable="false"
        style="margin-bottom: 16px"
      >
        <template #title>
          将服务器磁盘上已有的文件登记到数据库（不复制文件，仅记录元数据）。
          适合先用 rsync 传输大文件到服务器，再通过此功能导入。
        </template>
      </el-alert>

      <el-form :model="importForm" label-width="100px">
        <el-form-item label="选择项目" required>
          <el-select v-model="importForm.projectId" placeholder="请选择项目" style="width: 100%">
            <el-option
              v-for="project in projectList"
              :key="project.id"
              :label="project.name"
              :value="project.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="目录路径" required>
          <el-input
            v-model="importForm.dirPath"
            placeholder="服务器上的绝对路径，如 /home/luosg/uploads/bioplatform/1"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" @click="handleImport">
          开始导入
        </el-button>
      </template>
    </el-dialog>

    <!-- rsync 传输指引对话框 -->
    <el-dialog
      v-model="rsyncDialogVisible"
      title="rsync 传输指引"
      width="650px"
    >
      <el-alert
        type="info"
        :closable="false"
        style="margin-bottom: 16px"
      >
        <template #title>
          使用 rsync 将本地大文件/文件夹传输到服务器，速度远快于 Web 上传。
          传输完成后，在"数据管理"页面点击"导入本地文件"登记到数据库。
        </template>
      </el-alert>

      <div v-if="rsyncInfo" class="rsync-content">
        <div class="rsync-section">
          <h4>传输文件夹</h4>
          <div class="code-block">
            <code>rsync -avz --progress ./your_data/ user@{{ rsyncInfo.host }}:{{ rsyncInfo.uploadPath }}/{projectId}/</code>
            <el-button type="primary" link @click="copyToClipboard(rsyncInfo.example.split('\n')[0])">
              <el-icon><CopyDocument /></el-icon>
            </el-button>
          </div>
        </div>

        <div class="rsync-section">
          <h4>传输单文件</h4>
          <div class="code-block">
            <code>rsync -avz --progress ./your_file.bam user@{{ rsyncInfo.host }}:{{ rsyncInfo.uploadPath }}/{projectId}/</code>
            <el-button type="primary" link @click="copyToClipboard(rsyncInfo.example.split('\n')[1] || '')">
              <el-icon><CopyDocument /></el-icon>
            </el-button>
          </div>
        </div>

        <div class="rsync-section">
          <h4>常用参数说明</h4>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="-a">归档模式，保留权限、时间戳等</el-descriptions-item>
            <el-descriptions-item label="-v">显示传输详情</el-descriptions-item>
            <el-descriptions-item label="-z">压缩传输，节省带宽</el-descriptions-item>
            <el-descriptions-item label="--progress">显示进度条</el-descriptions-item>
            <el-descriptions-item label="--partial">断点续传（大文件推荐）</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="rsync-section">
          <h4>传输完成后</h4>
          <p>回到本页面，点击 <el-tag type="warning" size="small">导入本地文件</el-tag>，填写上面使用的 {projectId} 对应的目录路径即可。</p>
        </div>
      </div>

      <template #footer>
        <el-button @click="rsyncDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile, UploadInstance } from 'element-plus'
import { Upload, FolderAdd, Folder, Document, FolderOpened, Connection, CopyDocument } from '@element-plus/icons-vue'
import { listFiles, uploadFile, batchUploadFiles, deleteFile, downloadFile, storageCheck, getRsyncInfo, importLocalFiles } from '@/api/dataFileApi'
import type { DataFile, StorageInfo, RsyncInfo } from '@/api/dataFileApi'
import { listProjects } from '@/api/projectApi'
import type { Project } from '@/api/projectApi'
import { chunkUpload, shouldUseChunkUpload } from '@/utils/chunkUpload'

const loading = ref(false)
const uploadLoading = ref(false)
const fileList = ref<DataFile[]>([])
const projectList = ref<Project[]>([])
const uploadDialogVisible = ref(false)
const uploadRef = ref<UploadInstance>()
const folderInputRef = ref<HTMLInputElement>()
const selectedFile = ref<File | null>(null)
const uploadMode = ref<'file' | 'folder'>('file')
const storageInfo = ref<StorageInfo | null>(null)
const importDialogVisible = ref(false)
const importLoading = ref(false)
const rsyncDialogVisible = ref(false)
const rsyncInfo = ref<RsyncInfo | null>(null)

const importForm = reactive({
  projectId: null as number | null,
  dirPath: ''
})

// 文件夹上传相关
const folderFiles = ref<File[]>([])

const searchForm = reactive({
  projectId: null as number | null,
  fileName: ''
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const uploadForm = reactive({
  projectId: null as number | null
})

const uploadProgress = reactive({
  current: 0,
  total: 0,
  percent: 0,
  currentFile: '',
  status: '' as '' | 'success' | 'exception' | 'warning'
})

// 文件夹名称
const folderName = computed(() => {
  if (!folderFiles.value.length) return ''
  const firstPath = folderFiles.value[0].webkitRelativePath || ''
  return firstPath.split('/')[0] || '未知文件夹'
})

// 文件夹总大小
const totalFolderSize = computed(() => {
  return folderFiles.value.reduce((sum, f) => sum + f.size, 0)
})

// 待上传文件大小
const pendingFileSize = computed(() => {
  if (uploadMode.value === 'file') return selectedFile.value?.size || 0
  return totalFolderSize.value
})

// 构建文件夹树结构
interface TreeNode {
  name: string
  isDirectory: boolean
  size?: number
  children?: TreeNode[]
}

const folderTree = computed<TreeNode[]>(() => {
  if (!folderFiles.value.length) return []
  const root: TreeNode[] = []
  const dirMap = new Map<string, TreeNode>()

  for (const file of folderFiles.value) {
    const relativePath = file.webkitRelativePath || file.name
    const parts = relativePath.split('/')
    // 跳过根文件夹名，从子级开始
    let currentLevel = root
    let currentPath = ''

    for (let i = 1; i < parts.length; i++) {
      const part = parts[i]
      currentPath = currentPath ? currentPath + '/' + part : part
      const isLast = i === parts.length - 1

      if (isLast) {
        // 文件
        currentLevel.push({
          name: part,
          isDirectory: false,
          size: file.size
        })
      } else {
        // 目录
        let dir = dirMap.get(currentPath)
        if (!dir) {
          dir = { name: part, isDirectory: true, children: [] }
          dirMap.set(currentPath, dir)
          currentLevel.push(dir)
        }
        currentLevel = dir.children!
      }
    }
  }
  return root
})

const formatFileSize = (bytes: number) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const loadFiles = async () => {
  loading.value = true
  try {
    const res = await listFiles({
      page: pagination.page,
      size: pagination.size,
      ...searchForm
    })
    fileList.value = res.records
    pagination.total = res.total
  } catch (error) {
    console.error('Failed to load files:', error)
  } finally {
    loading.value = false
  }
}

const loadProjects = async () => {
  try {
    const res = await listProjects({ page: 1, size: 100 })
    projectList.value = res.records
  } catch (error) {
    console.error('Failed to load projects:', error)
  }
}

const handleSearch = () => {
  pagination.page = 1
  loadFiles()
}

const resetSearch = () => {
  searchForm.projectId = null
  searchForm.fileName = ''
  handleSearch()
}

// 打开上传对话框
const openUploadDialog = async (mode: 'file' | 'folder') => {
  uploadMode.value = mode
  selectedFile.value = null
  folderFiles.value = []
  uploadProgress.total = 0
  uploadProgress.current = 0
  uploadProgress.percent = 0
  uploadProgress.currentFile = ''
  uploadProgress.status = ''
  storageInfo.value = null

  // 打开时先检查存储空间（不带文件大小）
  await checkStorageSpace(0)
  uploadDialogVisible.value = true
}

// 检查存储空间
const checkStorageSpace = async (pendingSize: number) => {
  try {
    const res = await storageCheck(pendingSize)
    storageInfo.value = res as any
  } catch (error) {
    console.error('Storage check failed:', error)
  }
}

// 单文件上传
const handleFileChange = (file: UploadFile) => {
  selectedFile.value = file.raw || null
  if (selectedFile.value) {
    checkStorageSpace(selectedFile.value.size)
  }
}

const handleExceed = () => {
  ElMessage.warning('只能上传一个文件')
}

// 文件夹上传
const triggerFolderInput = () => {
  folderInputRef.value?.click()
}

const handleFolderChange = (e: Event) => {
  const input = e.target as HTMLInputElement
  if (input.files) {
    folderFiles.value = Array.from(input.files)
    // 检查存储空间
    const totalSize = folderFiles.value.reduce((sum, f) => sum + f.size, 0)
    checkStorageSpace(totalSize)
  }
}

const clearFolder = () => {
  folderFiles.value = []
  if (folderInputRef.value) {
    folderInputRef.value.value = ''
  }
}

// 上传处理
const handleUpload = async () => {
  if (!uploadForm.projectId) {
    ElMessage.warning('请选择项目')
    return
  }

  if (uploadMode.value === 'file') {
    await handleSingleUpload()
  } else {
    await handleFolderUpload()
  }
}

const handleSingleUpload = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请选择文件')
    return
  }

  uploadLoading.value = true
  try {
    if (shouldUseChunkUpload(selectedFile.value)) {
      // 大文件：分片上传
      await chunkUpload({
        file: selectedFile.value,
        projectId: uploadForm.projectId!,
        onProgress: (progress) => {
          uploadProgress.total = 1
          uploadProgress.current = progress.completedChunks
          uploadProgress.percent = progress.percent
          uploadProgress.currentFile = selectedFile.value!.name
          uploadProgress.status = progress.status === 'done' ? 'success' : ''
        }
      })
    } else {
      // 小文件：直接上传
      await uploadFile(selectedFile.value, uploadForm.projectId!)
    }
    ElMessage.success('上传成功')
    uploadDialogVisible.value = false
    loadFiles()
  } catch (error) {
    ElMessage.error('上传失败')
  } finally {
    uploadLoading.value = false
  }
}

const handleFolderUpload = async () => {
  if (!folderFiles.value.length) {
    ElMessage.warning('请选择文件夹')
    return
  }

  uploadLoading.value = true
  uploadProgress.total = folderFiles.value.length
  uploadProgress.current = 0
  uploadProgress.percent = 0
  uploadProgress.status = ''

  const files = folderFiles.value
  const projectId = uploadForm.projectId!
  let uploaded = 0
  let hasError = false

  for (const file of files) {
    uploadProgress.currentFile = file.webkitRelativePath || file.name

    try {
      if (shouldUseChunkUpload(file)) {
        // 大文件：分片上传
        await chunkUpload({
          file,
          projectId,
          onProgress: (progress) => {
            uploadProgress.percent = Math.round(((uploaded + progress.percent / 100) / files.length) * 100)
          }
        })
      } else {
        // 小文件：直接上传
        await uploadFile(file, projectId)
      }
      uploaded++
      uploadProgress.current = uploaded
      uploadProgress.percent = Math.round((uploaded / files.length) * 100)
    } catch (error) {
      hasError = true
      console.error('File upload failed:', file.name, error)
    }
  }

  uploadProgress.currentFile = ''
  if (hasError) {
    uploadProgress.status = 'exception'
    ElMessage.warning(`部分文件上传失败，成功 ${uploaded}/${files.length} 个`)
  } else {
    uploadProgress.status = 'success'
    ElMessage.success(`文件夹上传成功，共 ${files.length} 个文件`)
    uploadDialogVisible.value = false
    loadFiles()
  }
  uploadLoading.value = false
}

const handleDownload = async (row: DataFile) => {
  try {
    const res = await downloadFile(row.id)
    const blob = new Blob([res as any])
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = row.fileName
    link.click()
    window.URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error('下载失败')
  }
}

const handleDelete = async (row: DataFile) => {
  try {
    await ElMessageBox.confirm(`确定要删除文件"${row.fileName}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteFile(row.id)
    ElMessage.success('删除成功')
    loadFiles()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 导入本地文件
const openImportDialog = () => {
  importForm.projectId = null
  importForm.dirPath = ''
  importDialogVisible.value = true
}

const handleImport = async () => {
  if (!importForm.projectId) {
    ElMessage.warning('请选择项目')
    return
  }
  if (!importForm.dirPath.trim()) {
    ElMessage.warning('请输入目录路径')
    return
  }

  importLoading.value = true
  try {
    const res = await importLocalFiles(importForm.dirPath.trim(), importForm.projectId)
    const data = res as any
    ElMessage.success(`导入成功，共 ${data.count} 个文件`)
    importDialogVisible.value = false
    loadFiles()
  } catch (error: any) {
    ElMessage.error(error?.message || '导入失败')
  } finally {
    importLoading.value = false
  }
}

// rsync 传输指引
const openRsyncDialog = async () => {
  rsyncDialogVisible.value = true
  if (!rsyncInfo.value) {
    try {
      const res = await getRsyncInfo()
      rsyncInfo.value = res as any
    } catch (error) {
      console.error('Failed to load rsync info:', error)
    }
  }
}

const copyToClipboard = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

onMounted(() => {
  loadFiles()
  loadProjects()
})
</script>

<style scoped>
.data-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* 文件夹上传 */
.folder-upload-area {
  width: 100%;
}

.folder-dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  border: 2px dashed #dcdfe6;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.folder-dropzone:hover {
  border-color: #409eff;
  background: #f5f7fa;
}

.folder-dropzone p {
  margin: 8px 0 0;
  color: #606266;
  font-size: 14px;
}

.folder-tip {
  color: #909399 !important;
  font-size: 12px !important;
}

.folder-preview {
  border: 1px solid #e4e7ed;
  border-radius: 12px;
  overflow: hidden;
  width: 100%;
}

.folder-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
}

.folder-name {
  font-weight: 600;
  color: #303133;
}

.folder-count {
  color: #909399;
  font-size: 13px;
  flex: 1;
}

.folder-tree {
  padding: 8px;
  max-height: 300px;
  overflow-y: auto;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.tree-size {
  color: #909399;
  font-size: 12px;
  margin-left: 8px;
}

/* 上传进度 */
.upload-progress {
  width: 100%;
}

.progress-text {
  margin: 4px 0 0;
  font-size: 12px;
  color: #909399;
}

.progress-file {
  color: #606266;
  margin-left: 8px;
}

.el-upload__tip {
  color: #909399;
  font-size: 12px;
}

/* 存储空间信息 */
.storage-info {
  width: 100%;
}

.storage-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.storage-label {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
  min-width: 70px;
}

.storage-value {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  min-width: 120px;
  text-align: right;
}

.pending-size {
  margin: 8px 0 0;
  font-size: 13px;
  color: #606266;
}

/* rsync 对话框 */
.rsync-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.rsync-section h4 {
  margin: 0 0 8px;
  font-size: 14px;
  color: #303133;
}

.rsync-section p {
  margin: 0;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}

.code-block {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: #f5f7fa;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
}

.code-block code {
  flex: 1;
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #303133;
  word-break: break-all;
}
</style>
