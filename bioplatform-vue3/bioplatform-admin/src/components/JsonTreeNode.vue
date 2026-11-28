<template>
  <div class="jtn">
    <!-- 容器节点:可折叠 -->
    <template v-if="isContainer">
      <div class="jtn-line" @click="state.toggle(path)">
        <span class="jtn-caret">{{ open ? '▾' : '▸' }}</span>
        <template v-if="label !== null">
          <span class="jtn-key">{{ label }}</span><span class="jtn-colon">: </span>
        </template>
        <span class="jtn-brace">{{ open ? (isArray ? '[' : '{') : (isArray ? `[ … ${valueLength} 项 ]` : `{ … ${keyCount} 键 }`) }}</span>
      </div>
      <template v-if="open">
        <JsonTreeNode
          v-for="(child, k) in (value as any)"
          :key="k"
          :label="isArray ? String(k) : String(k)"
          :value="child"
          :depth="depth + 1"
          :path="path + (isArray ? `[${k}]` : `.${k}`)"
        />
        <div class="jtn-line jtn-close">{{ isArray ? ']' : '}' }}</div>
      </template>
    </template>
    <!-- 叶子值 -->
    <div v-else class="jtn-line">
      <span class="jtn-caret jtn-leaf">·</span>
      <template v-if="label !== null">
        <span class="jtn-key">{{ label }}</span><span class="jtn-colon">: </span>
      </template>
      <span :class="valueClass">{{ displayValue }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, inject } from 'vue'

const props = defineProps<{ label: string | number | null; value: unknown; depth: number; path: string }>()

const state = inject<any>('jsonTreeState')

const isContainer = computed(() => props.value !== null && typeof props.value === 'object')
const isArray = computed(() => Array.isArray(props.value))
const keyCount = computed(() => (isContainer.value ? Object.keys(props.value as object).length : 0))
const valueLength = computed(() => (isArray.value ? (props.value as unknown[]).length : 0))

// 根节点($ )恒展开,其余按全局展开状态
const open = computed(() => props.depth === 0 || state.expanded.has(props.path))

const displayValue = computed(() => {
  if (props.value === null) return 'null'
  if (typeof props.value === 'string') return JSON.stringify(props.value)
  return String(props.value)
})
const valueClass = computed(() => {
  if (props.value === null) return 'jtn-null'
  if (typeof props.value === 'string') return 'jtn-string'
  if (typeof props.value === 'number') return 'jtn-number'
  if (typeof props.value === 'boolean') return 'jtn-bool'
  return ''
})
</script>

<style scoped>
.jtn-line {
  display: flex;
  align-items: baseline;
  cursor: default;
  white-space: pre;
}
.jtn-line:hover {
  background: #f0f2f5;
  border-radius: 3px;
}
.jtn-caret {
  display: inline-block;
  width: 16px;
  cursor: pointer;
  color: #606266;
  user-select: none;
}
.jtn-leaf {
  cursor: default;
  color: #c0c4cc;
}
.jtn-close {
  cursor: default;
  color: #606266;
}
.jtn-key {
  color: #303133;
}
.jtn-colon {
  color: #909399;
}
.jtn-brace {
  color: #606266;
  cursor: pointer;
}
.jtn-string {
  color: #67c23a;
}
.jtn-number {
  color: #409eff;
}
.jtn-bool {
  color: #b37feb;
}
.jtn-null {
  color: #909399;
}
.jtn > .jtn {
  padding-left: 18px;
  border-left: 1px dotted #dcdfe6;
  margin-left: 7px;
}
</style>
