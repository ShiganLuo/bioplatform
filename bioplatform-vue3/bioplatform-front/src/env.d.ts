/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

declare module 'pinia-plugin-persistedstate' {
  import { PiniaPlugin } from 'pinia'
  export default function piniaPluginPersistedstate(): PiniaPlugin
}
