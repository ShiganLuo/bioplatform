import type { Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * v-permission 指令：按角色名或权限标识控制 DOM 元素的显示（任一命中即显示）
 *
 * 用法：
 *   <el-button v-permission="['ROLE_ADMIN']">删除</el-button>
 *   <el-button v-permission="['system:user:delete']">删除</el-button>
 *   <el-button v-permission="['ROLE_ADMIN', 'project:export']">导出</el-button>
 */
function match(el: HTMLElement, binding: DirectiveBinding<string[]>) {
  const required = binding.value
  if (!required || required.length === 0) return

  const userStore = useUserStore()
  const userRoles = userStore.userInfo?.roles || []
  const userPerms = userStore.userInfo?.permissions || []
  const hit = required.some(token => userRoles.includes(token) || userPerms.includes(token))

  if (!hit && el.parentNode) {
    el.parentNode.removeChild(el)
  }
}

export const permissionDirective: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding<string[]>) {
    match(el, binding)
  },
  updated(el: HTMLElement, binding: DirectiveBinding<string[]>) {
    match(el, binding)
  }
}
