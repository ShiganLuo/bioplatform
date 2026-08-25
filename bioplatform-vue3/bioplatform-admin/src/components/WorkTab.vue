<template>
  <div class="worktab">
    <div class="scroll-view" ref="scrollRef">
      <ul class="tabs" ref="tabsRef" :style="{ transform: `translateX(${translateX}px)` }">
        <li
          v-for="tab in worktabStore.opened"
          :key="tab.path"
          class="tab-item"
          :class="{ active: tab.path === worktabStore.currentPath }"
          @click="handleClick(tab)"
          @contextmenu.prevent="(e: MouseEvent) => showContextMenu(e, tab)"
        >
          <span class="tab-title">{{ tab.title }}</span>
          <el-icon
            v-if="tab.closable !== false"
            class="tab-close"
            @click.stop="worktabStore.closeTab(tab.path)"
          >
            <Close />
          </el-icon>
        </li>
      </ul>
    </div>

    <!-- Dropdown menu for batch operations -->
    <el-dropdown @command="handleBatchCommand" trigger="click">
      <el-icon class="more-btn"><ArrowDown /></el-icon>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="closeCurrent" :disabled="!contextTab?.closable">
            <el-icon><Close /></el-icon>关闭当前
          </el-dropdown-item>
          <el-dropdown-item command="closeOthers">
            <el-icon><FolderRemove /></el-icon>关闭其他
          </el-dropdown-item>
          <el-dropdown-item command="closeLeft">
            <el-icon><Back /></el-icon>关闭左侧
          </el-dropdown-item>
          <el-dropdown-item command="closeRight">
            <el-icon><Right /></el-icon>关闭右侧
          </el-dropdown-item>
          <el-dropdown-item divided command="closeAll">
            <el-icon><CircleClose /></el-icon>关闭全部
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>

    <!-- Right-click context menu -->
    <teleport to="body">
      <div
        v-if="contextMenu.visible"
        class="context-menu"
        :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
      >
        <div
          class="context-menu-item"
          :class="{ disabled: !contextTab?.closable }"
          @click="handleContextCommand('closeCurrent')"
        >
          <el-icon><Close /></el-icon>关闭当前
        </div>
        <div class="context-menu-item" @click="handleContextCommand('closeOthers')">
          <el-icon><FolderRemove /></el-icon>关闭其他
        </div>
        <div class="context-menu-item" @click="handleContextCommand('closeLeft')">
          <el-icon><Back /></el-icon>关闭左侧
        </div>
        <div class="context-menu-item" @click="handleContextCommand('closeRight')">
          <el-icon><Right /></el-icon>关闭右侧
        </div>
        <div class="context-menu-divider" />
        <div class="context-menu-item" @click="handleContextCommand('closeAll')">
          <el-icon><CircleClose /></el-icon>关闭全部
        </div>
      </div>
    </teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useWorktabStore, type WorkTab } from '@/stores/worktab'
import { Close, ArrowDown, FolderRemove, Back, Right, CircleClose } from '@element-plus/icons-vue'

const router = useRouter()
const worktabStore = useWorktabStore()

const scrollRef = ref<HTMLElement | null>(null)
const tabsRef = ref<HTMLElement | null>(null)
const translateX = ref(0)
const contextTab = ref<WorkTab | null>(null)

const contextMenu = reactive({
  visible: false,
  x: 0,
  y: 0
})

// --- Tab click ---
function handleClick(tab: WorkTab) {
  if (tab.path !== worktabStore.currentPath) {
    router.push(tab.path)
  }
}

// --- Context menu ---
function showContextMenu(e: MouseEvent, tab: WorkTab) {
  contextTab.value = tab
  contextMenu.x = e.clientX
  contextMenu.y = e.clientY
  contextMenu.visible = true
}

function hideContextMenu() {
  contextMenu.visible = false
}

function handleContextCommand(command: string) {
  if (!contextTab.value) return
  executeCommand(command, contextTab.value.path)
  hideContextMenu()
}

function handleBatchCommand(command: string) {
  executeCommand(command, worktabStore.currentPath)
}

function executeCommand(command: string, path: string) {
  switch (command) {
    case 'closeCurrent':
      worktabStore.closeTab(path)
      break
    case 'closeOthers':
      worktabStore.closeOtherTabs(path)
      break
    case 'closeLeft':
      worktabStore.closeLeftTabs(path)
      break
    case 'closeRight':
      worktabStore.closeRightTabs(path)
      break
    case 'closeAll':
      worktabStore.closeAllTabs()
      break
  }
}

// --- Scroll with mouse wheel ---
function handleWheel(e: WheelEvent) {
  if (!scrollRef.value || !tabsRef.value) return
  if (tabsRef.value.offsetWidth <= scrollRef.value.offsetWidth) return

  e.preventDefault()
  const delta = Math.abs(e.deltaX) > Math.abs(e.deltaY) ? e.deltaX : e.deltaY
  const xMin = scrollRef.value.offsetWidth - tabsRef.value.offsetWidth
  translateX.value = Math.min(Math.max(translateX.value - delta, xMin), 0)
}

// --- Auto-position active tab ---
function autoPosition() {
  if (!scrollRef.value || !tabsRef.value) return
  const activeEl = tabsRef.value.querySelector('.active') as HTMLElement | null
  if (!activeEl) return

  const scrollWidth = scrollRef.value.offsetWidth
  const { offsetLeft, clientWidth } = activeEl
  const tabRight = offsetLeft + clientWidth
  const ulWidth = tabsRef.value.offsetWidth

  if (tabRight > scrollWidth - translateX.value) {
    translateX.value = Math.max(scrollWidth - tabRight - 12, scrollWidth - ulWidth)
  } else if (offsetLeft < -translateX.value) {
    translateX.value = -offsetLeft
  }
}

watch(() => worktabStore.currentPath, () => {
  setTimeout(autoPosition, 50)
})

// --- Global click to close context menu ---
function onGlobalClick() {
  hideContextMenu()
}

onMounted(() => {
  scrollRef.value?.addEventListener('wheel', handleWheel, { passive: false })
  document.addEventListener('click', onGlobalClick)
})

onBeforeUnmount(() => {
  scrollRef.value?.removeEventListener('wheel', handleWheel)
  document.removeEventListener('click', onGlobalClick)
})
</script>

<style scoped>
.worktab {
  display: flex;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  height: 38px;
  padding: 0 4px;
  flex-shrink: 0;
}

.scroll-view {
  flex: 1;
  overflow: hidden;
  position: relative;
}

.tabs {
  display: flex;
  list-style: none;
  margin: 0;
  padding: 4px 0;
  gap: 4px;
  white-space: nowrap;
  transition: transform 0.3s ease;
}

.tab-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0 12px;
  height: 28px;
  font-size: 12px;
  color: #606266;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  cursor: pointer;
  user-select: none;
  transition: all 0.2s;
  flex-shrink: 0;
}

.tab-item:hover {
  color: #409eff;
  background: #ecf5ff;
  border-color: #d9ecff;
}

.tab-item.active {
  color: #fff;
  background: #409eff;
  border-color: #409eff;
}

.tab-item.active .tab-close {
  color: rgba(255, 255, 255, 0.8);
}

.tab-item.active .tab-close:hover {
  color: #fff;
}

.tab-title {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tab-close {
  font-size: 12px;
  color: #c0c4cc;
  border-radius: 50%;
  transition: all 0.2s;
}

.tab-close:hover {
  color: #fff;
  background: rgba(0, 0, 0, 0.15);
}

.more-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  margin-left: 4px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #f5f7fa;
  cursor: pointer;
  color: #606266;
  font-size: 12px;
  transition: all 0.2s;
}

.more-btn:hover {
  color: #409eff;
  border-color: #d9ecff;
  background: #ecf5ff;
}

/* Context menu */
.context-menu {
  position: fixed;
  z-index: 9999;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  padding: 4px 0;
  min-width: 140px;
}

.context-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 16px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  transition: all 0.15s;
}

.context-menu-item:hover {
  background: #ecf5ff;
  color: #409eff;
}

.context-menu-item.disabled {
  color: #c0c4cc;
  cursor: not-allowed;
}

.context-menu-item.disabled:hover {
  background: transparent;
  color: #c0c4cc;
}

.context-menu-divider {
  height: 1px;
  background: #e4e7ed;
  margin: 4px 0;
}
</style>
