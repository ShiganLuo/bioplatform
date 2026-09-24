import type { Directive, DirectiveBinding } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * v-permission 指令：根据用户角色控制 DOM 元素的显示
 *
 * 用法：
 *   <el-button v-permission="['ROLE_ADMIN']">删除</el-button>
 *   <el-button v-permission="['ROLE_ADMIN', 'ROLE_USER']">编辑</el-button>
 */
export const permissionDirective: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding<string[]>) {
    const requiredRoles = binding.value
    if (!requiredRoles || requiredRoles.length === 0) return

    const userStore = useUserStore()
    const userRoles = userStore.userInfo?.roles || []
    const hasPermission = requiredRoles.some(role => userRoles.includes(role))

    if (!hasPermission) {
      el.parentNode?.removeChild(el)
    }
  },
  updated(el: HTMLElement, binding: DirectiveBinding<string[]>) {
    const requiredRoles = binding.value
    if (!requiredRoles || requiredRoles.length === 0) return

    const userStore = useUserStore()
    const userRoles = userStore.userInfo?.roles || []
    const hasPermission = requiredRoles.some(role => userRoles.includes(role))

    if (!hasPermission && el.parentNode) {
      el.parentNode.removeChild(el)
    }
  }
}
