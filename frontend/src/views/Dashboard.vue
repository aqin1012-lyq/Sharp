<template>
  <div>
    <el-row :gutter="20">
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-title">邮箱管理</div>
          <div class="stat-desc">解析并管理 Gmail / 012e / Outlook 邮箱账号</div>
          <el-button type="primary" text @click="$router.push('/email')">进入 →</el-button>
        </el-card>
      </el-col>
    </el-row>
    <el-card style="margin-top: 20px">
      <template #header>关于 Sharp</template>
      <p>Sharp 是一个前后端分离的管理平台（后端 Java / Spring Boot + MySQL，前端 Vue3 + Element Plus）。</p>
      <p>当前已上线「邮箱管理」模块，后续可在左侧菜单扩展更多管理页面。</p>
    </el-card>
  </div>
</template>

<script setup>
</script>

<style scoped>
.stat-title {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 8px;
}
.stat-desc {
  color: #888;
  margin-bottom: 12px;
}
</style>
