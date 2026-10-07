<template>
  <el-dialog v-model="visible" title="编辑文件" width="520px" :close-on-click-modal="false">
    <el-form :model="form" label-width="90px">
      <el-form-item label="文件名" required>
        <el-input v-model="form.name" placeholder="显示名与下载名，不重命名物理文件" />
      </el-form-item>
      <el-form-item label="物种">
        <el-input v-model="form.organism" placeholder="如 Homo sapiens（留空则清除）" clearable />
      </el-form-item>
      <el-form-item label="基因组版本">
        <el-input v-model="form.genomeVersion" placeholder="如 GRCh38（留空则清除）" clearable />
      </el-form-item>
      <el-form-item label="所属项目" required>
        <el-select
          v-model="form.projectIds"
          multiple
          filterable
          placeholder="至少选择一个项目（多选）"
          style="width: 100%"
        >
          <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { updateFile } from '@/api/dataFileApi'
import type { DataFile } from '@/api/dataFileApi'
import type { Project } from '@/api/projectApi'

defineProps<{ projects: Project[] }>()

const visible = defineModel<boolean>('visible', { default: false })
const emit = defineEmits<{ saved: [] }>()

const saving = ref(false)
const file = ref<DataFile | null>(null)
const form = reactive({
  name: '',
  organism: '',
  genomeVersion: '',
  projectIds: [] as number[]
})

// 打开时用当前行回显（projectIds 为后端逗号分隔字符串）
function open(row: DataFile) {
  file.value = row
  form.name = row.name
  form.organism = row.organism || ''
  form.genomeVersion = row.genomeVersion || ''
  form.projectIds = row.projectIds
    ? row.projectIds.split(',').filter(Boolean).map(Number)
    : []
  visible.value = true
}

defineExpose({ open })

watch(visible, v => {
  if (!v) file.value = null
})

async function save() {
  if (!file.value) return
  if (!form.name.trim()) {
    ElMessage.warning('文件名不能为空')
    return
  }
  if (!form.projectIds.length) {
    ElMessage.warning('所属项目至少选择一个')
    return
  }
  saving.value = true
  try {
    await updateFile(file.value.id, {
      name: form.name.trim(),
      organism: form.organism,
      genomeVersion: form.genomeVersion,
      projectIds: form.projectIds
    })
    ElMessage.success('保存成功')
    visible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>
