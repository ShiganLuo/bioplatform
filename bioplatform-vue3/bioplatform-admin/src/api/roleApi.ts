import http from '@/utils/http/axios'

export interface Role {
  id: number
  roleName: string
  roleDesc: string
  createdAt?: string
  updatedAt?: string
}

export interface RoleListItem {
  id: number
  roleName: string
  roleDesc: string
  userCount: number
  permCount: number
  createdAt?: string
}

export interface PermTreeNode {
  id: number
  name: string
  permission: string
  type: string
  parentId: number
  path?: string
  orderNum: number
  children?: PermTreeNode[]
}

export function listRoles() {
  return http.get<RoleListItem[]>('/api/admin/roles/list')
}

export function createRole(data: { roleName: string; roleDesc?: string }) {
  return http.post<Role>('/api/admin/roles/create', data)
}

export function updateRole(id: number, roleDesc: string) {
  return http.put('/api/admin/roles/update', { id, roleDesc })
}

export function deleteRole(id: number) {
  return http.delete(`/api/admin/roles/${id}`)
}

export function getRolePermissionIds(id: number) {
  return http.get<{ permissionIds: number[] }>(`/api/admin/roles/${id}/permissions`)
}

export function assignRolePermissions(id: number, permissionIds: number[]) {
  return http.put(`/api/admin/roles/${id}/permissions`, { permissionIds })
}

export function getPermissionTree() {
  return http.get<PermTreeNode[]>('/api/admin/permissions/tree')
}
