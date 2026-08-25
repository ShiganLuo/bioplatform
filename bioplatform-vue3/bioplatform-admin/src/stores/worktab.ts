import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import router from '@/router'

export interface WorkTab {
  title: string
  path: string
  name: string
  closable?: boolean
}

const HOME_TAB: WorkTab = {
  title: '仪表盘',
  path: '/dashboard',
  name: 'Dashboard',
  closable: false
}

export const useWorktabStore = defineStore('worktab', () => {
  const opened = ref<WorkTab[]>([HOME_TAB])
  const currentPath = ref('/dashboard')

  const currentTab = computed(() =>
    opened.value.find(tab => tab.path === currentPath.value) || HOME_TAB
  )

  function openTab(tab: WorkTab) {
    currentPath.value = tab.path
    const exists = opened.value.find(t => t.path === tab.path)
    if (!exists) {
      opened.value.push({ ...tab, closable: tab.closable ?? tab.path !== '/dashboard' })
    }
  }

  function closeTab(path: string) {
    const index = opened.value.findIndex(t => t.path === path)
    if (index === -1) return

    // Cannot close dashboard
    if (path === '/dashboard') return

    opened.value.splice(index, 1)

    // If closed the active tab, activate the nearest tab
    if (currentPath.value === path) {
      const newIndex = index >= opened.value.length ? opened.value.length - 1 : index
      const target = opened.value[newIndex]
      if (target) {
        router.push(target.path)
      }
    }
  }

  function closeOtherTabs(path: string) {
    opened.value = opened.value.filter(t => t.path === '/dashboard' || t.path === path)
    if (currentPath.value !== path) {
      router.push(path)
    }
  }

  function closeLeftTabs(path: string) {
    const index = opened.value.findIndex(t => t.path === path)
    if (index > 0) {
      // Keep dashboard and tabs from index onward
      opened.value = opened.value.filter((t, i) => i === 0 || i >= index)
    }
  }

  function closeRightTabs(path: string) {
    const index = opened.value.findIndex(t => t.path === path)
    if (index !== -1) {
      opened.value = opened.value.slice(0, index + 1)
    }
  }

  function closeAllTabs() {
    opened.value = [HOME_TAB]
    router.push('/dashboard')
  }

  function setCurrentPath(path: string) {
    currentPath.value = path
  }

  return {
    opened,
    currentPath,
    currentTab,
    openTab,
    closeTab,
    closeOtherTabs,
    closeLeftTabs,
    closeRightTabs,
    closeAllTabs,
    setCurrentPath
  }
})
