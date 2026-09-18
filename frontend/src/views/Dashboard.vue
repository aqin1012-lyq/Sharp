<template>
  <div class="dashboard">
    <div class="page-head">
      <span class="eyebrow">Overview</span>
      <h1 class="display">控制台</h1>
      <p class="lede">前后端分离的账号管理平台 · Spring Boot + MySQL · Vue 3 + Element Plus</p>
    </div>

    <div class="cards">
      <div class="feature" @click="$router.push('/email')">
        <div class="feature-top">
          <div class="ico"><el-icon><Message /></el-icon></div>
          <el-icon class="arrow"><Right /></el-icon>
        </div>
        <div class="feature-title">邮箱管理</div>
        <div class="feature-desc">解析并管理 Gmail / 012e / Outlook 账号，一键拆分字段入库。</div>
      </div>

      <div class="feature soon">
        <div class="feature-top">
          <div class="ico"><el-icon><Plus /></el-icon></div>
        </div>
        <div class="feature-title">更多模块</div>
        <div class="feature-desc">后续可在左侧菜单扩展更多管理页面。</div>
      </div>
    </div>

    <el-card shadow="never" class="about">
      <template #header><span class="card-title">关于 Sharp</span></template>
      <p>Sharp 是一个前后端分离的管理平台，后端 Java / Spring Boot + MySQL，前端 Vue 3 + Element Plus。</p>
      <p>当前已上线「邮箱管理」模块，支持三种邮箱格式的原始串解析、增删改查与关键字检索。</p>
    </el-card>
  </div>
</template>

<script setup>
import { Message, Right, Plus } from '@element-plus/icons-vue'
</script>

<style scoped>
.page-head { margin-bottom: 22px; }
.page-head .eyebrow { display: block; margin-bottom: 6px; }
.page-head h1 { font-size: clamp(24px, 4vw, 32px); margin: 0; color: var(--ink); }
.lede { color: var(--muted); margin: 8px 0 0; }

.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}
.feature {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 14px;
  box-shadow: var(--shadow);
  padding: 18px;
  cursor: pointer;
  transition: transform .12s ease, border-color .12s ease;
}
.feature:hover { transform: translateY(-2px); border-color: var(--border-strong); }
.feature.soon { cursor: default; opacity: .7; }
.feature.soon:hover { transform: none; border-color: var(--border); }
.feature-top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.ico {
  width: 40px; height: 40px; border-radius: 10px;
  display: grid; place-items: center;
  background: var(--accent-soft); color: var(--accent); font-size: 20px;
}
.arrow { color: var(--faint); font-size: 18px; }
.feature-title { font-weight: 600; font-size: 16px; color: var(--ink); margin-bottom: 4px; }
.feature-desc { color: var(--muted); font-size: 13px; line-height: 1.6; }

.about :deep(.el-card__body) p { color: var(--muted); line-height: 1.7; margin: 0 0 8px; }
.card-title { font-weight: 600; color: var(--ink); }
</style>
