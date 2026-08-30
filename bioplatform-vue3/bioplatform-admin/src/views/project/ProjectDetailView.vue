<template>
  <div class="project-detail">
    <!-- Project Info Card -->
    <el-card class="info-card">
      <div class="info-header">
        <div>
          <h2>{{ project.name }}</h2>
          <p class="desc">{{ project.description || '暂无描述' }}</p>
        </div>
        <el-button @click="router.push('/projects')">返回列表</el-button>
      </div>
      <el-descriptions :column="4" border size="small" style="margin-top: 12px">
        <el-descriptions-item label="物种">{{ project.organism || '-' }}</el-descriptions-item>
        <el-descriptions-item label="基因组版本">{{ project.genomeVersion || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="project.status === 1 ? 'success' : 'info'" size="small">
            {{ project.status === 1 ? '活跃' : project.status === 2 ? '归档' : '草稿' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="可见性">
          <el-tag :type="project.isPrivate ? 'warning' : 'success'" size="small">
            {{ project.isPrivate ? '私有' : '公开' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- Data Files -->
    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <span>数据文件</span>
          <div class="header-actions">
            <el-button size="small" @click="showImportDialog">
              <el-icon><FolderOpened /></el-icon>
              导入服务器文件
            </el-button>
            <el-upload
              :show-file-list="false"
              :before-upload="handleUpload"
              accept=".tsv,.csv,.txt,.fq.gz,.fastq.gz,.bam,.sam,.vcf,.bed,.gff,.gtf,.fa,.fasta"
            >
              <el-button type="primary" size="small">
                <el-icon><Upload /></el-icon>
                上传文件
              </el-button>
            </el-upload>
          </div>
        </div>
      </template>

      <el-table v-loading="fileLoading" :data="fileList" style="width: 100%" size="small">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="fileType" label="类型" width="80">
          <template #default="{ row }">
            <el-tag size="small">{{ row.fileType || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="100">
          <template #default="{ row }">
            {{ formatSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="organism" label="物种" width="100" />
        <el-table-column prop="createdAt" label="上传时间" width="170" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" link size="small" @click="handleDeleteFile(row as DataFile)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="filePagination.page"
          v-model:page-size="filePagination.size"
          :page-sizes="[10, 20, 50]"
          :total="filePagination.total"
          layout="total, sizes, prev, pager, next"
          @size-change="loadFiles"
          @current-change="loadFiles"
        />
      </div>
    </el-card>

    <!-- 样本信息 -->
    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <span>样本信息</span>
          <div class="header-actions">
            <el-button size="small" @click="handleImportTsv">
              <el-icon><Upload /></el-icon>
              导入文本
            </el-button>
            <el-button type="primary" size="small" @click="handleCreateMeta">
              <el-icon><Plus /></el-icon>
              新建
            </el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="metaLoading" :data="metaList" style="width: 100%" size="small">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="名称" min-width="200" />
        <el-table-column prop="metaMode" label="模式" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.metaMode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="样本数" width="80">
          <template #default="{ row }">
            {{ countSamples(row.metaContent) }}
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEditMeta(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDeleteMeta(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Meta 编辑对话框 -->
    <el-dialog
      v-model="metaDialogVisible"
      :title="metaDialogTitle"
      width="900px"
      top="5vh"
      destroy-on-close
    >
      <div style="margin-bottom: 12px; display: flex; gap: 12px; align-items: center">
        <el-input v-model="metaForm.name" placeholder="Meta名称，如 RNA-seq WT vs KO" style="flex: 1" />
        <el-select v-model="metaForm.metaMode" placeholder="模式" style="width: 140px" @change="handleModeChange">
          <el-option label="FASTQ" value="fastq" />
          <el-option label="PacBio" value="pacbio" />
          <el-option label="MS" value="ms" />
          <el-option label="scRNA-seq" value="scrnaseq" />
        </el-select>
        <el-input v-model="metaForm.description" placeholder="描述（可选）" style="width: 200px" />
      </div>

      <el-tabs v-model="metaEditTab">
        <!-- 表格编辑模式 -->
        <el-tab-pane label="表格编辑" name="table">
          <div style="overflow-x: auto; max-height: 400px; overflow-y: auto">
            <table class="meta-table">
              <thead>
                <tr>
                  <th style="width: 40px">#</th>
                  <th v-for="(col, ci) in metaColumns" :key="ci">
                    <div style="display: flex; align-items: center; gap: 4px">
                      <span>{{ col }}</span>
                      <el-button link size="small" type="danger" @click="removeColumn(ci)" v-if="!isFixedColumn(col)">
                        <el-icon><Close /></el-icon>
                      </el-button>
                    </div>
                  </th>
                  <th style="width: 40px">
                    <el-button link size="small" @click="addColumn">
                      <el-icon><Plus /></el-icon>
                    </el-button>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, ri) in metaRows" :key="ri">
                  <td style="text-align: center; color: #909399">{{ ri + 1 }}</td>
                  <td v-for="(col, ci) in metaColumns" :key="ci">
                    <input
                      v-model="metaRows[ri][ci]"
                      class="meta-cell"
                      :placeholder="col"
                    />
                  </td>
                  <td style="text-align: center">
                    <el-button link size="small" type="danger" @click="removeRow(ri)">
                      <el-icon><Close /></el-icon>
                    </el-button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div style="margin-top: 8px">
            <el-button size="small" @click="addRow">+ 添加行</el-button>
          </div>
        </el-tab-pane>

        <!-- 文本编辑模式 -->
        <el-tab-pane label="文本编辑" name="tsv">
          <el-input
            v-model="metaTsvContent"
            type="textarea"
            :rows="15"
            placeholder="粘贴表格内容，支持制表符、逗号、分号、竖线等常见分隔符..."
            style="font-family: monospace"
          />
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="metaDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="metaSaving" @click="handleSaveMeta">保存</el-button>
      </template>
    </el-dialog>

    <!-- 导入文本对话框 -->
    <el-dialog v-model="importTsvDialogVisible" title="导入文本" width="600px">
      <el-input
        v-model="importTsvContent"
        type="textarea"
        :rows="12"
        placeholder="粘贴表格内容，支持制表符、逗号、分号、竖线等常见分隔符..."
        style="font-family: monospace"
      />
      <div style="margin-top: 8px">
        <el-input v-model="importTsvName" placeholder="Meta名称" />
      </div>
      <template #footer>
        <el-button @click="importTsvDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleConfirmImportTsv">导入</el-button>
      </template>
    </el-dialog>

    <!-- Analyses List -->
    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <span>分析列表</span>
          <el-button type="primary" @click="showCreateDialog">
            <el-icon><Plus /></el-icon>
            新建分析
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="analysesList" style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="分析名称" min-width="150" />
        <el-table-column prop="type" label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.type === 'pipeline' ? 'primary' : 'success'" size="small">
              {{ row.type }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="120" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
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
          @size-change="loadAnalyses"
          @current-change="loadAnalyses"
        />
      </div>
    </el-card>

    <!-- 分析结果文件浏览 -->
    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <span>分析结果</span>
          <div class="header-actions">
            <el-button size="small" :loading="pptLoading" @click="handleExportPpt">
              <el-icon><Document /></el-icon>
              下载PPT
            </el-button>
            <el-button size="small" :loading="excelLoading" @click="handleExportExcel">
              <el-icon><Document /></el-icon>
              下载Excel
            </el-button>
            <el-button size="small" type="warning" :loading="downloadAllLoading" @click="handleDownloadAll">
              <el-icon><Download /></el-icon>
              全部下载(zip)
            </el-button>
          </div>
        </div>
      </template>

      <el-empty v-if="!treeLoading && fileTreeFlat.length === 0" description="暂无分析结果，请先执行流程" />

      <el-table
        v-else
        v-loading="treeLoading"
        :data="fileTreeFlat"
        style="width: 100%"
        size="small"
        row-key="rowKey"
        @selection-change="handleTreeSelectionChange"
      >
        <el-table-column type="selection" width="40" />
        <el-table-column label="文件名" min-width="300">
          <template #default="{ row }">
            <span v-if="row._isExecDir" style="font-weight: bold; color: #409eff">
              <el-icon><FolderOpened /></el-icon> {{ row.name }}
              <el-tag v-if="row.pipelineName" size="small" type="info" style="margin-left: 8px">{{ row.pipelineName }}</el-tag>
            </span>
            <span v-else-if="row.directory" :style="{ paddingLeft: '16px', fontWeight: 'bold' }">
              <el-icon><Folder /></el-icon> {{ row.name }}/
            </span>
            <span v-else :style="{ paddingLeft: '32px' }">
              <el-icon><Document /></el-icon> {{ row.name }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag v-if="!row.directory && row.fileType" size="small">{{ row.fileType }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="100">
          <template #default="{ row }">
            {{ row.directory ? '-' : formatSize(row.size) }}
          </template>
        </el-table-column>
        <el-table-column prop="modifiedAt" label="时间" width="170" />
      </el-table>

      <div v-if="selectedFiles.length > 0" style="margin-top: 12px; text-align: right">
        <el-button type="primary" size="small" :loading="batchDownloadLoading" @click="handleBatchDownload">
          下载选中文件 ({{ selectedFiles.length }})
        </el-button>
      </div>
    </el-card>

    <!-- Import Local Files Dialog -->
    <el-dialog v-model="importDialogVisible" title="导入服务器文件" width="500px">
      <el-form label-width="100px">
        <el-form-item label="服务器路径">
          <el-input v-model="importDirPath" placeholder="/home/luosg/Data/samples/" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" @click="handleImport">导入</el-button>
      </template>
    </el-dialog>

    <!-- Create Analysis Dialog -->
    <el-dialog v-model="createDialogVisible" title="新建分析" width="700px">
      <el-steps :active="createStep" finish-status="success" align-center style="margin-bottom: 24px">
        <el-step title="选择流程" />
        <el-step title="输入数据" />
        <el-step title="参数覆盖" />
      </el-steps>

      <!-- Step 0: Select Workflow Template -->
      <div v-if="createStep === 0">
        <div v-loading="templateLoading" class="template-grid">
          <el-empty v-if="!templateList.length" description="暂无模板，请先在系统管理中导入" />
          <el-radio-group v-model="selectedTemplateName" class="template-radio-group">
            <el-radio-button
              v-for="tpl in templateList"
              :key="tpl.id"
              :value="tpl.name"
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
          <el-button @click="createDialogVisible = false">取消</el-button>
          <el-button type="primary" :disabled="!selectedTemplateName" @click="createStep = 1">下一步</el-button>
        </div>
      </div>

      <!-- Step 1: Input Meta -->
      <div v-if="createStep === 1">
        <el-form label-width="100px">
          <el-form-item label="分析名称">
            <el-input v-model="analysisName" :placeholder="selectedTemplateName + '-分析'" />
          </el-form-item>
          <el-form-item label="数据来源">
            <el-radio-group v-model="metaType">
              <el-radio value="text">TSV 文本</el-radio>
              <el-radio value="path">服务器路径</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="metaType === 'text'" label="Meta TSV">
            <el-input
              v-model="metaContent"
              type="textarea"
              :rows="8"
              placeholder="sample_id&#9;data_id&#9;design&#9;fastq_1&#9;fastq_2&#9;Sample1&#9;Sample1&#9;&#9;/data/S1_R1.fq.gz&#9;/data/S1_R2.fq.gz"
              style="font-family: monospace"
            />
          </el-form-item>
          <el-form-item v-else label="文件路径">
            <el-input v-model="metaContent" placeholder="/data/samples/meta_input.tsv" />
          </el-form-item>
        </el-form>
        <div style="text-align: right; margin-top: 16px">
          <el-button @click="createStep = 0">上一步</el-button>
          <el-button type="primary" :disabled="!metaContent.trim()" @click="createStep = 2">下一步</el-button>
        </div>
      </div>

      <!-- Step 2: Extra Params Override (optional) -->
      <div v-if="createStep === 2">
        <el-form label-width="100px">
          <el-form-item label="描述">
            <el-input v-model="analysisDescription" type="textarea" :rows="2" placeholder="可选" />
          </el-form-item>
          <el-form-item label="参数覆盖">
            <el-input
              v-model="extraParamsText"
              type="textarea"
              :rows="10"
              placeholder='可选，JSON 格式，如：&#10;{&#10;  "Params.cutadapt.quality": 30,&#10;  "genome.default": "GRCh38"&#10;}'
              style="font-family: monospace"
            />
            <div v-if="extraParamsError" class="json-error">{{ extraParamsError }}</div>
          </el-form-item>
        </el-form>
        <div style="text-align: right; margin-top: 16px">
          <el-button @click="createStep = 1">上一步</el-button>
          <el-button type="primary" :loading="submitLoading" @click="handleCreateAnalysis">创建</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/theme-chalk/el-message-box.css'
import 'element-plus/theme-chalk/el-message.css'
import { Plus, Upload, FolderOpened, Document, Download, Folder, Close } from '@element-plus/icons-vue'
import { getProject, createAnalysis, listAnalyses, exportExcel, exportPpt, getFileTree, batchDownload, downloadAll } from '@/api/projectApi'
import type { Project, Pipeline, FileTreeNode } from '@/api/projectApi'
import { listSampleMeta, createSampleMeta, updateSampleMeta, deleteSampleMeta } from '@/api/sampleMetaApi'
import type { SampleMeta } from '@/api/sampleMetaApi'
import { listTemplates } from '@/api/templateApi'
import type { WorkflowTemplate } from '@/api/templateApi'
import { listFiles, uploadFile, deleteFile, importLocalFiles } from '@/api/dataFileApi'
import type { DataFile } from '@/api/dataFileApi'

const route = useRoute()
const router = useRouter()
const projectId = Number(route.params.id)

const loading = ref(false)
const submitLoading = ref(false)
const templateLoading = ref(false)
const fileLoading = ref(false)
const importLoading = ref(false)
const createDialogVisible = ref(false)
const importDialogVisible = ref(false)
const createStep = ref(0)

// 文件浏览器状态
const treeLoading = ref(false)
const pptLoading = ref(false)
const excelLoading = ref(false)
const downloadAllLoading = ref(false)
const batchDownloadLoading = ref(false)
const fileTreeFlat = ref<any[]>([])
const selectedFiles = ref<any[]>([])

// 样本信息状态
const metaLoading = ref(false)
const metaList = ref<SampleMeta[]>([])
const metaDialogVisible = ref(false)
const metaDialogTitle = ref('新建 Meta')
const metaSaving = ref(false)
const metaEditTab = ref('table')
const metaForm = reactive({ id: 0, projectId, name: '', metaMode: 'fastq', metaContent: '', description: '' })
const metaColumns = ref<string[]>([])
const metaRows = ref<string[][]>([])
const metaTsvContent = ref('')
const importTsvDialogVisible = ref(false)
const importTsvContent = ref('')
const importTsvName = ref('')

const META_MODE_COLUMNS: Record<string, string[]> = {
  fastq: ['sample_id', 'design', 'fastq_1', 'fastq_2', 'group', 'organism'],
  pacbio: ['sample_id', 'design', 'bam', 'pbi', 'group', 'organism'],
  ms: ['sample_id', 'ms_file', 'organism'],
  scrnaseq: ['sample_id', 'fastq_dir', 'sample_prefix', 'design', 'group', 'organism'],
}

const project = reactive<Project>({
  id: 0, name: '', description: '', organism: '', genomeVersion: '',
  ownerId: 0, status: 1, isPrivate: false, createdAt: '', updatedAt: ''
})

const analysesList = ref<Pipeline[]>([])
const fileList = ref<DataFile[]>([])
const templateList = ref<WorkflowTemplate[]>([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const filePagination = reactive({ page: 1, size: 10, total: 0 })

// Create analysis form
const selectedTemplateName = ref('')
const analysisName = ref('')
const metaType = ref('text')
const metaContent = ref('')
const analysisDescription = ref('')
const extraParamsText = ref('')
const extraParamsError = ref('')

// Import form
const importDirPath = ref('')

// --- Data loading ---
const loadProject = async () => {
  try {
    const res = await getProject(projectId)
    Object.assign(project, res)
  } catch (e) {
    ElMessage.error('加载项目失败')
  }
}

const loadAnalyses = async () => {
  loading.value = true
  try {
    const res = await listAnalyses(projectId, { page: pagination.page, size: pagination.size })
    analysesList.value = res.records
    pagination.total = res.total
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}

const loadFiles = async () => {
  fileLoading.value = true
  try {
    const res = await listFiles({ projectId, page: filePagination.page, size: filePagination.size })
    fileList.value = res.records
    filePagination.total = res.total
  } catch (e) { console.error(e) }
  finally { fileLoading.value = false }
}

const loadTemplates = async () => {
  templateLoading.value = true
  try {
    const res = await listTemplates({ page: 1, size: 50 })
    templateList.value = res.records
  } catch (e) { console.error(e) }
  finally { templateLoading.value = false }
}

// --- File tree（基于流程执行输出目录） ---
const loadFileTree = async () => {
  treeLoading.value = true
  try {
    const res = await getFileTree(projectId)
    // 展平为列表（带层级标识）
    const flat: any[] = []
    let keyIdx = 0
    for (const execNode of res) {
      // 执行记录目录节点
      flat.push({ ...execNode, rowKey: 'exec-' + (execNode.executionId || keyIdx++), _isExecDir: true })
      if (execNode.children) {
        for (const child of execNode.children) {
          flattenNode(child, flat, '  ', keyIdx)
        }
      }
    }
    fileTreeFlat.value = flat
  } catch (e) { console.error(e) }
  finally { treeLoading.value = false }
}

const flattenNode = (node: FileTreeNode, flat: any[], indent: string, keyIdx: number) => {
  if (node.directory && node.children && node.children.length > 0) {
    flat.push({ ...node, rowKey: 'dir-' + node.path + '-' + (keyIdx++), _indent: indent })
    for (const child of node.children) {
      flattenNode(child, flat, indent + '  ', keyIdx)
    }
  } else if (!node.directory) {
    flat.push({ ...node, rowKey: 'file-' + (node.filePath || keyIdx++), _indent: indent })
  }
}

const handleTreeSelectionChange = (selection: any[]) => {
  selectedFiles.value = selection.filter(f => !f.directory && f.filePath)
}

// --- 样本信息 ---
const loadMetaList = async () => {
  metaLoading.value = true
  try {
    metaList.value = await listSampleMeta(projectId) as any || []
  } catch (e) { console.error(e) }
  finally { metaLoading.value = false }
}

const countSamples = (content: string) => {
  if (!content) return 0
  const lines = content.trim().split('\n')
  // 减去表头行
  return Math.max(0, lines.length - 1)
}

const isFixedColumn = (col: string) => {
  return META_MODE_COLUMNS[metaForm.metaMode]?.includes(col) || col === 'sample_id'
}

const handleModeChange = (mode: string) => {
  // 切换模式时，如果表格为空则填充默认列
  if (metaColumns.value.length === 0 || metaColumns.value.length <= 1) {
    metaColumns.value = [...(META_MODE_COLUMNS[mode] || ['sample_id'])]
    metaRows.value = [metaColumns.value.map(() => '')]
  }
}

const detectDelimiter = (line: string): string => {
  const candidates = ['\t', ',', ';', '|']
  let best = '\t'
  let maxCount = 0
  for (const d of candidates) {
    const count = line.split(d).length - 1
    if (count > maxCount) {
      maxCount = count
      best = d
    }
  }
  return best
}

const tsvToTable = (tsv: string) => {
  const lines = tsv.trim().split('\n')
  if (lines.length === 0) {
    metaColumns.value = [...META_MODE_COLUMNS[metaForm.metaMode]]
    metaRows.value = [metaColumns.value.map(() => '')]
    return
  }
  const delimiter = detectDelimiter(lines[0])
  metaColumns.value = lines[0].split(delimiter).map(s => s.trim())
  metaRows.value = []
  for (let i = 1; i < lines.length; i++) {
    const cells = lines[i].split(delimiter).map(s => s.trim())
    // 确保列数一致
    while (cells.length < metaColumns.value.length) cells.push('')
    metaRows.value.push(cells.slice(0, metaColumns.value.length))
  }
  if (metaRows.value.length === 0) {
    metaRows.value = [metaColumns.value.map(() => '')]
  }
}

const tableToTsv = () => {
  const header = metaColumns.value.join('\t')
  const rows = metaRows.value.map(r => r.join('\t')).join('\n')
  return header + '\n' + rows
}

const handleCreateMeta = () => {
  metaDialogTitle.value = '新建 Meta'
  metaForm.id = 0
  metaForm.name = ''
  metaForm.metaMode = 'fastq'
  metaForm.description = ''
  metaColumns.value = [...META_MODE_COLUMNS.fastq]
  metaRows.value = [metaColumns.value.map(() => '')]
  metaTsvContent.value = ''
  metaEditTab.value = 'table'
  metaDialogVisible.value = true
}

const handleEditMeta = (row: SampleMeta) => {
  metaDialogTitle.value = '编辑 Meta'
  metaForm.id = row.id
  metaForm.name = row.name
  metaForm.metaMode = row.metaMode
  metaForm.description = row.description || ''
  metaForm.metaContent = row.metaContent
  metaEditTab.value = 'table'
  tsvToTable(row.metaContent)
  metaDialogVisible.value = true
}

const handleDeleteMeta = async (row: SampleMeta) => {
  try {
    await ElMessageBox.confirm(`确定要删除「${row.name}」吗？`, '提示', { type: 'warning' })
    await deleteSampleMeta(row.id)
    ElMessage.success('删除成功')
    loadMetaList()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

const addRow = () => {
  metaRows.value.push(metaColumns.value.map(() => ''))
}

const removeRow = (index: number) => {
  metaRows.value.splice(index, 1)
}

const addColumn = () => {
  const colName = prompt('请输入列名:')
  if (!colName) return
  metaColumns.value.push(colName)
  metaRows.value.forEach(row => row.push(''))
}

const removeColumn = (index: number) => {
  metaColumns.value.splice(index, 1)
  metaRows.value.forEach(row => row.splice(index, 1))
}

const handleSaveMeta = async () => {
  metaSaving.value = true
  try {
    // 根据当前tab获取content
    let content = ''
    if (metaEditTab.value === 'table') {
      content = tableToTsv()
    } else {
      content = metaTsvContent.value
    }

    const data = {
      id: metaForm.id || undefined,
      projectId,
      name: metaForm.name || 'meta_input',
      metaMode: metaForm.metaMode,
      metaContent: content,
      description: metaForm.description,
    }

    if (metaForm.id) {
      await updateSampleMeta(data as any)
    } else {
      await createSampleMeta(data as any)
    }
    ElMessage.success('保存成功')
    metaDialogVisible.value = false
    loadMetaList()
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    metaSaving.value = false
  }
}

const handleImportTsv = () => {
  importTsvContent.value = ''
  importTsvName.value = ''
  importTsvDialogVisible.value = true
}

const handleConfirmImportTsv = async () => {
  if (!importTsvContent.value.trim()) {
    ElMessage.warning('请输入TSV内容')
    return
  }
  try {
    await createSampleMeta({
      projectId,
      name: importTsvName.value || 'meta_input',
      metaMode: 'fastq',
      metaContent: importTsvContent.value.trim(),
    })
    ElMessage.success('导入成功')
    importTsvDialogVisible.value = false
    loadMetaList()
  } catch (e) {
    ElMessage.error('导入失败')
  }
}

const triggerBlobDownload = (blob: Blob, filename: string) => {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

const handleExportPpt = async () => {
  pptLoading.value = true
  try {
    const blob = await exportPpt(projectId) as any
    triggerBlobDownload(blob, project.name + '_report.pptx')
  } catch (e: any) {
    ElMessage.error(e?.message || '导出PPT失败')
  } finally { pptLoading.value = false }
}

const handleExportExcel = async () => {
  excelLoading.value = true
  try {
    const blob = await exportExcel(projectId) as any
    triggerBlobDownload(blob, project.name + '_report.xlsx')
  } catch (e: any) {
    ElMessage.error(e?.message || '导出Excel失败')
  } finally { excelLoading.value = false }
}

const handleDownloadAll = async () => {
  downloadAllLoading.value = true
  try {
    const blob = await downloadAll(projectId) as any
    triggerBlobDownload(blob, project.name + '_all.zip')
  } catch (e: any) {
    ElMessage.error(e?.message || '下载失败（可能超过200MB限制）')
  } finally { downloadAllLoading.value = false }
}

const handleBatchDownload = async () => {
  const paths = selectedFiles.value.map(f => f.filePath).filter(Boolean)
  if (paths.length === 0) {
    ElMessage.warning('请先选择文件')
    return
  }
  batchDownloadLoading.value = true
  try {
    const blob = await batchDownload(projectId, paths) as any
    triggerBlobDownload(blob, project.name + '_selected.zip')
  } catch (e: any) {
    ElMessage.error(e?.message || '下载失败')
  } finally { batchDownloadLoading.value = false }
}

// --- File upload ---
const handleUpload = async (file: File) => {
  try {
    await uploadFile(file, projectId)
    ElMessage.success('上传成功')
    loadFiles()
  } catch (e) {
    ElMessage.error('上传失败')
  }
  return false // prevent el-upload default behavior
}

const handleDeleteFile = async (row: DataFile) => {
  try {
    await ElMessageBox.confirm(`确定要删除文件"${row.fileName}"吗？`, '提示', { type: 'warning' })
    await deleteFile(row.id)
    ElMessage.success('删除成功')
    loadFiles()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

const showImportDialog = () => {
  importDirPath.value = ''
  importDialogVisible.value = true
}

const handleImport = async () => {
  if (!importDirPath.value.trim()) {
    ElMessage.warning('请输入服务器路径')
    return
  }
  importLoading.value = true
  try {
    const res = await importLocalFiles(importDirPath.value, projectId)
    ElMessage.success(`导入成功，共 ${res.count} 个文件`)
    importDialogVisible.value = false
    loadFiles()
  } catch (e) {
    ElMessage.error('导入失败')
  } finally {
    importLoading.value = false
  }
}

const formatSize = (bytes: number) => {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + ' MB'
  return (bytes / 1024 / 1024 / 1024).toFixed(1) + ' GB'
}

// --- Analysis ---
const showCreateDialog = () => {
  createStep.value = 0
  selectedTemplateName.value = ''
  analysisName.value = ''
  metaType.value = 'text'
  metaContent.value = ''
  analysisDescription.value = ''
  extraParamsText.value = ''
  extraParamsError.value = ''
  createDialogVisible.value = true
  loadTemplates()
}

const handleCreateAnalysis = async () => {
  if (extraParamsText.value.trim()) {
    try {
      JSON.parse(extraParamsText.value)
      extraParamsError.value = ''
    } catch (e: any) {
      extraParamsError.value = 'JSON 格式错误: ' + e.message
      return
    }
  }
  submitLoading.value = true
  try {
    await createAnalysis(projectId, {
      workflowTemplateName: selectedTemplateName.value,
      name: analysisName.value || undefined,
      metaContent: metaContent.value,
      metaType: metaType.value,
      extraParams: extraParamsText.value.trim() || undefined,
      description: analysisDescription.value || undefined
    })
    ElMessage.success('分析创建成功')
    createDialogVisible.value = false
    loadAnalyses()
  } catch (e) {
    ElMessage.error('创建失败')
  } finally {
    submitLoading.value = false
  }
}

const handleEdit = (row: Pipeline) => {
  router.push(`/pipelines`)
}

const handleExecute = (row: Pipeline) => {
  ElMessage.info('执行功能待实现')
}

const handleDelete = async (row: Pipeline) => {
  try {
    await ElMessageBox.confirm(`确定要删除分析"${row.name}"吗？`, '提示', { type: 'warning' })
    const { default: http } = await import('@/utils/http/axios')
    await http.delete(`/api/admin/pipelines/${row.id}`)
    ElMessage.success('删除成功')
    loadAnalyses()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

onMounted(() => {
  loadProject()
  loadFiles()
  loadAnalyses()
  loadFileTree()
  loadMetaList()
})
</script>

<style scoped>
.project-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.info-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.info-header h2 {
  margin: 0 0 4px 0;
  font-size: 20px;
}

.desc {
  color: #909399;
  margin: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.template-grid {
  min-height: 200px;
}

.template-radio-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.template-option {
  height: auto !important;
  padding: 12px !important;
}

.template-option-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
  text-align: left;
  min-width: 120px;
}

.template-desc {
  font-size: 12px;
  color: #909399;
}

.json-error {
  color: #f56c6c;
  font-size: 12px;
  margin-top: 4px;
}

.meta-table {
  border-collapse: collapse;
  width: 100%;
  font-size: 13px;
}

.meta-table th,
.meta-table td {
  border: 1px solid #ebeef5;
  padding: 4px 8px;
  white-space: nowrap;
}

.meta-table th {
  background: #f5f7fa;
  font-weight: 600;
  position: sticky;
  top: 0;
  z-index: 1;
}

.meta-cell {
  border: none;
  outline: none;
  width: 100%;
  font-size: 13px;
  font-family: monospace;
  padding: 2px 0;
  background: transparent;
}

.meta-cell:focus {
  background: #ecf5ff;
}
</style>
