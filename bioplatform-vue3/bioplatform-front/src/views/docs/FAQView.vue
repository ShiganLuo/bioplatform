<template>
  <div class="faq-view">
    <div class="page-header">
      <h1 class="page-title">常见问题</h1>
      <p class="page-desc">汇集用户最常遇到的问题及解答</p>
    </div>

    <div class="faq-content">
      <el-collapse v-model="activeNames">
        <el-collapse-item
          v-for="(item, index) in faqList"
          :key="index"
          :title="item.q"
          :name="String(index)"
        >
          <div class="faq-answer" v-html="item.a"></div>
        </el-collapse-item>
      </el-collapse>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const activeNames = ref<string[]>([])

const faqList = [
  {
    q: '平台支持哪些物种的分析？',
    a: '平台支持多种模式生物和非模式生物的分析，包括但不限于：人（Homo sapiens）、小鼠（Mus musculus）、大鼠（Rattus norvegicus）、果蝇（Drosophila melanogaster）、拟南芥（Arabidopsis thaliana）等。您可以在创建项目时指定物种和基因组版本，也支持自定义基因组。'
  },
  {
    q: '上传文件大小有限制吗？',
    a: '单文件最大支持 <strong>1GB</strong>。平台支持分片上传和断点续传，大文件上传过程中如遇网络中断，恢复后可继续上传，无需重新开始。'
  },
  {
    q: '分析任务运行多久？',
    a: '分析时长取决于数据量和流程复杂度。一般 RNA-seq 分析（6个样本）约需 2-4 小时，WGS 分析可能需要 6-12 小时。您可以在"执行监控"页面实时查看进度。'
  },
  {
    q: '如何查看和下载分析结果？',
    a: '分析完成后，在"执行监控"页面点击对应的执行记录，即可查看结果摘要和下载结果文件。结果文件通常包括统计表格、可视化图表和原始分析文件。'
  },
  {
    q: 'AI 助手的回答准确吗？',
    a: 'AI 助手基于大语言模型生成回答，内容仅供参考。建议将 AI 建议作为起点，结合实际数据和文献进行验证。AI 更适合用于解释概念、推荐工具和分析思路，具体的参数设置请参考官方文档。'
  },
  {
    q: '项目可以设置为私有吗？',
    a: '可以。创建项目时可以选择公开或私有状态。私有项目仅对创建者和管理员可见。您也可以在项目设置中随时修改可见性。'
  },
  {
    q: '如何联系技术支持？',
    a: '您可以通过以下方式联系我们：<br>1. 页面右下角的在线客服对话框<br>2. 发送邮件至平台联系邮箱<br>3. 在 GitHub 上提交 Issue'
  },
  {
    q: '支持自定义分析流程吗？',
    a: '目前平台内置了多种标准化分析流程。自定义流程功能正在开发中，届时用户可以上传自己的分析脚本或使用工作流描述语言（如 Nextflow、Snakemake）定义流程。'
  }
]
</script>

<style scoped>
.faq-view {
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

.faq-content {
  max-width: 800px;
  margin: 0 auto;
}

.faq-answer {
  padding: 8px 0;
  line-height: 1.8;
  color: #606266;
}
</style>
