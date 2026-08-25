<template>
  <div class="schema-form">
    <template v-for="(fieldSchema, fieldName) in schema" :key="fieldName">
      <!-- 跳过纯 null 类型且没有 properties 的系统字段 -->
      <SchemaFormItem
        v-if="shouldRender(fieldSchema)"
        :name="String(fieldName)"
        :schema="fieldSchema"
        :model-value="modelValue[String(fieldName)]"
        @update:model-value="updateField(fieldName, $event)"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import SchemaFormItem from './SchemaFormItem.vue'

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
  schema: Record<string, FieldSchema>
  modelValue: Record<string, any>
}>()

const emit = defineEmits<{
  'update:model-value': [value: Record<string, any>]
}>()

// 跳过纯系统字段（ROOT_DIR、indir、outdir、logdir、raw_files 这些由系统自动填充）
const SYSTEM_FIELDS = new Set(['ROOT_DIR', 'indir', 'outdir', 'logdir', 'raw_files', 'outfiles'])

function shouldRender(fieldSchema: FieldSchema): boolean {
  // 有 properties 的 dict 一定要渲染（即使 type 是 dict/null）
  if (fieldSchema.properties && Object.keys(fieldSchema.properties).length > 0) return true
  // 纯 null 类型跳过
  if (fieldSchema.type === 'null') return false
  return true
}

function updateField(fieldName: string | number, value: any) {
  const newData = { ...props.modelValue }
  newData[String(fieldName)] = value
  emit('update:model-value', newData)
}
</script>

<style scoped>
.schema-form {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
</style>
