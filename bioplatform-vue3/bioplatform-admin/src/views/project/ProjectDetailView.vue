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
        <el-descriptions-item label="物种">
          <template v-if="project.organism">
            <el-tag v-for="org in project.organism.split(',').filter(Boolean)" :key="org" size="small" type="info" effect="plain" style="margin-right: 4px">{{ org.trim() }}</el-tag>
          </template>
          <span v-else>-</span>
        </el-descriptions-item>
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

    <!-- Sub Projects -->
    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <span>子项目 ({{ childProjects.length }})</span>
          <el-button type="primary" size="small" @click="showAddChildDialog">
            <el-icon><Plus /></el-icon>
            添加子项目
          </el-button>
        </div>
      </template>
      <el-table v-if="childProjects.length > 0" :data="childProjects" size="small" style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="项目名称" min-width="150">
          <template #default="{ row }">
            <el-button type="primary" link @click="router.push(`/projects/${row.id}`)">{{ row.name }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '活跃' : '归档' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="danger" link size="small" @click="removeChild(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无子项目" :image-size="40" />
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
        <el-table-column prop="name" label="文件名" min-width="200" show-overflow-tooltip />
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
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tooltip
              v-if="row.fileStatus === 0"
              :content="'磁盘文件不存在：' + row.path"
              placement="top"
            >
              <el-tag size="small" type="danger">丢失</el-tag>
            </el-tooltip>
            <el-tag v-else size="small" type="success">在库</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上传时间" width="170" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="fileEditRef?.open(row as DataFile)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDeleteFile(row as DataFile)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <FileEditDialog ref="fileEditRef" :projects="allProjects" @saved="loadFiles" />

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
            <el-upload
              :show-file-list="false"
              :before-upload="handleMetaFileUpload"
              accept=".tsv,.csv,.txt"
            >
              <el-button size="small">
                <el-icon><Upload /></el-icon>
                导入文件
              </el-button>
            </el-upload>
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
        <el-table-column label="文件引用" width="110">
          <template #default="{ row }">
            <el-tooltip :content="fileRefTip(row as SampleMeta)" placement="top">
              <el-tag
                size="small"
                :type="fileRefTagType(row as SampleMeta)"
                style="cursor: pointer"
                @click="openFileRefs(row as SampleMeta)"
              >
                {{ fileRefText(row as SampleMeta) }}
              </el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEditMeta(row)">编辑</el-button>
            <el-button type="warning" link size="small" @click="openDesign(row as SampleMeta)">实验设计</el-button>
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
                  <th v-for="(col, ci) in metaColumns" :key="ci" :style="{ width: (metaColWidths[ci] || 180) + 'px' }">
                    <div style="display: flex; align-items: center; gap: 4px">
                      <input
                        v-model="metaColumns[ci]"
                        class="meta-header-input"
                      />
                      <el-button link size="small" type="danger" @click="removeColumn(ci)" v-if="!isFixedColumn(col)">
                        <el-icon><Close /></el-icon>
                      </el-button>
                    </div>
                    <span class="col-resizer" @mousedown.prevent="startColResize(ci, $event)"></span>
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
            placeholder="粘贴表格内容，支持制表符、逗号、分号、竖线、多空格等分隔符..."
            style="font-family: monospace"
          />
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="metaDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="metaSaving" @click="handleSaveMeta">保存</el-button>
      </template>
    </el-dialog>

    <!-- 文件引用明细对话框 -->
    <el-dialog v-model="fileRefsVisible" :title="`文件引用明细：${fileRefsMetaName}`" width="800px" top="6vh">
      <div v-loading="fileRefsLoading">
        <div v-if="fileRefsSummary" style="margin-bottom: 12px; display: flex; align-items: center; gap: 12px">
          <el-tag :type="fileRefsSummary.matchedRefs === fileRefsSummary.totalRefs ? 'success' : 'warning'" size="small">
            已入库 {{ fileRefsSummary.matchedRefs }} / {{ fileRefsSummary.totalRefs }}
          </el-tag>
          <el-button
            v-if="registerableCount > 0"
            type="warning"
            size="small"
            :loading="registerLoading"
            @click="handleRegisterMissing"
          >
            一键登记入库（{{ registerableCount }}）
          </el-button>
        </div>
        <el-table v-if="fileRefs.length" :data="fileRefs" size="small" style="width: 100%">
          <el-table-column prop="rawPath" label="引用路径" min-width="260" show-overflow-tooltip />
          <el-table-column label="状态" width="150">
            <template #default="{ row }">
              <el-tag v-if="row.matched && row.fileStatus === 1" size="small" type="success">已入库 · 在库</el-tag>
              <el-tag v-else-if="row.matched" size="small" type="danger">已入库 · 丢失</el-tag>
              <el-tag v-else-if="row.onDisk" size="small" type="warning">未入库 · 磁盘存在</el-tag>
              <el-tag v-else size="small" type="info">未入库 · 文件缺失</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110">
            <template #default="{ row }">
              <el-button
                v-if="!row.matched && row.onDisk"
                type="primary"
                link
                size="small"
                :loading="registerLoading"
                @click="handleRegisterMissing"
              >登记入库</el-button>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else-if="!fileRefsLoading" description="未解析到文件引用（检查 TSV 中的文件列）" :image-size="60" />
      </div>
      <template #footer>
        <el-button @click="fileRefsVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 导入文件对话框 -->
    <el-dialog v-model="importTsvDialogVisible" title="导入文件" width="600px">
      <div v-if="!importTsvContent" style="text-align: center; padding: 20px">
        <el-upload
          drag
          :show-file-list="false"
          :before-upload="handleMetaFileUpload"
          accept=".tsv,.csv,.txt"
        >
          <el-icon style="font-size: 40px; color: #c0c4cc"><Upload /></el-icon>
          <div style="margin-top: 8px">拖拽文件到此处，或点击上传</div>
          <div style="font-size: 12px; color: #909399; margin-top: 4px">支持 .tsv / .csv / .txt</div>
        </el-upload>
      </div>
      <div v-else>
        <el-input v-model="importTsvName" placeholder="Meta名称" style="margin-bottom: 12px" />
        <div style="font-size: 12px; color: #909399; margin-bottom: 8px">
          已读取文件：<strong>{{ importedFileName }}</strong>（{{ importTsvContent.split('\n').length }} 行）
        </div>
        <el-input
          v-model="importTsvContent"
          type="textarea"
          :rows="8"
          readonly
          style="font-family: monospace"
        />
      </div>
      <template #footer>
        <el-button @click="importTsvDialogVisible = false; resetImportFile()">取消</el-button>
        <el-button v-if="importTsvContent" @click="resetImportFile">重新选择</el-button>
        <el-button type="primary" :disabled="!importTsvContent" @click="handleConfirmImportTsv">导入</el-button>
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
        <el-table-column label="执行版本" width="120">
          <template #default="{ row }">
            <el-tooltip :content="versionTip(row)" placement="top">
              <el-tag v-if="row.latestWorkflowVersion" size="small" :type="row.latestVersionSource === 'git' ? 'success' : 'info'">
                {{ row.latestWorkflowVersion.slice(0, 8) }}{{ row.latestVersionSource === 'git' ? '' : '*' }}
              </el-tag>
              <el-tag v-else-if="row.templateWorkflowVersion" size="small" type="info" effect="plain">
                {{ row.templateWorkflowVersion.slice(0, 8) }}·导入
              </el-tag>
              <span v-else>-</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="执行状态" width="150">
          <template #default="{ row }">
            <template v-if="latestExecutions[(row as Pipeline).id]">
              <el-tag :type="statusTagType(latestExecutions[(row as Pipeline).id].status)" size="small">
                {{ statusLabel(latestExecutions[(row as Pipeline).id].status) }}
              </el-tag>
              <span v-if="latestExecutions[(row as Pipeline).id].status === 'RUNNING'" class="exec-progress">
                {{ latestExecutions[(row as Pipeline).id].progress }}%
              </span>
              <div v-if="latestExecutions[(row as Pipeline).id].progressText" style="font-size: 11px; color: #909399; line-height: 1.3;">
                {{ latestExecutions[(row as Pipeline).id].progressText }}
              </div>
            </template>
            <el-tag v-else type="info" size="small">未执行</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row as Pipeline)">编辑</el-button>
            <el-button type="success" link @click="handleExecute(row as Pipeline)">执行</el-button>
            <el-button v-if="isRunning(row as Pipeline)" type="danger" link @click="handleCancel(row as Pipeline)">取消</el-button>
            <el-button type="info" link @click="router.push('/executions')">记录</el-button>
            <el-button type="warning" link style="margin: 0 8px" @click="openMaintain(row as Pipeline)">维护</el-button>
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

    <!-- 实验设计(2026-11-26 换代):design JSON 编辑,run.py --design 唯一入口 -->
    <el-dialog v-model="designVisible" title="实验设计" width="980px" top="4vh">
      <div class="exec-hint" style="margin-bottom: 10px">
        按组织(scope)定义组与对比:组=样本集合,对比=组序列 × 编码方案,派生对比名自动为"参照组名_vs_检验组名"。
        样本取自该 Meta TSV,tissue 列决定 scope(无 tissue 列即单 scope UNKNOWN);对比名参与输出命名。
      </div>
      <el-radio-group v-model="designEditMode" size="small" style="margin-bottom: 10px" @change="onDesignModeChange">
        <el-radio-button label="form">表单编辑</el-radio-button>
        <el-radio-button label="json">JSON 粘贴</el-radio-button>
      </el-radio-group>

      <template v-if="designEditMode === 'form'">
      <el-tabs v-model="designActiveScope">
        <el-tab-pane v-for="sc in designScopes" :key="sc.scope" :label="sc.scope" :name="sc.scope">
          <!-- 组注册表 -->
          <div class="design-sub-title">组注册表(组内样本同组织、同 data_type)</div>
          <el-table :data="sc.groups" size="small" border>
            <el-table-column label="组名" width="180">
              <template #default="{ row }">
                <el-input v-model="row.name" placeholder="如 DMSO_P6" />
              </template>
            </el-table-column>
            <el-table-column label="样本" min-width="340">
              <template #default="{ row }">
                <el-select v-model="row.samples" multiple filterable style="width: 100%" placeholder="选择样本">
                  <el-option
                    v-for="s in samplesOfScope(sc.scope)"
                    :key="s.id"
                    :label="s.id + (s.dataType ? ' · ' + s.dataType : '')"
                    :value="s.id"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70">
              <template #default="{ $index }">
                <el-button type="danger" link size="small" @click="sc.groups.splice($index, 1)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button size="small" style="margin-top: 8px" @click="sc.groups.push({ name: '', samples: [] })">+ 添加组</el-button>
          <el-button size="small" style="margin-top: 8px; margin-left: 6px" @click="seedGroupsFromMeta(sc as DesignScopeRow)">
            从 meta 的 group 列导入组
          </el-button>

          <!-- 对比定义 -->
          <div class="design-sub-title" style="margin-top: 14px">对比定义(组序列顺序即生物学顺序:consecutive/helmert 依赖顺序,vs_ref 缺省取首个为基线)</div>
          <el-table :data="sc.comparisons" size="small" border>
            <el-table-column label="对比名(参与输出命名)" width="175">
              <template #default="{ row }">
                <el-input v-model="row.name" placeholder="如 vs_baseline" />
              </template>
            </el-table-column>
            <el-table-column label="组序列(顺序=生物学顺序)" min-width="250">
              <template #default="{ row }">
                <div class="design-seq">
                  <div v-for="(g, gi) in row.groups" :key="g" class="design-seq-row">
                    <span class="design-seq-no">{{ gi + 1 }}</span>
                    <span class="design-seq-name">{{ g }}</span>
                    <span class="design-seq-ops">
                      <el-tooltip content="上移" placement="top">
                        <el-button link size="small" :disabled="gi === 0" @click="moveDesignGroup(row as DesignCompRow, gi, -1)">
                          <el-icon><Top /></el-icon>
                        </el-button>
                      </el-tooltip>
                      <el-tooltip content="下移" placement="top">
                        <el-button link size="small" :disabled="gi === row.groups.length - 1" @click="moveDesignGroup(row as DesignCompRow, gi, 1)">
                          <el-icon><Bottom /></el-icon>
                        </el-button>
                      </el-tooltip>
                      <el-tooltip content="移出序列" placement="top">
                        <el-button link size="small" type="danger" @click="row.groups.splice(gi, 1)">
                          <el-icon><Close /></el-icon>
                        </el-button>
                      </el-tooltip>
                    </span>
                  </div>
                  <div v-if="!row.groups.length" class="design-seq-empty">尚未选组,从下方"添加组"按顺序加入</div>
                </div>
                <el-dropdown trigger="click" style="margin-top: 6px" @command="(cmd: string) => addDesignGroup(row as DesignCompRow, cmd)">
                  <el-button size="small" type="primary">
                    + 添加组<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-for="g in availableGroupsFor(sc, row as DesignCompRow)" :key="g" :command="g">
                        {{ g }}
                      </el-dropdown-item>
                      <el-dropdown-item v-if="!availableGroupsFor(sc, row as DesignCompRow).length" disabled>
                        该 scope 的组已全部加入
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>
            </el-table-column>
            <el-table-column label="coding" width="150">
              <template #default="{ row }">
                <el-select v-model="row.coding" style="width: 100%">
                  <el-option label="consecutive(相邻两两)" value="consecutive" />
                  <el-option label="vs_ref(对基线)" value="vs_ref" />
                  <el-option label="all_pairs(全两两)" value="all_pairs" />
                  <el-option label="helmert(对此前均值)" value="helmert" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="ref(基线)" width="130">
              <template #default="{ row }">
                <el-select v-if="row.coding === 'vs_ref'" v-model="row.ref" style="width: 100%" placeholder="缺省首组">
                  <el-option v-for="g in row.groups" :key="g" :label="g" :value="g" />
                </el-select>
                <span v-else style="color: #c0c4cc">—</span>
              </template>
            </el-table-column>
            <el-table-column label="派生对比" min-width="185">
              <template #default="{ row }">
                <el-tag
                  v-for="d in derivedFor(sc, row as DesignCompRow)"
                  :key="d.name"
                  :type="d.conflict ? 'danger' : 'primary'"
                  size="small"
                  effect="plain"
                  style="margin: 1px 4px 1px 0"
                >{{ d.name }}</el-tag>
                <span v-if="!derivedFor(sc, row as DesignCompRow).length" style="color: #c0c4cc">-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="60">
              <template #default="{ $index }">
                <el-button type="danger" link size="small" @click="sc.comparisons.splice($index, 1)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button size="small" style="margin-top: 8px" @click="sc.comparisons.push({ name: '', groups: [], ref: '', coding: 'consecutive' })">+ 添加对比</el-button>

          <!-- tests -->
          <div class="design-sub-title" style="margin-top: 14px">检验(tests)</div>
          <el-checkbox-group v-model="sc.tests">
            <el-checkbox label="pairwise">pairwise(已消费)</el-checkbox>
            <el-checkbox label="omnibus">omnibus(保留声明)</el-checkbox>
            <el-checkbox label="trend">trend(保留声明)</el-checkbox>
          </el-checkbox-group>
        </el-tab-pane>
      </el-tabs>

      <!-- 实时校验 -->
      <div v-if="designWarns.length" class="exec-hint" style="margin-top: 12px">
        <div v-for="m in designWarns" :key="m">ℹ {{ m }}</div>
      </div>
      <div v-if="designIssues.length" class="json-error" style="margin-top: 12px">
        <div v-for="m in designIssues" :key="m">⚠ {{ m }}</div>
      </div>

      <el-divider>JSON 原文(只读预览)</el-divider>
      <JsonTreeFold :model-value="designJsonPreview" max-height="220px" />
      </template>

      <!-- JSON 粘贴模式:原文直存,_ 注释键原样保留 -->
      <template v-else>
        <div class="exec-hint" style="margin-bottom: 8px">
          直接粘贴或编辑 design JSON(与 run.py --design 文件同构;<b>_ 开头键为注释</b>,保存时原文原样保留)。
          切回"表单编辑"会按此 JSON 重新装载;保存前按同一套规则校验。
        </div>
        <div style="margin-bottom: 6px">
          <el-button size="small" @click="formatDesignJsonText">格式化</el-button>
          <el-button size="small" @click="validateDesignJsonText">校验</el-button>
        </div>
        <el-input v-model="designJsonText" type="textarea" :rows="18"
          style="font-family: 'JetBrains Mono', Consolas, monospace; font-size: 12px" />
        <div v-if="designJsonMsg" :class="designJsonOk ? 'exec-hint' : 'json-error'" style="margin-top: 8px">
          {{ designJsonMsg }}
        </div>
      </template>

      <template #footer>
        <el-button @click="designVisible = false">取消</el-button>
        <el-button type="primary" :loading="designSaving"
          :disabled="designEditMode === 'form' && designIssues.length > 0" @click="saveDesign">保存</el-button>
      </template>
    </el-dialog>

    <!-- 维护操作输出(2026-11-25 表单化:操作与参数可见可改) -->
    <el-dialog v-model="maintainVisible" title="分析维护" width="760px" top="6vh">
      <div v-if="maintainRow">
        <div style="font-weight: 600; margin-bottom: 8px;">{{ maintainRow.name }}</div>
        <el-form label-width="110px">
          <el-form-item label="操作">
            <el-select v-model="maintainMode" style="width: 100%">
              <el-option value="dry-run" label="预检 (dry-run,只读)" />
              <el-option value="unlock" label="解锁 (--unlock)" />
              <el-option value="delete-temp-output" label="清理临时文件 (--delete-temp-output)" />
              <el-option value="touch" label="触碰时间戳 (--touch,可能导致下游重跑)" />
              <el-option value="cleanup-metadata" label="清理元数据 (--cleanup-metadata)" />
              <el-option v-if="canCalibrate" value="resource-calibrate" label="资源校准 (benchmark→建议表→护栏写回)" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="maintainMode === 'cleanup-metadata'" label="文件路径">
            <el-input v-model="maintainExtra" placeholder="相对输出目录,空格分隔;后端按白名单校验" />
          </el-form-item>
          <div class="exec-hint">即时执行不产生执行记录(审计照记);运行中执行存在时维护会被拒绝。强制重跑规则请走「执行」弹窗的"强制重跑规则"(run.py --forcerun,真实执行链路)。</div>
        </el-form>
        <div style="text-align: right; margin-top: 8px;">
          <el-button type="primary" :loading="maintainRunning" @click="runMaintain">执行</el-button>
        </div>
      </div>
      <template v-if="maintainOutput">
        <div style="font-size: 12px; color: #909399; margin: 12px 0 8px;">{{ maintainTitle }}</div>
        <pre :style="{ background: '#f5f7fa', borderRadius: '4px', padding: '12px', fontFamily: 'monospace', fontSize: '12px', maxHeight: '45vh', overflow: 'auto', whiteSpace: 'pre-wrap', wordBreak: 'break-all', margin: '0' }">{{ maintainOutput }}</pre>
      </template>

      <!-- 资源校准建议表(2026-11-26 两段式:预览→确认写回) -->
      <template v-if="calibRows.length > 0">
        <div style="font-size: 12px; color: #909399; margin: 12px 0 8px;">
          校准建议(版本 {{ (calibVersion || '').substring(0, 8) }},历史 {{ calibHistoryRows }} 行;护栏:n≥3、±30% 限幅、取整、删失下界)
        </div>
        <el-table :data="calibRows" size="small" border max-height="300">
          <el-table-column prop="rule" label="规则" min-width="140" show-overflow-tooltip />
          <el-table-column prop="nOk" label="n" width="55" align="center" />
          <el-table-column prop="nCensored" label="删失" width="55" align="center" />
          <el-table-column prop="observedP90Mb" label="实测 p90 (MB)" width="115" align="right" />
          <el-table-column prop="suggestMb" label="建议值 (MB)" width="105" align="right" />
          <el-table-column prop="prevDeclaredMb" label="现声明值" width="85" align="right" />
          <el-table-column prop="deltaPct" label="delta" width="75" align="right" />
          <el-table-column prop="note" label="备注" min-width="120" show-overflow-tooltip />
        </el-table>
        <div style="text-align: right; margin-top: 10px;">
          <el-button type="success" :loading="calibApplying" @click="applyCalibration">应用写回 config</el-button>
        </div>
      </template>
    </el-dialog>

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
        <div class="template-toolbar">
          <el-input v-model="templateSearch" placeholder="搜索名称 / 描述 / 分类" clearable style="width: 240px" />
          <el-radio-group v-model="templateTypeFilter" size="small">
            <el-radio-button value="all">全部 ({{ templateCount('all') }})</el-radio-button>
            <el-radio-button value="pipeline">流程 ({{ templateCount('pipeline') }})</el-radio-button>
            <el-radio-button value="task">模块 ({{ templateCount('task') }})</el-radio-button>
          </el-radio-group>
        </div>
        <div v-loading="templateLoading" class="template-grid">
          <el-empty v-if="!templateList.length" description="暂无模板，请先在系统管理中导入" />
          <el-empty v-else-if="!filteredTemplates.length" description="没有匹配的模板" />
          <el-radio-group v-else v-model="selectedTemplateName" class="template-radio-group">
            <el-radio-button
              v-for="tpl in filteredTemplates"
              :key="tpl.id"
              :value="tpl.name"
              class="template-option"
            >
              <div class="template-option-content">
                <div class="template-title-line">
                  <strong>{{ tpl.name }}</strong>
                  <el-tag size="small" :type="tpl.type === 'pipeline' ? 'primary' : 'warning'">
                    {{ tpl.type === 'pipeline' ? '流程' : '模块' }}
                  </el-tag>
                  <el-tag size="small" type="info">{{ tpl.category }}</el-tag>
                </div>
                <span class="template-desc">{{ tpl.description }}</span>
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
          <el-form-item v-if="metaList.length" label="已有样本">
            <div class="existing-samples">
              <div class="existing-samples-hint">项目内已录入的样本信息，点「填入」直接作为输入数据：</div>
              <div v-for="sm in metaList" :key="sm.id" class="existing-sample-item">
                <div class="existing-sample-info">
                  <strong>{{ sm.name }}</strong>
                  <el-tag size="small" type="info">{{ sm.metaMode === 'path' ? '路径' : 'TSV' }}</el-tag>
                  <span class="existing-sample-preview">{{ tsvPreview(sm.metaContent) }}</span>
                </div>
                <el-button size="small" @click="fillFromSampleMeta(sm)">填入</el-button>
              </div>
            </div>
          </el-form-item>
          <div v-if="metaTpl && metaTpl.available" class="meta-tpl-panel">
            <div class="meta-tpl-head">
              <span>该流程的 meta 列固定为：</span>
              <el-tag v-for="c in metaTpl.columns" :key="c" size="small" style="margin-right: 4px;">{{ c }}</el-tag>
              <el-button size="small" type="primary" link @click="fillCreateFromMetaTpl">按模板填入</el-button>
            </div>
            <pre class="meta-tpl-example">{{ metaTpl.filled }}</pre>
          </div>
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
          <el-button type="primary" :disabled="!metaContent.trim()" @click="gotoStep2">下一步</el-button>
        </div>
      </div>

      <!-- Step 2: Extra Params Override (optional) -->
      <div v-if="createStep === 2">
        <el-form label-width="100px">
          <el-form-item label="描述">
            <el-input v-model="analysisDescription" type="textarea" :rows="2" placeholder="可选" />
          </el-form-item>
          <el-form-item label="参数覆盖">
            <div class="param-override-panel">
              <div class="param-hint">
                以下参数来自模板「{{ selectedTemplateName }}」的默认配置，填写覆盖值即替换默认值，留空使用默认值。
                覆盖以 dot 路径生效（如 Params.cutadapt.quality），执行时替换命令模板里的 config.xxx 占位符。
              </div>
              <el-table v-if="paramRows.length" :data="paramRows" size="small" max-height="320" border>
                <el-table-column prop="path" label="参数" min-width="210" show-overflow-tooltip />
                <el-table-column label="默认值" min-width="120">
                  <template #default="{ row }">
                    <span class="param-default">{{ displayValue((row as ParamRow).value) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip />
                <el-table-column label="覆盖值" min-width="170">
                  <template #default="{ row }">
                    <el-input
                      v-model="(row as ParamRow).override"
                      size="small"
                      clearable
                      :placeholder="displayValue((row as ParamRow).value)"
                    />
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-else description="该模板无可解析的配置参数，可用下方自定义参数覆盖" :image-size="60" />
              <div v-for="(c, i) in customParams" :key="i" class="custom-param-row">
                <el-input v-model="c.path" size="small" placeholder="参数路径，如 Params.cutadapt.quality" style="width: 280px" />
                <el-input v-model="c.override" size="small" placeholder="覆盖值" style="width: 180px" />
                <el-button size="small" text type="danger" @click="customParams.splice(i, 1)">移除</el-button>
              </div>
              <div style="margin-top: 8px">
                <el-button size="small" @click="addCustomParam">+ 自定义参数</el-button>
              </div>
              <div v-if="Object.keys(builtExtraParams).length" class="param-preview">
                覆盖预览：<code>{{ JSON.stringify(builtExtraParams) }}</code>
              </div>
            </div>
          </el-form-item>
        </el-form>
        <div style="text-align: right; margin-top: 16px">
          <el-button @click="createStep = 1">上一步</el-button>
          <el-button type="primary" :loading="submitLoading" @click="handleCreateAnalysis">创建</el-button>
        </div>
      </div>
    </el-dialog>

    <!-- Execute Analysis Dialog -->
    <el-dialog v-model="executeDialogVisible" title="执行分析" width="560px">
      <div v-if="executingPipeline">
        <div style="margin-bottom: 14px;">
          <div style="font-weight: 600; margin-bottom: 4px;">{{ executingPipeline.name }}</div>
          <div class="exec-hint">结果目录 results/{{ executingPipeline.id }}/，自动创建；同一分析重跑共用该目录，增量续跑</div>
          <div class="exec-hint">进度与日志见「执行状态」列和执行监控页</div>
        </div>
        <el-form label-width="130px">
          <el-form-item v-for="p in inputParamRows" :key="p.key" :label="p.key">
            <el-select
              v-if="metaFileSuggestions.length"
              v-model="p.value"
              filterable
              allow-create
              default-first-option
              clearable
              style="width: 100%"
              placeholder="选择样本文件路径，或直接输入"
            >
              <el-option v-for="f in metaFileSuggestions" :key="f" :label="f" :value="f" />
            </el-select>
            <el-input v-else v-model="p.value" :placeholder="'执行参数 ' + p.key" />
          </el-form-item>
          <el-form-item v-if="!inputParamRows.length" label="执行参数">
            <span class="exec-hint">无需执行时参数</span>
          </el-form-item>
          <el-form-item label="强制重跑规则">
            <el-input v-model="executeForcerun" placeholder="可选,空格分隔;规则短名(不带工作流前缀,如 scanpy_qc)或 /path/to/file" />
            <div class="exec-hint">留空=正常增量执行;填写=本次运行强制重跑指定规则并停在该处(run.py --forcerun)</div>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="executeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="executeLoading" @click="confirmExecute">执行</el-button>
      </template>
    </el-dialog>

    <!-- Edit Analysis Dialog -->
    <el-dialog v-model="editVisible" title="编辑分析" width="680px">
      <div v-if="metaList.length" class="existing-samples" style="margin-bottom: 14px;">
        <div class="existing-samples-hint">已有样本信息（点击"填入"覆盖下方 Meta TSV）：</div>
        <div v-for="sm in metaList" :key="sm.id" class="existing-sample-item">
          <div class="existing-sample-info">
            <strong>{{ sm.name }}</strong>
            <el-tag size="small" type="info">{{ sm.metaMode === 'path' ? '路径' : 'TSV' }}</el-tag>
            <span class="existing-sample-preview">{{ tsvPreview(sm.metaContent) }}</span>
          </div>
          <el-button size="small" @click="fillEditFromSampleMeta(sm)">填入</el-button>
        </div>
      </div>
      <div v-if="metaTpl && metaTpl.available" class="meta-tpl-panel" style="margin-bottom: 12px;">
        <div class="meta-tpl-head">
          <span>该流程的 meta 列固定为：</span>
          <el-tag v-for="c in metaTpl.columns" :key="c" size="small" style="margin-right: 4px;">{{ c }}</el-tag>
          <el-button size="small" type="primary" link @click="fillEditFromMetaTpl">按模板填入</el-button>
        </div>
        <pre class="meta-tpl-example">{{ metaTpl.filled }}</pre>
      </div>
      <el-form label-width="100px">
        <el-form-item label="分析名称">
          <el-input v-model="editForm.name" />
        </el-form-item>
        <el-form-item label="Meta TSV">
          <el-input v-model="editForm.metaContent" type="textarea" :rows="8" />
        </el-form-item>
      </el-form>
      <div style="font-size: 12px; color: #909399;">
        参数覆盖等更多设置在「流程管理」维护
        <el-button type="primary" link size="small" @click="editVisible = false; router.push({ path: '/pipelines', query: { edit: editForm.id } })">前往流程管理</el-button>
      </div>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editLoading" @click="handleSaveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 添加子项目弹窗 -->
    <el-dialog v-model="addChildVisible" title="添加子项目" width="500px">
      <el-form label-width="80px">
        <el-form-item label="选择项目">
          <el-select v-model="addChildId" filterable placeholder="选择一个现有项目作为子项目" style="width: 100%">
            <el-option v-for="p in availableChildren" :key="p.id" :label="p.name" :value="p.id">
              <span>{{ p.name }}</span>
              <span style="float: right; color: #909399; font-size: 12px">ID: {{ p.id }}</span>
            </el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addChildVisible = false">取消</el-button>
        <el-button type="primary" :loading="addChildLoading" @click="handleAddChild">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import 'element-plus/theme-chalk/el-message-box.css'
import 'element-plus/theme-chalk/el-message.css'
import { Plus, Upload, FolderOpened, Document, Download, Folder, Close, Top, Bottom, ArrowDown } from '@element-plus/icons-vue'
import { getProject, createAnalysis, listAnalyses, exportExcel, exportPpt, getFileTree, batchDownload, downloadAll, listProjects, updateProject, unbindParent } from '@/api/projectApi'
import type { Project, Pipeline, FileTreeNode } from '@/api/projectApi'
import { listSampleMeta, createSampleMeta, updateSampleMeta, deleteSampleMeta } from '@/api/sampleMetaApi'
import type { SampleMeta } from '@/api/sampleMetaApi'
import { getMetaFiles, registerMissingMetaFiles, getMetaFilesSummary } from '@/api/sampleMetaFileApi'
import type { SampleMetaFileRef, SampleMetaFilesResult } from '@/api/sampleMetaFileApi'
import { listTemplates, getTemplate, getMetaTemplate } from '@/api/templateApi'
import type { WorkflowTemplate, MetaTemplate } from '@/api/templateApi'
import { executePipeline, updatePipeline, maintainPipeline, calibratePipeline } from '@/api/pipelineApi'
import { listExecutions, cancelExecution } from '@/api/executionApi'
import type { Execution } from '@/api/executionApi'
import { listFiles, uploadFile, deleteFile, importLocalFiles } from '@/api/dataFileApi'
import type { DataFile } from '@/api/dataFileApi'
import FileEditDialog from '@/components/FileEditDialog.vue'
import JsonTreeFold from '@/components/JsonTreeFold.vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const fileEditRef = ref<InstanceType<typeof FileEditDialog> | null>(null)
const router = useRouter()
const projectId = computed(() => Number(route.params.id))

const loading = ref(false)
const submitLoading = ref(false)
const templateLoading = ref(false)
const fileLoading = ref(false)
const importLoading = ref(false)
const createDialogVisible = ref(false)
const importDialogVisible = ref(false)
const createStep = ref(0)

// 子项目状态
const childProjects = ref<Project[]>([])
const addChildVisible = ref(false)
const addChildId = ref<number | null>(null)
const addChildLoading = ref(false)
const allProjects = ref<Project[]>([])

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
// 文件引用汇总缓存：metaId -> { matched, total, lost }（列表加载即批量主动获取,明细打开时刷新）
const metaFileRefSummary = reactive<Record<number, { matched: number; total: number; lost?: number }>>({})
const fileRefsVisible = ref(false)
const fileRefsLoading = ref(false)
const fileRefsMetaName = ref('')
const fileRefsMetaId = ref<number | null>(null)
const fileRefs = ref<SampleMetaFileRef[]>([])
const fileRefsSummary = ref<SampleMetaFilesResult | null>(null)
const registerLoading = ref(false)
const metaDialogVisible = ref(false)
const metaDialogTitle = ref('新建 Meta')
const metaSaving = ref(false)
const metaEditTab = ref('table')
const metaForm = reactive({ id: 0, projectId: projectId.value, name: '', metaMode: 'fastq', metaContent: '', description: '' })
const metaColumns = ref<string[]>([])
const metaRows = ref<string[][]>([])
const metaColWidths = ref<number[]>([])
const metaTsvContent = ref('')
const importTsvDialogVisible = ref(false)
const importTsvContent = ref('')
const importTsvName = ref('')
const importedFileName = ref('')

const META_MODE_COLUMNS: Record<string, string[]> = {
  // tissue=实验设计 scope 来源(2026-11-26);data_type 供设计校验(组内同 data_type),可留空
  fastq: ['sample_id', 'design', 'fastq_1', 'fastq_2', 'group', 'organism', 'tissue', 'data_type'],
  pacbio: ['sample_id', 'design', 'bam', 'pbi', 'group', 'organism', 'tissue', 'data_type'],
  ms: ['sample_id', 'ms_file', 'organism', 'tissue', 'data_type'],
  scrnaseq: ['sample_id', 'fastq_dir', 'sample_prefix', 'design', 'group', 'organism', 'tissue', 'data_type'],
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
const designJson = ref('')
const analysisDescription = ref('')
const extraParamsText = ref('')
const extraParamsError = ref('')

// Import form
const importDirPath = ref('')

// --- Data loading ---
const loadProject = async () => {
  try {
    const res = await getProject(projectId.value)
    Object.assign(project, res)
  } catch (e) {
    ElMessage.error('加载项目失败')
  }
}

// 子项目相关
const availableChildren = computed(() => {
  const childIds = new Set(childProjects.value.map(c => c.id))
  return allProjects.value.filter(p => p.id !== projectId.value && !childIds.has(p.id))
})

const loadChildProjects = async () => {
  try {
    const res = await listProjects({ page: 1, size: 200 })
    const all = (res.records || []) as any as Project[]
    allProjects.value = all
    childProjects.value = all.filter(p => p.parentId === projectId.value)
  } catch (e) {
    console.error('加载子项目失败:', e)
  }
}

const showAddChildDialog = () => {
  addChildId.value = null
  addChildVisible.value = true
}

const handleAddChild = async () => {
  if (!addChildId.value) {
    ElMessage.warning('请选择一个项目')
    return
  }
  addChildLoading.value = true
  try {
    const child = allProjects.value.find(p => p.id === addChildId.value)
    if (!child) return
    await updateProject(child.id, { id: child.id, parentId: projectId.value } as any)
    ElMessage.success('子项目添加成功')
    addChildVisible.value = false
    await loadChildProjects()
  } catch (e) {
    ElMessage.error('添加失败')
  } finally {
    addChildLoading.value = false
  }
}

const removeChild = async (child: Project) => {
  try {
    await ElMessageBox.confirm(`确定将"${child.name}"从子项目中移除？`, '提示', { type: 'warning' })
    await unbindParent(child.id)
    ElMessage.success('已移除')
    await loadChildProjects()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('移除失败')
  }
}

const loadAnalyses = async () => {
  loading.value = true
  try {
    const res = await listAnalyses(projectId.value, { page: pagination.page, size: pagination.size })
    analysesList.value = res.records
    pagination.total = res.total
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}

const loadFiles = async () => {
  fileLoading.value = true
  try {
    const res = await listFiles({ projectId: projectId.value, page: filePagination.page, size: filePagination.size })
    fileList.value = res.records
    filePagination.total = res.total
  } catch (e) { console.error(e) }
  finally { fileLoading.value = false }
}

const loadTemplates = async () => {
  templateLoading.value = true
  try {
    const res = await listTemplates({ page: 1, size: 500 })
    templateList.value = res.records
  } catch (e) { console.error(e) }
  finally { templateLoading.value = false }
}

// --- File tree（基于流程执行输出目录） ---
const loadFileTree = async () => {
  treeLoading.value = true
  try {
    const res = await getFileTree(projectId.value)
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
    metaList.value = await listSampleMeta(projectId.value) as any || []
    await refreshFileRefSummaries()
  } catch (e) { console.error(e) }
  finally { metaLoading.value = false }
}

// 文件引用状态主动获取(2026-11-26):列表加载即批量拉取,不再等逐条打开明细
const refreshFileRefSummaries = async () => {
  const ids = metaList.value.map(m => m.id).filter(Boolean)
  if (!ids.length) return
  try {
    const res = await getMetaFilesSummary(ids) as any[]
    for (const s of res || []) {
      metaFileRefSummary[s.metaId] = { matched: s.matchedRefs, total: s.totalRefs, lost: s.lostRefs }
    }
  } catch (e) { console.error(e) }
}

// --- 新建分析:模板搜索/类型筛选 ---
const templateSearch = ref('')
const templateTypeFilter = ref<'all' | 'pipeline' | 'task'>('all')
const filteredTemplates = computed(() => {
  const kw = templateSearch.value.trim().toLowerCase()
  return templateList.value.filter(t => {
    if (templateTypeFilter.value !== 'all' && t.type !== templateTypeFilter.value) return false
    if (!kw) return true
    return ((t.name || '') + ' ' + (t.description || '') + ' ' + (t.category || '')).toLowerCase().includes(kw)
  })
})
const templateCount = (type: 'all' | 'pipeline' | 'task') =>
  type === 'all' ? templateList.value.length : templateList.value.filter(t => t.type === type).length

// --- 新建分析:已有样本信息填入输入数据 ---
const tsvPreview = (content?: string) => {
  if (!content || !content.trim()) return '（空）'
  const text = content.trim().split('\n').slice(0, 2).join('  |  ')
  return text.length > 80 ? text.slice(0, 80) + '…' : text
}

const fillFromSampleMeta = (sm: SampleMeta) => {
  metaType.value = sm.metaMode === 'path' ? 'path' : 'text'
  metaContent.value = sm.metaContent || ''
  // 实验设计关系随 meta 一并带入(2026-11-25),执行时落 comparison.json 传 --comparison
  designJson.value = sm.designJson || ''
  const n = countDesignComparisons(designJson.value)
  ElMessage.success(`已填入「${sm.name}」` + (n ? `,含 ${n} 个实验对比` : ''))
}

// --- 参数覆盖:按模板 config/schema 渲染可调参数 ---
interface ParamRow { path: string; value: any; description: string; override: string }
const paramRows = ref<ParamRow[]>([])
const customParams = ref<{ path: string; override: string }[]>([])

const displayValue = (v: any) => {
  if (v === null || v === undefined) return 'null'
  if (typeof v === 'object') return JSON.stringify(v)
  return String(v)
}

const deepFindDesc = (obj: any, key: string): string => {
  if (!obj || typeof obj !== 'object') return ''
  for (const [k, v] of Object.entries(obj)) {
    if (k === key && v && typeof v === 'object' && typeof (v as any).description === 'string') {
      return (v as any).description
    }
    if (v && typeof v === 'object') {
      const r = deepFindDesc(v, key)
      if (r) return r
    }
  }
  return ''
}

const schemaDesc = (schema: any, path: string): string => {
  if (!schema || typeof schema !== 'object') return ''
  let node: any = schema
  for (const seg of path.split('.')) {
    if (node && typeof node === 'object' && seg in node) node = node[seg]
    else { node = null; break }
  }
  if (node && typeof node === 'object' && typeof node.description === 'string') return node.description
  return deepFindDesc(schema, path.split('.').pop() || '')
}

const flattenConfig = (obj: any, prefix: string, out: ParamRow[], schema: any) => {
  if (obj && typeof obj === 'object' && !Array.isArray(obj)) {
    const keys = Object.keys(obj)
    if (keys.length === 0) {
      if (prefix) out.push({ path: prefix, value: {}, description: schemaDesc(schema, prefix), override: '' })
      return
    }
    for (const k of keys) {
      flattenConfig((obj as any)[k], prefix ? prefix + '.' + k : k, out, schema)
    }
  } else if (prefix) {
    out.push({ path: prefix, value: obj, description: schemaDesc(schema, prefix), override: '' })
  }
}

const buildParamRows = () => {
  const tpl = templateList.value.find(t => t.name === selectedTemplateName.value)
  let config: any = {}
  let schema: any = {}
  try { config = JSON.parse(tpl?.configTemplate || '{}') } catch { /* 忽略坏 JSON */ }
  try { schema = JSON.parse(tpl?.schemaJson || '{}') } catch { /* 忽略坏 JSON */ }
  const rows: ParamRow[] = []
  flattenConfig(config, '', rows, schema)
  paramRows.value = rows
}
watch(selectedTemplateName, buildParamRows)

const coerceValue = (raw: string, def: any) => {
  const t = raw.trim()
  if (t === '') return undefined
  if (typeof def === 'number') {
    const n = Number(t)
    return Number.isNaN(n) ? t : n
  }
  if (typeof def === 'boolean') return t === 'true' || t === '1'
  if (t === 'null') return null
  const n = Number(t)
  if (t !== '' && !Number.isNaN(n)) return n
  return t
}

const builtExtraParams = computed(() => {
  const out: Record<string, any> = {}
  for (const r of paramRows.value) {
    if (r.override.trim() === '') continue
    out[r.path] = coerceValue(r.override, r.value)
  }
  for (const c of customParams.value) {
    if (!c.path.trim() || c.override.trim() === '') continue
    out[c.path.trim()] = coerceValue(c.override, undefined)
  }
  return out
})

const addCustomParam = () => customParams.value.push({ path: '', override: '' })

// --- 实验设计(2026-11-26 换代):design JSON 编辑(run.py --design 唯一入口) ---
interface DesignGroupRow { name: string; samples: string[] }
interface DesignCompRow { name: string; groups: string[]; ref: string; coding: string }
interface DesignScopeRow { scope: string; groups: DesignGroupRow[]; comparisons: DesignCompRow[]; tests: string[] }
interface DesignSampleRow { id: string; tissue: string; dataType: string; group: string }

const userStore = useUserStore()
const canCalibrate = computed(() => userStore.hasPermission('pipeline:calibrate'))
const designVisible = ref(false)
const designSaving = ref(false)
const designMeta = ref<SampleMeta | null>(null)
const designScopes = ref<DesignScopeRow[]>([])
const designActiveScope = ref('')
const designSamples = ref<DesignSampleRow[]>([])

// 统计 design JSON 的对比条目数(跨 scope;兼容旧数组格式)
const countDesignComparisons = (json: string): number => {
  if (!json) return 0
  try {
    const obj = JSON.parse(json)
    if (Array.isArray(obj)) return obj.length
    let n = 0
    for (const block of Object.values(obj || {})) {
      if (block && typeof block === 'object') n += Object.keys((block as any).comparisons || {}).length
    }
    return n
  } catch { return 0 }
}

// 解析 Meta TSV 的 sample_id/tissue/data_type 三列(tissue 缺省 UNKNOWN)
const parseSampleRows = (content: string): DesignSampleRow[] => {
  if (!content) return []
  const lines = content.trim().split('\n')
  if (lines.length < 2) return []
  const header = lines[0].split('\t').map(h => h.trim().toLowerCase())
  let iSid = header.indexOf('sample_id')
  if (iSid < 0) iSid = 0
  const iTissue = header.indexOf('tissue')
  const iGroup = header.indexOf('group')
  const iDt = header.indexOf('data_type')
  const rows: DesignSampleRow[] = []
  for (let i = 1; i < lines.length; i++) {
    const cells = lines[i].split('\t')
    const id = (cells[iSid] || '').trim()
    if (!id) continue
    rows.push({
      id,
      tissue: iTissue >= 0 ? ((cells[iTissue] || '').trim() || 'UNKNOWN') : 'UNKNOWN',
      dataType: iDt >= 0 ? (cells[iDt] || '').trim().toUpperCase() : '',
      group: iGroup >= 0 ? (cells[iGroup] || '').trim() : ''
    })
  }
  return rows
}

const samplesOfScope = (scope: string) => designSamples.value.filter(s => s.tissue === scope)

// 派生两两对(与 Omics MetaUtil.build_design_model_pairs 逐条一致):
// consecutive 相邻两两(前者参照)/vs_ref 其余各组对 ref(缺省首组)/all_pairs 全两两/
// helmert 第 k 组对此前 k-1 组均值(参照侧名固定 prev_mean)
const derivePairs = (names: string[], coding: string, ref: string): [string, string][] => {
  const pairs: [string, string][] = []
  if (coding === 'vs_ref') {
    const r = ref || names[0]
    for (const n of names) if (n !== r) pairs.push([r, n])
  } else if (coding === 'all_pairs') {
    for (let i = 0; i < names.length; i++) {
      for (let j = i + 1; j < names.length; j++) pairs.push([names[i], names[j]])
    }
  } else if (coding === 'helmert') {
    for (let k = 1; k < names.length; k++) pairs.push(['prev_mean', names[k]])
  } else {
    for (let i = 0; i < names.length - 1; i++) pairs.push([names[i], names[i + 1]])
  }
  return pairs
}

// 派生名 → 归属集合(跨条目撞名检测,镜像"派生对比名不可跨条目重复")
const derivedNameOwners = computed(() => {
  const map = new Map<string, Set<string>>()
  for (const sc of designScopes.value) {
    for (const c of sc.comparisons) {
      if (!c.name.trim() || c.groups.length < 2) continue
      const key = `${sc.scope}/${c.name.trim()}`
      for (const [a, b] of derivePairs(c.groups, c.coding, c.ref)) {
        const dname = `${a}_vs_${b}`
        if (!map.has(dname)) map.set(dname, new Set())
        map.get(dname)!.add(key)
      }
    }
  }
  return map
})

// 派生预览 chips(conflict=跨条目撞名)
const derivedFor = (sc: DesignScopeRow, row: DesignCompRow): { name: string; conflict: boolean }[] => {
  if (!row.name.trim() || row.groups.length < 2) return []
  return derivePairs(row.groups, row.coding, row.ref).map(([a, b]) => {
    const dname = `${a}_vs_${b}`
    return { name: dname, conflict: (derivedNameOwners.value.get(dname)?.size || 0) > 1 }
  })
}

// 该对比还可追加的组(scope 内已注册且未选)
const availableGroupsFor = (sc: DesignScopeRow, row: DesignCompRow): string[] =>
  sc.groups.filter((g: DesignGroupRow) => g.name.trim() && !(row.groups as string[]).includes(g.name.trim()))
    .map((g: DesignGroupRow) => g.name.trim())

const addDesignGroup = (row: DesignCompRow, name: string) => {
  if (name && !(row.groups as string[]).includes(name)) row.groups.push(name)
}

const moveDesignGroup = (row: DesignCompRow, idx: number, delta: number) => {
  const j = idx + delta
  if (j < 0 || j >= row.groups.length) return
  const arr = row.groups
  ;[arr[idx], arr[j]] = [arr[j], arr[idx]]
}

// 实时校验(阻断;与 DesignJsonUtil 同严)
const designIssues = computed(() => {
  const issues: string[] = []
  for (const sc of designScopes.value) {
    const nameSet = new Map<string, DesignGroupRow>()
    for (const g of sc.groups) {
      const n = g.name.trim()
      if (!n) {
        if (g.samples.length) issues.push(`[${sc.scope}] 有组未命名`)
        continue
      }
      if (nameSet.has(n)) issues.push(`[${sc.scope}] 组名重复: ${n}`)
      nameSet.set(n, g)
      if (!g.samples.length) issues.push(`[${sc.scope}] 组 ${n} 样本为空`)
      if (new Set(g.samples).size !== g.samples.length) issues.push(`[${sc.scope}] 组 ${n} 样本重复`)
      const dts = new Set(g.samples
        .map(id => designSamples.value.find(s => s.id === id)?.dataType)
        .filter(Boolean))
      if (dts.size > 1) issues.push(`[${sc.scope}] 组 ${n} 样本跨多个 data_type: ${[...dts].join(', ')}`)
    }
    for (const c of sc.comparisons) {
      const cn = c.name.trim() || '(未命名)'
      if (!c.name.trim()) issues.push(`[${sc.scope}] 有对比未命名`)
      if (c.groups.length < 2) { issues.push(`[${sc.scope}] 对比 ${cn} 需选 ≥2 个组`); continue }
      if (new Set(c.groups).size !== c.groups.length) issues.push(`[${sc.scope}] 对比 ${cn} 组序列有重复`)
      for (const gn of c.groups) {
        if (!nameSet.has(gn)) issues.push(`[${sc.scope}] 对比 ${cn} 引用未注册的组 ${gn}`)
      }
      if (c.coding === 'vs_ref' && c.ref && !c.groups.includes(c.ref)) {
        issues.push(`[${sc.scope}] 对比 ${cn} 的 ref ${c.ref} 不在组序列中`)
      }
      const seen = new Map<string, string>()
      for (const gn of c.groups) {
        const g = nameSet.get(gn)
        if (!g) continue
        for (const s of g.samples) {
          if (seen.has(s) && seen.get(s) !== gn) {
            issues.push(`[${sc.scope}] 对比 ${cn} 样本 ${s} 同属 ${seen.get(s)} 与 ${gn}——同一对比引用的组之间样本须互斥`)
          }
          seen.set(s, gn)
        }
      }
    }
  }
  for (const [dname, owners] of derivedNameOwners.value) {
    if (owners.size > 1) issues.push(`派生对比 ${dname} 撞名: ${[...owners].join(' / ')}——改组名或组序列`)
  }
  return [...new Set(issues)]
})

// 非阻断提示(sccoda 直通约定)
const designWarns = computed(() => {
  const warns: string[] = []
  for (const sc of designScopes.value) {
    for (const c of sc.comparisons) {
      if (c.coding === 'vs_ref' && c.groups.length > 2 && c.ref && c.ref !== c.groups[0]) {
        warns.push(`[${sc.scope}] 对比 ${c.name || '(未命名)'}: 多组 entry 直通 sccoda 时 ref 须为首组(首柱即基线)`)
      }
    }
  }
  return warns
})

// 构建 design JSON(空 scope 不输出;consecutive 缺省不写)
const buildDesignJson = (): Record<string, unknown> => {
  const out: Record<string, unknown> = {}
  for (const sc of designScopes.value) {
    const groups: Record<string, string[]> = {}
    for (const g of sc.groups) {
      const n = g.name.trim()
      if (n) groups[n] = g.samples
    }
    const comparisons: Record<string, unknown> = {}
    for (const c of sc.comparisons) {
      const n = c.name.trim()
      if (!n) continue
      const entry: Record<string, unknown> = { groups: c.groups }
      if (c.coding && c.coding !== 'consecutive') entry.coding = c.coding
      if (c.coding === 'vs_ref' && c.ref) entry.ref = c.ref
      comparisons[n] = entry
    }
    if (Object.keys(groups).length && Object.keys(comparisons).length) {
      const block: Record<string, unknown> = { groups, comparisons }
      if (sc.tests.length) block.tests = sc.tests
      out[sc.scope] = block
    }
  }
  return out
}
const designJsonPreview = computed(() => JSON.stringify(buildDesignJson(), null, 2))

// 从 meta 的 group 列自动识别组(只补缺,不动已建组);每个 meta 通常自带 group 列
const seedGroupsFromMeta = (sc: DesignScopeRow, quiet = false) => {
  const byGroup = new Map<string, string[]>()
  for (const s of samplesOfScope(sc.scope)) {
    const g = s.group.trim()
    if (!g) continue
    if (!byGroup.has(g)) byGroup.set(g, [])
    byGroup.get(g)!.push(s.id)
  }
  let added = 0
  for (const [g, samples] of byGroup) {
    if (!sc.groups.some((x: DesignGroupRow) => x.name.trim() === g)) {
      sc.groups.push({ name: g, samples: [...samples] })
      added++
    }
  }
  if (!quiet) ElMessage.info(added ? `已从 group 列导入 ${added} 个组` : 'group 列没有可导入的新组')
  return added
}

// design 对象 → 表单状态(JSON 粘贴 ↔ 表单 共用装载器)
const loadDesignObject = (stored: Record<string, any>) => {
  const tissues = [...new Set(designSamples.value.map(s => s.tissue))]
  const scopeKeys = tissues.length ? tissues : ['UNKNOWN']
  const toScope = (scope: string, block: any): DesignScopeRow => ({
    scope,
    groups: Object.entries(block?.groups || {}).map(([name, samples]) => ({ name, samples: [...(samples as string[])] })),
    comparisons: Object.entries(block?.comparisons || {}).map(([name, e]: any) => ({
      name, groups: [...(e?.groups || [])], ref: e?.ref || '', coding: e?.coding || 'consecutive'
    })),
    tests: Array.isArray(block?.tests) ? [...block.tests] : ['pairwise']
  })
  designScopes.value = scopeKeys.map(s => toScope(s, stored[s]))
  // 存量 JSON 有而当前 meta tissue 没有的 scope(样本变动遗留)保留展示,便于发现
  for (const [scope, block] of Object.entries(stored)) {
    if (scope.startsWith('_') || scopeKeys.includes(scope)) continue
    designScopes.value.push(toScope(scope, block))
  }
  // 自动识别:某 scope 没有组时,直接从 meta 的 group 列建组(组注册表不该手敲)
  for (const sc of designScopes.value) {
    if (!sc.groups.length) seedGroupsFromMeta(sc, true)
  }
  designActiveScope.value = designScopes.value[0]?.scope || ''
}

const openDesign = (row: SampleMeta) => {
  designMeta.value = row
  designSamples.value = parseSampleRows(row.metaContent)
  let stored: Record<string, any> = {}
  try { stored = row.designJson ? JSON.parse(row.designJson) : {} } catch { stored = {} }
  loadDesignObject(stored)
  designEditMode.value = 'form'
  // JSON 面板初值:有存量原文(含 _ 注释键)按原文,否则按表单构建
  designJsonText.value = Object.keys(stored).length
    ? JSON.stringify(stored, null, 2) : JSON.stringify(buildDesignJson(), null, 2)
  designJsonMsg.value = ''
  designVisible.value = true
}

// ===== JSON 粘贴模式(2026-11-27 用户要求:直接粘贴 design JSON 的入口) =====
const designEditMode = ref<'form' | 'json'>('form')
const designJsonText = ref('')
const designJsonMsg = ref('')
const designJsonOk = ref(true)

const onDesignModeChange = (mode: string | number | boolean | undefined) => {
  if (mode === 'json') {
    designJsonText.value = JSON.stringify(buildDesignJson(), null, 2)
    designJsonMsg.value = ''
    return
  }
  // JSON → 表单:解析并装载;失败留在 JSON 模式
  try {
    const obj = JSON.parse(designJsonText.value || '{}')
    if (obj === null || typeof obj !== 'object' || Array.isArray(obj)) {
      throw new Error('design JSON 顶层须为对象 {scope: {groups, comparisons, ...}}')
    }
    loadDesignObject(obj)
    designJsonMsg.value = ''
  } catch (e: any) {
    ElMessage.error('JSON 无法转表单: ' + (e?.message || e))
    designEditMode.value = 'json'
  }
}

const formatDesignJsonText = () => {
  try {
    designJsonText.value = JSON.stringify(JSON.parse(designJsonText.value || '{}'), null, 2)
    designJsonOk.value = true
    designJsonMsg.value = 'JSON 合法,已格式化'
  } catch (e: any) {
    designJsonOk.value = false
    designJsonMsg.value = 'JSON 解析失败: ' + (e?.message || e)
  }
}

const validateDesignJsonText = () => {
  try {
    const obj = JSON.parse(designJsonText.value || '{}')
    if (obj === null || typeof obj !== 'object' || Array.isArray(obj)) {
      throw new Error('顶层须为对象 {scope: {groups, comparisons, ...}}')
    }
    loadDesignObject(obj)
    if (designIssues.value.length) {
      designJsonOk.value = false
      designJsonMsg.value = '校验未通过: ' + designIssues.value.join(';')
      return
    }
    designJsonOk.value = true
    designJsonMsg.value = 'JSON 合法,校验通过(' + designWarns.value.length + ' 条提示)'
  } catch (e: any) {
    designJsonOk.value = false
    designJsonMsg.value = 'JSON 解析失败: ' + (e?.message || e)
  }
}

const saveDesign = async () => {
  if (!designMeta.value) return
  // JSON 粘贴模式:原文直存(保留 _ 注释键),先按同一套规则校验
  if (designEditMode.value === 'json') {
    let text = designJsonText.value.trim()
    if (!text) text = '{}'
    try {
      const obj = JSON.parse(text)
      if (obj === null || typeof obj !== 'object' || Array.isArray(obj)) {
        throw new Error('顶层须为对象 {scope: {groups, comparisons, ...}}')
      }
      loadDesignObject(obj)
    } catch (e) {
      ElMessage.error('JSON 解析失败,无法保存: ' + (e instanceof Error ? e.message : String(e)))
      return
    }
    if (designIssues.value.length) { ElMessage.warning('请先解决校验问题: ' + designIssues.value[0]); return }
    designSaving.value = true
    try {
      const hasContent = text !== '{}'
      await updateSampleMeta({ ...designMeta.value, designJson: hasContent ? text : undefined })
      ElMessage.success('实验设计已保存')
      designVisible.value = false
      loadMetaList()
    } catch (e) {
      ElMessage.error('保存失败: ' + (e instanceof Error ? e.message : String(e)))
    } finally {
      designSaving.value = false
    }
    return
  }
  if (designIssues.value.length) { ElMessage.warning('请先解决校验问题'); return }
  designSaving.value = true
  try {
    const built = buildDesignJson()
    const hasContent = Object.keys(built).length > 0
    await updateSampleMeta({ ...designMeta.value, designJson: hasContent ? JSON.stringify(built) : undefined })
    ElMessage.success('实验设计已保存')
    designVisible.value = false
    loadMetaList()
  } catch (e) {
    ElMessage.error('保存失败: ' + (e instanceof Error ? e.message : String(e)))
  } finally {
    designSaving.value = false
  }
}

// --- 执行:状态列/执行对话框/取消 ---
const latestExecutions = ref<Record<number, Execution>>({})
let execPollTimer: ReturnType<typeof setInterval> | null = null

// --- 维护操作(2026-11-25 表单化):操作/参数在弹窗内可见可改 ---
const maintainVisible = ref(false)
const maintainTitle = ref('')
const maintainOutput = ref('')
const maintainRow = ref<Pipeline | null>(null)
const maintainMode = ref('dry-run')
const maintainExtra = ref('')
const maintainRunning = ref(false)

// --- 资源校准两段式(2026-11-26):预览建议表 → 确认写回 ---
interface CalibRow {
  rule: string
  nOk: number
  nCensored: number
  observedP90Mb: string
  suggestMb: string
  prevDeclaredMb: number | null
  deltaPct: string
  note: string
}
const calibRows = ref<CalibRow[]>([])
const calibVersion = ref('')
const calibHistoryRows = ref(0)
const calibApplying = ref(false)

const openMaintain = (row: Pipeline) => {
  maintainRow.value = row
  maintainMode.value = 'dry-run'
  maintainExtra.value = ''
  maintainOutput.value = ''
  maintainTitle.value = ''
  calibRows.value = []
  calibVersion.value = ''
  calibHistoryRows.value = 0
  maintainVisible.value = true
}

const runCalibrationPreview = async () => {
  if (!maintainRow.value) return
  maintainRunning.value = true
  maintainTitle.value = '资源校准(预览)'
  maintainOutput.value = '正在导出版本纯净历史并计算建议…'
  calibRows.value = []
  try {
    const res = await calibratePipeline(maintainRow.value.id!, false, false)
    calibRows.value = res.rows || []
    calibVersion.value = res.version || ''
    calibHistoryRows.value = res.historyRows || 0
    maintainOutput.value = res.log || '(无输出)'
    ElMessage.success('校准预览完成')
  } catch (e: any) {
    maintainOutput.value = '校准预览失败: ' + (e?.message || e)
  } finally {
    maintainRunning.value = false
  }
}

const applyCalibration = async () => {
  if (!maintainRow.value) return
  try {
    await ElMessageBox.confirm(
      `将按护栏把建议值写回 Omics config(版本 ${calibVersion.value.substring(0, 8)})。确认写回?`,
      '资源校准写回', { type: 'warning', confirmButtonText: '写回', cancelButtonText: '取消' })
  } catch { return }
  calibApplying.value = true
  try {
    const res = await calibratePipeline(maintainRow.value.id!, true, false)
    calibRows.value = res.rows || []
    maintainOutput.value = (res.log || '') + '\n\n' + (res.diff || '')
    ElMessage.success('写回完成(改动已留痕,git 可回滚)')
  } catch (e: any) {
    const msg = String(e?.message || e)
    if (msg.includes('force') || msg.includes('未提交')) {
      try {
        await ElMessageBox.confirm('Omics 工作区有未提交改动,写回会与手头编辑混在一起。仍要继续吗?', '带脏写回确认',
          { type: 'warning', confirmButtonText: '继续写回', cancelButtonText: '取消' })
      } catch { calibApplying.value = false; return }
      try {
        const res = await calibratePipeline(maintainRow.value.id!, true, true)
        calibRows.value = res.rows || []
        maintainOutput.value = (res.log || '') + '\n\n' + (res.diff || '')
        ElMessage.success('写回完成(改动已留痕,git 可回滚)')
      } catch (e2: any) {
        maintainOutput.value = '写回失败: ' + (e2?.message || e2)
      }
    } else {
      maintainOutput.value = '写回失败: ' + msg
    }
  } finally {
    calibApplying.value = false
  }
}

const runMaintain = async () => {
  if (!maintainRow.value) return
  const mode = maintainMode.value
  if (mode === 'resource-calibrate') {
    await runCalibrationPreview()
    return
  }
  const titles: Record<string, string> = {
    'dry-run': '预检 (snakemake --dry-run,只读)',
    'unlock': '解锁 (snakemake --unlock)',
    'delete-temp-output': '清理临时文件 (--delete-temp-output)',
    'touch': '触碰时间戳 (--touch)',
    'cleanup-metadata': '清理元数据 (--cleanup-metadata)'
  }
  const confirms: Record<string, string> = {
    'unlock': `将对分析"${maintainRow.value.name}"执行解锁(--unlock)。确认执行?`,
    'delete-temp-output': `将清理分析"${maintainRow.value.name}"输出目录中不再需要的临时文件。确认执行?`,
    'touch': `将触碰分析"${maintainRow.value.name}"输出文件的时间戳(--touch,可能导致下游重跑)。确认执行?`,
    'cleanup-metadata': `将清理分析"${maintainRow.value.name}"指定文件的 snakemake 元数据。确认执行?`
  }
  if (mode === 'cleanup-metadata' && !maintainExtra.value.trim()) {
    ElMessage.warning('请填写文件路径')
    return
  }
  if (confirms[mode]) {
    try {
      await ElMessageBox.confirm(confirms[mode], '维护操作确认',
        { type: 'warning', confirmButtonText: '执行', cancelButtonText: '取消' })
    } catch { return }
  }
  maintainRunning.value = true
  maintainTitle.value = titles[mode] || mode
  maintainOutput.value = '执行中…'
  try {
    const out = await maintainPipeline(maintainRow.value.id!, mode, maintainExtra.value.trim())
    maintainOutput.value = typeof out === 'string' && out.trim() ? out : '(无输出)'
    ElMessage.success('维护操作完成')
    loadAnalyses()
    loadExecutionStatuses()
  } catch (e) {
    maintainOutput.value = '执行失败: ' + (e instanceof Error ? e.message : String(e))
    ElMessage.error('维护操作失败')
  } finally {
    maintainRunning.value = false
  }
}

const loadExecutionStatuses = async () => {
  // keep-alive 下切走页面 projectId 会变 NaN(route.params.id 消失),守卫掉避免每 10s 打一个 500
  if (!Number.isFinite(projectId.value)) return
  try {
    const res = await listExecutions({ projectId: projectId.value, size: 500 })
    const map: Record<number, Execution> = {}
    for (const ex of res.records || []) {
      const cur = map[ex.pipelineId]
      if (!cur || ex.id > cur.id) map[ex.pipelineId] = ex
    }
    latestExecutions.value = map
  } catch { /* 状态列加载失败不影响主流程 */ }
}

const statusLabel = (s: string) =>
  ({ PENDING: '排队中', RUNNING: '运行中', SUCCESS: '成功', FAILED: '失败', CANCELLED: '已取消' } as Record<string, string>)[s] || s
const statusTagType = (s: string) =>
  ({ PENDING: 'info', RUNNING: 'warning', SUCCESS: 'success', FAILED: 'danger', CANCELLED: 'info' } as Record<string, string>)[s] || 'info'
const isRunning = (row: Pipeline) => {
  const ex = latestExecutions.value[row.id]
  return !!ex && (ex.status === 'RUNNING' || ex.status === 'PENDING')
}

const executeDialogVisible = ref(false)
const executeLoading = ref(false)
const executingPipeline = ref<Pipeline | null>(null)
const inputParamRows = ref<{ key: string; value: string }[]>([])
const executeForcerun = ref('')
const metaFileSuggestions = ref<string[]>([])

const handleExecute = async (row: Pipeline) => {
  executingPipeline.value = row
  // 从模板命令模板解析 {{inputParams.*}} 必填项
  let cmd = ''
  let snakemakePath = ''
  try {
    const tpl = await getTemplate(row.templateId)
    cmd = tpl.commandTemplate || ''
    snakemakePath = tpl.snakemakePath || ''
  } catch { /* 模板拉不到则按无执行参数处理 */ }
  if (!cmd && !snakemakePath) {
    ElMessage.warning('该模板未配置 command_template 且无 snakemake_path，无法执行。请先在系统管理→分析模板中配置')
    return
  }
  const keys = [...cmd.matchAll(/\{\{\s*inputParams\.([^}]+?)\s*\}\}/g)].map(m => m[1])
  inputParamRows.value = [...new Set(keys)].map(k => ({ key: k, value: '' }))
  // meta TSV 中的文件路径作为候选值
  const files = new Set<string>()
  for (const line of (row.metaContent || '').split('\n').slice(1)) {
    for (const cell of line.split('\t')) {
      const v = cell.trim()
      if (v && (v.includes('/') || /\.(fastq|fq|gz|bam|vcf|cram|tsv|txt)(\.|$)/i.test(v))) files.add(v)
    }
  }
  metaFileSuggestions.value = [...files]
  executeDialogVisible.value = true
}

const confirmExecute = async () => {
  if (!executingPipeline.value) return
  const missing = inputParamRows.value.filter(p => !p.value.trim())
  if (missing.length) {
    ElMessage.warning('请填写执行参数: ' + missing.map(m => m.key).join(', '))
    return
  }
  executeLoading.value = true
  try {
    const paramsMap: Record<string, string> = {}
    for (const p of inputParamRows.value) paramsMap[p.key] = p.value.trim()
    // 运行参数(2026-11-25):--forcerun 保留键进 inputParams,记录可见;
    // 后端逐 token 白名单校验后以 run.py 原生参数拼接
    const forcerun = executeForcerun.value.trim()
    if (forcerun) paramsMap._forcerun = forcerun
    const execution = await executePipeline(executingPipeline.value.id, {
      projectId: executingPipeline.value.projectId,
      inputParams: JSON.stringify(paramsMap)
    }) as any
    ElMessage.success('执行已提交 (#' + (execution?.id ?? '') + ')')
    executeDialogVisible.value = false
    loadExecutionStatuses()
  } catch (e: any) {
    ElMessage.error(e?.message || '执行启动失败')
  } finally {
    executeLoading.value = false
  }
}

const handleCancel = async (row: Pipeline) => {
  const ex = latestExecutions.value[row.id]
  if (!ex) return
  try {
    await ElMessageBox.confirm(`确定取消执行 #${ex.id} 吗？`, '提示', { type: 'warning' })
    await cancelExecution(ex.id)
    ElMessage.success('已请求取消')
    loadExecutionStatuses()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('取消失败')
  }
}

const versionTip = (row: any) => {
  const parts: string[] = []
  if (row.latestWorkflowVersion) {
    const src = row.latestVersionSource === 'git' ? '派发采集' : row.latestVersionSource === 'inferred' ? '历史推断' : '未知'
    parts.push(`最近执行版本: ${row.latestWorkflowVersion}（来源:${src}）`)
  } else {
    parts.push('尚无执行记录')
  }
  if (row.templateWorkflowVersion) parts.push(`模板导入版本: ${row.templateWorkflowVersion}`)
  return parts.join('\n')
}

const fileRefSummary = (row: SampleMeta) => {
  return metaFileRefSummary[row.id] || { matched: 0, total: 0 }
}

const fileRefText = (row: SampleMeta) => {
  const s = metaFileRefSummary[row.id]
  return !s || s.total === 0 ? '-' : `${s.matched}/${s.total}`
}

const fileRefTagType = (row: SampleMeta): any => {
  const s = metaFileRefSummary[row.id]
  if (!s || s.total === 0) return 'info'
  if (s.lost && s.lost > 0) return 'danger'
  return s.matched === s.total ? 'success' : 'warning'
}

const fileRefTip = (row: SampleMeta) => {
  const s = metaFileRefSummary[row.id]
  if (!s) return '文件引用状态获取中…(点击看明细)'
  if (s.total === 0) return '未解析到文件引用(检查 TSV 中的文件列)'
  const lost = s.lost || 0
  const unmatched = s.total - s.matched
  return `已入库 ${s.matched}/${s.total}` + (unmatched ? `,${unmatched} 个未入库` : '') + (lost ? `,其中 ${lost} 个磁盘丢失` : '')
}

// 打开文件引用明细（每次进入都请求，保证与最新 meta 内容一致）
const openFileRefs = async (row: SampleMeta) => {
  fileRefsMetaId.value = row.id
  fileRefsMetaName.value = row.name
  fileRefsVisible.value = true
  fileRefsLoading.value = true
  try {
    const res = await getMetaFiles(row.id) as any
    fileRefs.value = res.refs || []
    fileRefsSummary.value = res
    metaFileRefSummary[row.id] = {
      matched: res.matchedRefs || 0,
      total: res.totalRefs || 0,
      lost: (res.refs || []).filter((r: any) => r.fileStatus === 0).length
    }
  } catch (e) {
    ElMessage.error('解析文件引用失败')
  } finally {
    fileRefsLoading.value = false
  }
}

const registerableCount = computed(() => {
  return fileRefs.value.filter(r => !r.matched && r.onDisk).length
})

// 登记未入库引用（一键/单条都走同一接口，登记全部可登记项）
const handleRegisterMissing = async () => {
  if (!fileRefsMetaId.value) return
  registerLoading.value = true
  try {
    const res = await registerMissingMetaFiles(fileRefsMetaId.value) as any
    ElMessage.success(`已登记 ${res.count} 个文件`)
    // 重新解析并刷新样本列表
    const row = metaList.value.find(m => m.id === fileRefsMetaId.value)
    if (row) await openFileRefs(row)
  } catch (e) {
    ElMessage.error('登记失败')
  } finally {
    registerLoading.value = false
  }
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
  // 检查多空格分隔（两个或以上连续空格）
  const spaceCount = line.split(/\s{2,}/).length - 1
  if (spaceCount > maxCount) {
    return '__spaces__'
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
  const splitLine = (line: string) => {
    if (delimiter === '__spaces__') {
      return line.split(/\s{2,}/).map(s => s.trim()).filter(s => s !== '')
    }
    return line.split(delimiter).map(s => s.trim())
  }
  metaColumns.value = splitLine(lines[0])
  metaRows.value = []
  for (let i = 1; i < lines.length; i++) {
    const cells = splitLine(lines[i])
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
  metaTsvContent.value = row.metaContent || ''
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
  metaColumns.value.push('new_col')
  metaRows.value.forEach(row => row.push(''))
  metaColWidths.value.push(180)
}

const removeColumn = (index: number) => {
  metaColumns.value.splice(index, 1)
  metaRows.value.forEach(row => row.splice(index, 1))
  metaColWidths.value.splice(index, 1)
}

// 列宽拖拽(自绘表格:th 右缘手柄,拖动改列宽)
const startColResize = (ci: number, e: MouseEvent) => {
  const startX = e.clientX
  const startW = metaColWidths.value[ci] || 180
  const onMove = (ev: MouseEvent) => {
    metaColWidths.value[ci] = Math.max(80, startW + ev.clientX - startX)
  }
  const onUp = () => {
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
  }
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
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
      projectId: projectId.value,
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

const handleMetaFileUpload = (file: File) => {
  // 读取文件内容
  const reader = new FileReader()
  reader.onload = (e) => {
    const content = e.target?.result as string
    if (!content || !content.trim()) {
      ElMessage.warning('文件内容为空')
      return
    }
    importTsvContent.value = content.trim()
    importedFileName.value = file.name
    // 自动用文件名（去掉扩展名）作为 Meta 名称
    if (!importTsvName.value) {
      importTsvName.value = file.name.replace(/\.(tsv|csv|txt)$/i, '')
    }
    importTsvDialogVisible.value = true
  }
  reader.onerror = () => {
    ElMessage.error('文件读取失败')
  }
  reader.readAsText(file)
  return false // 阻止 el-upload 自动上传
}

const resetImportFile = () => {
  importTsvContent.value = ''
  importedFileName.value = ''
  importTsvName.value = ''
}

const handleConfirmImportTsv = async () => {
  if (!importTsvContent.value.trim()) {
    ElMessage.warning('请先上传文件')
    return
  }
  try {
    await createSampleMeta({
      projectId: projectId.value,
      name: importTsvName.value || 'meta_input',
      metaMode: 'fastq',
      metaContent: importTsvContent.value.trim(),
    })
    ElMessage.success('导入成功')
    importTsvDialogVisible.value = false
    resetImportFile()
    loadMetaList()
  } catch (e) {
    ElMessage.error('导入失败')
  }
}

const handleExportPpt = async () => {
  pptLoading.value = true
  try {
    // 浏览器原生下载接管（有进度条与保存步骤），文件名由服务端 Content-Disposition 决定
    await exportPpt(projectId.value)
  } catch (e: any) {
    ElMessage.error(e?.message || '导出PPT失败')
  } finally { pptLoading.value = false }
}

const handleExportExcel = async () => {
  excelLoading.value = true
  try {
    await exportExcel(projectId.value)
  } catch (e: any) {
    ElMessage.error(e?.message || '导出Excel失败')
  } finally { excelLoading.value = false }
}

const handleDownloadAll = async () => {
  downloadAllLoading.value = true
  try {
    await downloadAll(projectId.value)
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
    await batchDownload(projectId.value, paths)
  } catch (e: any) {
    ElMessage.error(e?.message || '下载失败')
  } finally { batchDownloadLoading.value = false }
}

// --- File upload ---
const handleUpload = async (file: File) => {
  try {
    await uploadFile(file, projectId.value)
    ElMessage.success('上传成功')
    loadFiles()
  } catch (e) {
    ElMessage.error('上传失败')
  }
  return false // prevent el-upload default behavior
}

const handleDeleteFile = async (row: DataFile) => {
  try {
    await ElMessageBox.confirm(`确定要删除文件"${row.name}"吗？`, '提示', { type: 'warning' })
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
    const res = await importLocalFiles(importDirPath.value, projectId.value)
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
  const overrides = builtExtraParams.value
  submitLoading.value = true
  try {
    await createAnalysis(projectId.value, {
      workflowTemplateName: selectedTemplateName.value,
      name: analysisName.value || undefined,
      metaContent: metaContent.value,
      metaType: metaType.value,
      designJson: designJson.value || undefined,
      extraParams: Object.keys(overrides).length ? JSON.stringify(overrides) : undefined,
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
  editBase.value = { ...row }
  editForm.id = row.id
  editForm.name = row.name
  editForm.metaType = row.metaType || 'text'
  editForm.metaContent = row.metaContent || ''
  editForm.designJson = row.designJson || ''
  editVisible.value = true
  loadMetaTemplate(row.templateId)
}

// 分析编辑:展示已有样本信息可一键填入 meta;参数覆盖等在流程管理维护
const editVisible = ref(false)
const editLoading = ref(false)
const editBase = ref<Pipeline | null>(null)
const editForm = reactive({
  id: 0,
  name: '',
  metaType: 'text',
  metaContent: '',
  designJson: ''
})

const fillEditFromSampleMeta = (sm: SampleMeta) => {
  editForm.metaType = sm.metaMode === 'path' ? 'path' : 'text'
  editForm.metaContent = sm.metaContent || ''
  editForm.designJson = sm.designJson || ''
  const n = countDesignComparisons(editForm.designJson)
  ElMessage.success(`已填入「${sm.name}」` + (n ? `,含 ${n} 个实验对比` : ''))
}

// --- 工作流 meta 规范(Omics/assests/test/meta_<流程>.tsv,各流程列固定) ---
const metaTpl = ref<MetaTemplate | null>(null)
const loadMetaTemplate = async (templateId: number) => {
  metaTpl.value = null
  if (!templateId) return
  try {
    metaTpl.value = await getMetaTemplate(templateId)
  } catch {
    metaTpl.value = null
  }
}
watch(selectedTemplateName, (n) => {
  const tpl = templateList.value.find(t => t.name === n)
  if (tpl) loadMetaTemplate(tpl.id)
})

const metaTplError = (meta: string): string | null => {
  const tpl = metaTpl.value
  if (!tpl || !tpl.available) return null
  const lines = (meta || '').trim().split('\n').filter(Boolean)
  if (!lines.length) return 'Meta TSV 为空'
  const header = lines[0].split('\t').map(s => s.trim())
  const missing = tpl.columns.filter(c => !header.includes(c))
  const extra = header.filter(c => !tpl.columns.includes(c))
  if (missing.length) return `Meta TSV 缺少必需列: ${missing.join('、')}（该流程的列是固定的）`
  if (extra.length) return `Meta TSV 含该流程之外的列: ${extra.join('、')}`
  const orgIdx = header.indexOf('organism')
  if (orgIdx >= 0) {
    for (let i = 1; i < lines.length; i++) {
      const cells = lines[i].split('\t')
      if (!(cells[orgIdx] || '').trim()) {
        return `第 ${i + 1} 行 organism 为空（应为 GRCh38 / GRCm39 / Mmul_10）`
      }
    }
  }
  return null
}

const fillCreateFromMetaTpl = () => {
  metaContent.value = (metaTpl.value?.filled || '') + '\n'
}
const fillEditFromMetaTpl = () => {
  editForm.metaContent = (metaTpl.value?.filled || '') + '\n'
}

const gotoStep2 = () => {
  if (metaType.value === 'text') {
    const err = metaTplError(metaContent.value)
    if (err) {
      ElMessage.error(err)
      return
    }
  }
  createStep.value = 2
}

const handleSaveEdit = async () => {
  if (editForm.metaType === 'text') {
    const err = metaTplError(editForm.metaContent)
    if (err) {
      ElMessage.error(err)
      return
    }
  }
  editLoading.value = true
  try {
    // 更新接口是全量覆盖,必须把行内其余字段(含 extraParams)原样带上,否则被写空
    await updatePipeline(editForm.id, {
      ...(editBase.value as any),
      name: editForm.name,
      metaType: editForm.metaType,
      metaContent: editForm.metaContent,
      designJson: editForm.designJson
    } as any)
    ElMessage.success('已保存')
    editVisible.value = false
    loadAnalyses()
    loadExecutionStatuses()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    editLoading.value = false
  }
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
  loadChildProjects()
  loadExecutionStatuses()
  execPollTimer = setInterval(loadExecutionStatuses, 10000)
})

onUnmounted(() => {
  if (execPollTimer) clearInterval(execPollTimer)
})

// keep-alive:切走页面挂起轮询(组件不卸载),回来再恢复
onActivated(() => {
  if (!execPollTimer) execPollTimer = setInterval(loadExecutionStatuses, 10000)
  loadExecutionStatuses()
})
onDeactivated(() => {
  if (execPollTimer) {
    clearInterval(execPollTimer)
    execPollTimer = null
  }
})

// 监听路由参数变化，组件复用时重新加载数据
watch(() => route.params.id, (newId, oldId) => {
  if (newId && newId !== oldId) {
    pagination.page = 1
    filePagination.page = 1
    loadProject()
    loadFiles()
    loadAnalyses()
    loadFileTree()
    loadMetaList()
    loadChildProjects()
  }
})

// 切换tab时同步数据
watch(metaEditTab, (newTab, oldTab) => {
  if (newTab === oldTab) return
  if (newTab === 'tsv') {
    // 切到文本编辑：表格→文本
    metaTsvContent.value = tableToTsv()
  } else if (newTab === 'table') {
    // 切到表格编辑：文本→表格
    if (metaTsvContent.value.trim()) {
      tsvToTable(metaTsvContent.value)
    }
  }
})

// 文本编辑实时同步到表格（防抖）
let tsvSyncTimer: ReturnType<typeof setTimeout> | null = null
watch(metaTsvContent, (val) => {
  if (metaEditTab.value !== 'tsv') return
  if (tsvSyncTimer) clearTimeout(tsvSyncTimer)
  tsvSyncTimer = setTimeout(() => {
    if (val.trim()) {
      tsvToTable(val)
    }
  }, 300)
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

.template-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.template-title-line {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.existing-samples {
  width: 100%;
  max-height: 180px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 8px;
}

.existing-samples-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
}

.existing-sample-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 4px 2px;
  border-bottom: 1px solid var(--el-fill-color-light);
}

.existing-sample-item:last-child {
  border-bottom: none;
}

.existing-sample-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.existing-sample-preview {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-family: monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.param-override-panel {
  width: 100%;
}

.param-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
  margin-bottom: 8px;
}

.param-default {
  font-family: monospace;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.custom-param-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.param-preview {
  font-family: monospace;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  word-break: break-all;
}

.meta-tpl-panel {
  border: 1px solid var(--el-color-primary-light-6);
  background: var(--el-color-primary-light-9);
  border-radius: 6px;
  padding: 8px 10px;
  margin-bottom: 12px;
}

.meta-tpl-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.meta-tpl-example {
  margin: 6px 0 0;
  font-size: 12px;
  font-family: monospace;
  max-height: 110px;
  overflow: auto;
  white-space: pre;
  color: var(--el-text-color-secondary);
}

.exec-hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
  margin: 0 0 12px;
}

.exec-progress {
  margin-left: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
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
  table-layout: fixed;
  width: max-content;
  font-size: 13px;
}

.meta-table th,
.meta-table td {
  border: 1px solid #ebeef5;
  padding: 4px 8px;
  white-space: nowrap;
}

.meta-table td {
  overflow: hidden;
}

.meta-table th {
  background: #f5f7fa;
  font-weight: 600;
  position: sticky;
  top: 0;
  z-index: 1;
}

.col-resizer {
  position: absolute;
  right: 0;
  top: 0;
  bottom: 0;
  width: 6px;
  cursor: col-resize;
  user-select: none;
  z-index: 2;
}

.col-resizer:hover {
  background: var(--el-color-primary-light-5);
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

.meta-header-input {
  border: 1px solid transparent;
  outline: none;
  background: transparent;
  font-size: 13px;
  font-weight: 600;
  width: 100%;
  min-width: 60px;
  padding: 2px 4px;
  border-radius: 3px;
}

.meta-header-input:hover {
  border-color: #c0c4cc;
}

.meta-header-input:focus {
  border-color: #409eff;
  background: #fff;
}

.meta-header-input:disabled {
  color: #606266;
  cursor: not-allowed;
}
/* --- 实验设计弹窗(2026-11-26) --- */
.design-sub-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin: 8px 0 6px;
}
.design-seq {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 4px 8px;
  background: #fafcff;
}
.design-seq-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 2px;
}
.design-seq-row + .design-seq-row {
  border-top: 1px dashed #ebeef5;
}
.design-seq-no {
  flex: none;
  width: 18px;
  height: 18px;
  line-height: 18px;
  text-align: center;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  font-size: 11px;
}
.design-seq-name {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.design-seq-ops {
  flex: none;
  display: flex;
  gap: 2px;
}
.design-seq-empty {
  padding: 4px 2px;
  font-size: 12px;
  color: #909399;
}
</style>
