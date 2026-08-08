/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

declare module 'element-plus'
declare module '@element-plus/icons-vue'
declare module 'echarts'
declare module 'md-editor-v3'
