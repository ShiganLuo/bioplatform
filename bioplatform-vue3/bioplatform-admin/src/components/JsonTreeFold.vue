<template>
  <div class="json-tree-fold">
    <div class="json-tree-toolbar">
      <el-button size="small" @click="setAll(true)">全部展开</el-button>
      <el-button size="small" @click="setAll(false)">全部折叠</el-button>
      <span class="json-tree-hint">点 ▸/▾ 按层级折叠;树形只读,修改请切回原文编辑</span>
    </div>
    <div v-if="!String(modelValue || '').trim()" class="json-tree-error">（空内容）</div>
    <div v-else-if="parsed.ok" class="json-tree-body" :style="{ maxHeight: maxHeight || '480px' }">
      <JsonTreeNode :value="parsed.value" :depth="0" path="$" :label="null" />
    </div>
    <div v-else class="json-tree-error">JSON 解析失败: {{ parsed.error }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed, provide, reactive, watch } from 'vue'
import JsonTreeNode from './JsonTreeNode.vue'

const props = defineProps<{ modelValue: string; maxHeight?: string }>()

const parsed = computed(() => {
  try {
    return { ok: true as const, value: JSON.parse(props.modelValue || 'null') }
  } catch (e: any) {
    return { ok: false as const, error: String(e?.message || e) }
  }
})

// 展开状态按路径管理;默认只展开前两层,大 JSON 才有折叠价值
const expanded = reactive(new Set<string>())
const allPaths: string[] = []

const collect = (v: unknown, path: string, depth: number) => {
  if (v !== null && typeof v === 'object') {
    allPaths.push(path)
    if (depth <= 1) expanded.add(path)
    for (const [k, child] of Object.entries(v)) {
      collect(child, path + (Array.isArray(v) ? `[${k}]` : `.${k}`), depth + 1)
    }
  }
}

watch(parsed, (p) => {
  expanded.clear()
  allPaths.length = 0
  if (p.ok) collect(p.value, '$', 0)
}, { immediate: true })

const toggle = (path: string) => {
  if (expanded.has(path)) expanded.delete(path)
  else expanded.add(path)
}

const setAll = (open: boolean) => {
  expanded.clear()
  if (open) for (const path of allPaths) expanded.add(path)
}

provide('jsonTreeState', { expanded, toggle })
</script>

<style scoped>
.json-tree-toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}
.json-tree-hint {
  font-size: 12px;
  color: #909399;
}
.json-tree-body {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 8px 10px;
  overflow: auto;
  background: #fafafa;
  font-family: 'Cascadia Code', 'Fira Code', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.6;
}
.json-tree-error {
  padding: 10px;
  color: #f56c6c;
  font-size: 12px;
  border: 1px dashed #f56c6c;
  border-radius: 4px;
}
</style>
