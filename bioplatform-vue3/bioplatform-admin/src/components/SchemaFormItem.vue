<template>
  <el-form-item
    :label="name"
    :required="schema.required"
    class="schema-form-item"
  >
    <template #label>
      <span>{{ name }}</span>
      <el-tooltip v-if="schema.description" :content="schema.description" placement="top">
        <el-icon class="field-help"><QuestionFilled /></el-icon>
      </el-tooltip>
    </template>

    <!-- dict with properties → 嵌套折叠面板 -->
    <el-collapse v-if="isDict" class="nested-collapse">
      <el-collapse-item :title="name">
        <SchemaFormItem
          v-for="(childSchema, childName) in schema.properties"
          :key="childName"
          :name="String(childName)"
          :schema="childSchema"
          :model-value="(modelValue || {})[String(childName)]"
          @update:model-value="updateNested(String(childName), $event)"
        />
      </el-collapse-item>
    </el-collapse>

    <!-- null type + nullable (如 Procedure 里的工具路径) → string 输入 -->
    <el-input
      v-else-if="isNullButInputtable"
      :model-value="modelValue ?? ''"
      :placeholder="schema.description || '留空使用默认值'"
      clearable
      @update:model-value="emit('update:model-value', $event || null)"
    />

    <!-- boolean → switch -->
    <el-switch
      v-else-if="isBool"
      :model-value="modelValue ?? schema.default ?? false"
      @update:model-value="emit('update:model-value', $event)"
    />

    <!-- integer / number → input-number -->
    <el-input-number
      v-else-if="isNumber"
      :model-value="modelValue ?? schema.default"
      :min="0"
      :controls="true"
      @update:model-value="emit('update:model-value', $event)"
    />

    <!-- list → 动态标签 -->
    <div v-else-if="isList" class="list-input">
      <div class="list-tags">
        <el-tag
          v-for="(item, i) in (modelValue || [])"
          :key="i"
          closable
          size="small"
          @close="removeListItem(i)"
        >
          {{ item }}
        </el-tag>
      </div>
      <div style="display: flex; gap: 4px; margin-top: 4px">
        <el-input
          v-model="newListItem"
          size="small"
          placeholder="输入后回车添加"
          style="width: 240px"
          @keyup.enter="addListItem"
        />
        <el-button size="small" :icon="Plus" @click="addListItem" />
      </div>
    </div>

    <!-- string with path=file / prefix -->
    <el-input
      v-else-if="schema.path === 'file' || schema.path === 'prefix'"
      :model-value="modelValue"
      :placeholder="schema.description || '请输入文件路径'"
      @update:model-value="emit('update:model-value', $event)"
    >
      <template #prepend><el-icon><Document /></el-icon></template>
    </el-input>

    <!-- string with path=dir -->
    <el-input
      v-else-if="schema.path === 'dir'"
      :model-value="modelValue"
      :placeholder="schema.description || '请输入目录路径'"
      @update:model-value="emit('update:model-value', $event)"
    >
      <template #prepend><el-icon><FolderOpened /></el-icon></template>
    </el-input>

    <!-- string → plain input -->
    <el-input
      v-else
      :model-value="modelValue"
      :placeholder="schema.description || ''"
      @update:model-value="emit('update:model-value', $event)"
    />
  </el-form-item>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { QuestionFilled, Plus, Document, FolderOpened } from '@element-plus/icons-vue'

interface FieldSchema {
  type: string
  required?: boolean
  nullable?: boolean
  description?: string
  path?: string
  properties?: Record<string, FieldSchema>
  default?: any
}

const props = defineProps<{
  name: string
  schema: FieldSchema
  modelValue: any
}>()

const emit = defineEmits<{
  'update:model-value': [value: any]
}>()

// Type detection — Omics uses 'str'/'int'/'bool'/'dict'/'list'/'null'
const isDict = computed(() => props.schema.type === 'dict' && !!props.schema.properties)
const isBool = computed(() => props.schema.type === 'boolean' || props.schema.type === 'bool')
const isNumber = computed(() => ['integer', 'int', 'number', 'float'].includes(props.schema.type))
const isList = computed(() => props.schema.type === 'list')

// 关键：type=null 但 nullable=true 的字段（如 Procedure 里的工具路径），
// 实际上是可选的 string 输入——留空表示 null，填写表示覆盖默认值
const isNullButInputtable = computed(() =>
  props.schema.type === 'null' && props.schema.nullable === true
)

const newListItem = ref('')

function addListItem() {
  if (!newListItem.value.trim()) return
  const current = Array.isArray(props.modelValue) ? [...props.modelValue] : []
  current.push(newListItem.value.trim())
  emit('update:model-value', current)
  newListItem.value = ''
}

function removeListItem(index: number) {
  const current = Array.isArray(props.modelValue) ? [...props.modelValue] : []
  current.splice(index, 1)
  emit('update:model-value', current)
}

function updateNested(childName: string, value: any) {
  const current = { ...(props.modelValue || {}) }
  current[childName] = value
  emit('update:model-value', current)
}
</script>

<script lang="ts">
export default { name: 'SchemaFormItem' }
</script>

<style scoped>
.schema-form-item {
  margin-bottom: 8px;
}
.field-help {
  margin-left: 4px;
  font-size: 14px;
  color: #909399;
  cursor: help;
}
.nested-collapse {
  width: 100%;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}
.nested-collapse :deep(.el-collapse-item__header) {
  background: #f5f7fa;
  padding: 0 12px;
  font-size: 13px;
  color: #606266;
  height: 36px;
  line-height: 36px;
}
.nested-collapse :deep(.el-collapse-item__content) {
  padding: 12px;
}
.list-input {
  width: 100%;
}
.list-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
</style>
