<template>
  <div class="yaml-editor-wrapper" :style="{ height: height }">
    <codemirror
      :model-value="modelValue"
      :style="{ height: '100%' }"
      :extensions="extensions"
      :disabled="readonly"
      @update:model-value="$emit('update:modelValue', $event)"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Codemirror } from 'vue-codemirror'
import { yaml } from '@codemirror/lang-yaml'
import { oneDark } from '@codemirror/theme-one-dark'
import { EditorView } from 'codemirror'

const props = withDefaults(defineProps<{
  modelValue: string
  readonly?: boolean
  height?: string
  dark?: boolean
}>(), {
  readonly: false,
  height: '400px',
  dark: false
})

defineEmits<{
  'update:modelValue': [value: string]
}>()

const baseTheme = EditorView.theme({
  '&': { fontSize: '13px' },
  '.cm-gutters': { minWidth: '32px' },
  '.cm-scroller': { fontFamily: "'Consolas', 'Monaco', 'Courier New', monospace" }
})

const extensions = computed(() => {
  const ext = [yaml(), baseTheme]
  if (props.dark) ext.push(oneDark)
  if (props.readonly) ext.push(EditorView.editable.of(false))
  return ext
})
</script>

<style scoped>
.yaml-editor-wrapper {
  width: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
}
.yaml-editor-wrapper :deep(.cm-editor) {
  height: 100%;
}
.yaml-editor-wrapper :deep(.cm-editor.cm-focused) {
  outline: none;
}
</style>
