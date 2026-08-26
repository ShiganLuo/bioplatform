<template>
  <div class="docs-view">
    <div class="page-header">
      <h1 class="page-title">使用文档</h1>
      <p class="page-desc">生信云平台使用指南，帮助您快速上手各项功能</p>
    </div>

    <div class="docs-content">
      <el-collapse v-model="activeNames">
        <el-collapse-item title="🚀 快速开始" name="quickstart">
          <div class="doc-section">
            <h4>1. 注册与登录</h4>
            <p>点击页面右上角的"登录"按钮，输入用户名和密码完成登录。新用户请联系管理员开通账号。</p>
            <h4>2. 创建项目</h4>
            <p>进入"项目管理"页面，点击"新建项目"，填写项目名称、物种、基因组版本等信息。项目是组织分析流程的基本单位。</p>
            <h4>3. 发起分析</h4>
            <p>在项目详情页中，选择合适的分析流程模板，上传数据文件，配置参数后即可发起分析。</p>
          </div>
        </el-collapse-item>

        <el-collapse-item title="📁 项目管理" name="projects">
          <div class="doc-section">
            <h4>项目概念</h4>
            <p>项目是平台的核心组织单元，每个项目可以包含多个分析流程（Pipeline）。项目支持设置公开/私有状态。</p>
            <h4>创建项目</h4>
            <p>填写以下信息：</p>
            <ul>
              <li><strong>项目名称</strong>：简洁明了的项目标识</li>
              <li><strong>物种</strong>：如 Homo sapiens、Mus musculus 等</li>
              <li><strong>基因组版本</strong>：如 hg38、mm10、GRCh38 等</li>
              <li><strong>描述</strong>：项目的详细说明</li>
            </ul>
            <h4>项目权限</h4>
            <p>公开项目对所有用户可见，私有项目仅创建者可见。管理员可以管理所有项目。</p>
          </div>
        </el-collapse-item>

        <el-collapse-item title="⚙️ 分析流程" name="pipelines">
          <div class="doc-section">
            <h4>流程模板</h4>
            <p>平台内置多种标准化分析流程模板，包括 RNA-seq、ChIP-seq、全基因组测序（WGS）等。管理员可以在"流程模板"中管理模板。</p>
            <h4>发起分析</h4>
            <ol>
              <li>选择流程模板</li>
              <li>上传输入数据（支持 FASTQ、BAM、VCF 等格式）</li>
              <li>配置分析参数</li>
              <li>提交执行</li>
            </ol>
            <h4>查看结果</h4>
            <p>在"执行监控"页面查看分析进度和结果。分析完成后可下载结果文件。</p>
          </div>
        </el-collapse-item>

        <el-collapse-item title="🤖 AI 助手" name="agent">
          <div class="doc-section">
            <h4>功能介绍</h4>
            <p>AI 助手基于大语言模型，可以回答生物信息学相关问题、提供分析建议、解释结果等。</p>
            <h4>使用方式</h4>
            <ul>
              <li>进入"AI 助手"页面</li>
              <li>在输入框中输入问题</li>
              <li>支持多轮对话，AI 会记住上下文</li>
              <li>可以创建多个独立对话</li>
            </ul>
            <h4>注意事项</h4>
            <p>AI 生成的内容仅供参考，实际分析请以数据和文献为准。建议将 AI 建议作为起点，结合专业知识进行验证。</p>
          </div>
        </el-collapse-item>

        <el-collapse-item title="📊 数据管理" name="data">
          <div class="doc-section">
            <h4>文件上传</h4>
            <p>支持拖拽上传和点击上传，支持大文件分片上传和断点续传。单文件最大支持 1GB。</p>
            <h4>支持格式</h4>
            <ul>
              <li><strong>序列数据</strong>：FASTQ、FASTA</li>
              <li><strong>比对数据</strong>：BAM、SAM</li>
              <li><strong>变异数据</strong>：VCF、BCF</li>
              <li><strong>注释数据</strong>：BED、GFF、GTF</li>
              <li><strong>表格数据</strong>：TSV、CSV</li>
            </ul>
            <h4>存储配额</h4>
            <p>每个用户有存储空间配额限制，可在个人设置中查看已用空间。</p>
          </div>
        </el-collapse-item>
      </el-collapse>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const activeNames = ref(['quickstart'])
</script>

<style scoped>
.docs-view {
  padding: 20px 0;
}

.page-header {
  text-align: center;
  margin-bottom: 40px;
}

.page-title {
  font-size: 32px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 8px;
}

.page-desc {
  font-size: 15px;
  color: #909399;
}

.docs-content {
  max-width: 800px;
  margin: 0 auto;
}

.doc-section {
  padding: 8px 0;
  line-height: 1.8;
  color: #606266;
}

.doc-section h4 {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin: 16px 0 8px;
}

.doc-section h4:first-child {
  margin-top: 0;
}

.doc-section ul,
.doc-section ol {
  padding-left: 20px;
  margin: 8px 0;
}

.doc-section li {
  margin: 4px 0;
}
</style>
