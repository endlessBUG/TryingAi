<template>
  <el-container class="layout-container">
    <el-header class="layout-header">
      <div class="header-left">
        <el-icon :size="24" style="margin-right: 10px;"><setting /></el-icon>
        <h2>AI Model Trainer</h2>
      </div>
      <div class="header-right"></div>
    </el-header>

    <el-container>
      <el-aside width="200px" class="layout-aside">
        <el-menu
          :default-active="activeMenu"
          router
          class="el-menu-vertical"
        >
          <el-menu-item index="/upload">
            <el-icon><upload /></el-icon>
            <span>数据集管理</span>
          </el-menu-item>
          <el-menu-item index="/tasks">
            <el-icon><list /></el-icon>
            <span>任务列表</span>
          </el-menu-item>
          <el-menu-item index="/trainers">
            <el-icon><cpu /></el-icon>
            <span>训练器管理</span>
          </el-menu-item>
          <el-menu-item index="/conda">
            <el-icon><box /></el-icon>
            <span>Conda 配置</span>
          </el-menu-item>
          <el-menu-item index="/config">
            <el-icon><setting /></el-icon>
            <span>配置管理</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Setting, Upload, List, Cpu, Box } from '@element-plus/icons-vue'

const route = useRoute()
const activeMenu = computed(() => route.path)
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.layout-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: #409eff;
  color: white;
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
}

.header-left h2 {
  margin: 0;
  font-size: 20px;
}

.header-right {
  display: flex;
  align-items: center;
}

.layout-aside {
  background-color: #f5f7fa;
  border-right: 1px solid #e4e7ed;
}

.layout-main {
  background-color: #f0f2f5;
  padding: 0;
}

.el-menu {
  border-right: none;
}
</style>
