<template>
  <router-view />
</template>

<script setup lang="ts">
// App root component
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body {
  width: 100%;
  height: 100%;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB',
    'Microsoft YaHei', '微软雅黑', Arial, sans-serif;
}

#app {
  width: 100%;
  min-height: 100vh;
}

/* Global card styling */
.el-card {
  border-radius: 12px;
  border: none;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.el-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

/* Global button styling */
.el-button--primary {
  background: linear-gradient(135deg, #409eff, #66b1ff);
  border: none;
}

.el-button--primary:hover {
  background: linear-gradient(135deg, #66b1ff, #409eff);
}

/* Global tag styling */
.el-tag--light {
  border: none;
}

/* Smooth scrollbar */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-thumb {
  background: #c0c4cc;
  border-radius: 3px;
}

::-webkit-scrollbar-track {
  background: transparent;
}
</style>
