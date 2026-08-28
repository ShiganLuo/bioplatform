<template>
  <div class="profile-view">
    <el-card>
      <template #header>
        <span>个人中心</span>
      </template>
      <div class="profile-content">
        <div class="profile-avatar">
          <el-avatar :size="80" :src="userStore.userInfo?.avatar">
            {{ userStore.userInfo?.nickName?.charAt(0) || userStore.userInfo?.username?.charAt(0) || 'U' }}
          </el-avatar>
        </div>
        <el-descriptions :column="1" border style="max-width:500px;">
          <el-descriptions-item label="用户名">{{ userStore.userInfo?.username || '-' }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ userStore.userInfo?.nickName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ userStore.userInfo?.email || '-' }}</el-descriptions-item>
          <el-descriptions-item label="角色">
            <el-tag v-for="role in userStore.userInfo?.roles" :key="role" size="small" style="margin-right:4px;">{{ role }}</el-tag>
            <span v-if="!userStore.userInfo?.roles?.length">-</span>
          </el-descriptions-item>
          <el-descriptions-item label="注册时间">{{ userStore.userInfo?.createdAt || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from '@/stores/user'
const userStore = useUserStore()
</script>

<style scoped>
.profile-view { padding: 0; }
.profile-content { display: flex; flex-direction: column; align-items: center; gap: 24px; }
.profile-avatar { margin-bottom: 8px; }
</style>
