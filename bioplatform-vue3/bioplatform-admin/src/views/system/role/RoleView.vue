<template>
  <div class="role-container">
    <el-card shadow="never">
      <div class="toolbar">
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>新建角色
        </el-button>
      </div>

      <el-table :data="roles" v-loading="loading" border stripe>
        <el-table-column prop="roleName" label="角色名" min-width="160" />
        <el-table-column prop="roleDesc" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="userCount" label="用户数" width="90" align="center" />
        <el-table-column prop="permCount" label="权限数" width="90" align="center" />
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openPermDrawer(row as RoleListItem)">分配权限</el-button>
            <el-button type="primary" link @click="openEdit(row as RoleListItem)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row as RoleListItem)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建/编辑角色：仅名称+描述 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑角色' : '新建角色'" width="420px">
      <el-form :model="form" label-width="70px">
        <el-form-item label="角色名">
          <el-input v-model="form.roleName" placeholder="如 FINANCE，自动补 ROLE_ 前缀" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.roleDesc" type="textarea" :rows="2" placeholder="角色用途说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限：独立抽屉 -->
    <el-drawer v-model="permDrawerVisible" :title="`分配权限 - ${currentRole?.roleName || ''}`" size="420px">
      <el-tree
        ref="permTreeRef"
        :data="permTree"
        show-checkbox
        node-key="id"
        default-expand-all
        :default-checked-keys="checkedIds"
        :props="{ label: 'name', children: 'children' }"
      />
      <template #footer>
        <el-button @click="permDrawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingPerms" @click="savePerms">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import type { ElTree } from 'element-plus'
import {
  listRoles, createRole, updateRole, deleteRole,
  getRolePermissionIds, assignRolePermissions, getPermissionTree
} from '@/api/roleApi'
import type { RoleListItem, PermTreeNode } from '@/api/roleApi'

const loading = ref(false)
const roles = ref<RoleListItem[]>([])

const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref({ id: 0, roleName: '', roleDesc: '' })

const permDrawerVisible = ref(false)
const savingPerms = ref(false)
const currentRole = ref<RoleListItem | null>(null)
const permTree = ref<PermTreeNode[]>([])
const checkedIds = ref<number[]>([])
const permTreeRef = ref<InstanceType<typeof ElTree>>()

const loadRoles = async () => {
  loading.value = true
  try {
    const res = await listRoles()
    roles.value = res as unknown as RoleListItem[]
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  isEdit.value = false
  form.value = { id: 0, roleName: '', roleDesc: '' }
  dialogVisible.value = true
}

const openEdit = (row: RoleListItem) => {
  isEdit.value = true
  form.value = { id: row.id, roleName: row.roleName, roleDesc: row.roleDesc }
  dialogVisible.value = true
}

const submitForm = async () => {
  if (!form.value.roleName?.trim()) {
    ElMessage.warning('请输入角色名')
    return
  }
  if (isEdit.value) {
    await updateRole(form.value.id, form.value.roleDesc)
    ElMessage.success('已保存')
  } else {
    await createRole({ roleName: form.value.roleName, roleDesc: form.value.roleDesc })
    ElMessage.success('角色已创建')
  }
  dialogVisible.value = false
  await loadRoles()
}

const handleDelete = async (row: RoleListItem) => {
  await ElMessageBox.confirm(`确认删除角色 ${row.roleName}？`, '提示', { type: 'warning' })
  try {
    await deleteRole(row.id)
    ElMessage.success('已删除')
    await loadRoles()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '删除失败')
  }
}

const openPermDrawer = async (row: RoleListItem) => {
  currentRole.value = row
  if (!permTree.value.length) {
    permTree.value = (await getPermissionTree()) as unknown as PermTreeNode[]
  }
  const res = await getRolePermissionIds(row.id)
  checkedIds.value = ((res as unknown as { permissionIds: number[] }).permissionIds) || []
  permDrawerVisible.value = true
}

const savePerms = async () => {
  if (!currentRole.value) return
  savingPerms.value = true
  try {
    const tree = permTreeRef.value
    const checked = (tree?.getCheckedKeys(false) as number[]) || []
    const halfChecked = (tree?.getHalfCheckedKeys() as number[]) || []
    await assignRolePermissions(currentRole.value.id, [...checked, ...halfChecked])
    ElMessage.success('权限已保存，相关用户刷新页面后生效')
    permDrawerVisible.value = false
    await loadRoles()
  } finally {
    savingPerms.value = false
  }
}

onMounted(loadRoles)
</script>

<style scoped>
.role-container {
  padding: 16px;
}
.toolbar {
  margin-bottom: 12px;
}
</style>
