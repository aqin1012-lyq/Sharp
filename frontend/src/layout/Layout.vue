<template>
  <el-container class="layout">
    <el-aside width="230px" class="aside">
      <div class="brand">
        <div class="glyph"><el-icon><Lock /></el-icon></div>
        <div class="brand-meta">
          <span class="brand-name display">Sharp</span>
          <span class="eyebrow">Console</span>
        </div>
      </div>
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>控制台</span>
        </el-menu-item>
        <el-menu-item index="/email">
          <el-icon><Message /></el-icon>
          <span>邮箱管理</span>
        </el-menu-item>
        <el-menu-item index="/mail-reader">
          <el-icon><Promotion /></el-icon>
          <span>邮件取件</span>
        </el-menu-item>
        <el-menu-item index="/stats">
          <el-icon><DataAnalysis /></el-icon>
          <span>录入统计</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="title display">{{ currentTitle }}</span>
        <button
          class="theme-btn"
          type="button"
          aria-label="切换主题"
          :title="`当前：${modeLabel}（点击循环 跟随系统 / 浅色 / 深色）`"
          @click="cycleTheme"
        >
          <el-icon><component :is="modeIcon" /></el-icon>
          <span>{{ modeLabel }}</span>
        </button>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { Sunny, Moon, Monitor, Lock } from '@element-plus/icons-vue'

const route = useRoute()
const activeMenu = computed(() => route.path)
const currentTitle = computed(() => route.meta.title || '')

// 三态主题：跟随系统 / 浅色 / 深色，默认跟随系统配色
const MODES = ['system', 'light', 'dark']
const MODE_LABEL = { system: '跟随系统', light: '浅色', dark: '深色' }
const MODE_ICON = { system: Monitor, light: Sunny, dark: Moon }

const mode = ref('system')
const isDark = ref(false)
let mql = null

const modeLabel = computed(() => MODE_LABEL[mode.value])
const modeIcon = computed(() => MODE_ICON[mode.value])

// 当前模式下是否为深色：显式深/浅直接取，跟随系统时看媒体查询
function resolveDark() {
  if (mode.value === 'dark') return true
  if (mode.value === 'light') return false
  return !!(mql && mql.matches)
}
function render() {
  isDark.value = resolveDark()
  document.documentElement.classList.toggle('dark', isDark.value)
}
function setMode(m) {
  mode.value = m
  try { localStorage.setItem('sharp-theme', m) } catch { /* ignore */ }
  render()
}
function cycleTheme() {
  setMode(MODES[(MODES.indexOf(mode.value) + 1) % MODES.length])
}
// 系统深浅切换时，只有"跟随系统"才实时更新
function onSystemChange() {
  if (mode.value === 'system') render()
}

onMounted(() => {
  let saved
  try { saved = localStorage.getItem('sharp-theme') } catch { /* ignore */ }
  // 兼容旧的 light/dark 存值；无值或非法则默认跟随系统
  mode.value = MODES.includes(saved) ? saved : 'system'
  mql = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null
  if (mql) mql.addEventListener('change', onSystemChange)
  render()
})
onBeforeUnmount(() => {
  if (mql) mql.removeEventListener('change', onSystemChange)
})
</script>

<style scoped>
.layout { height: 100%; }
.aside {
  background: var(--surface);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
}
.brand {
  height: 72px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 20px;
  border-bottom: 1px solid var(--border);
}
.glyph {
  flex: none;
  width: 40px;
  height: 40px;
  border-radius: 11px;
  background: var(--accent-soft);
  color: var(--accent-ink);
  border: 1px solid var(--border);
  display: grid;
  place-items: center;
  font-size: 20px;
}
.brand-meta { display: flex; flex-direction: column; gap: 1px; }
.brand-name { font-size: 20px; color: var(--ink); line-height: 1.1; }
.menu {
  border-right: none;
  padding: 12px 10px;
  background: transparent;
}
.menu :deep(.el-menu-item) {
  border-radius: 9px;
  height: 44px;
  margin-bottom: 4px;
  color: var(--muted);
  font-weight: 500;
}
.menu :deep(.el-menu-item.is-active) {
  background: var(--accent-soft);
  color: var(--accent);
}
.menu :deep(.el-menu-item:hover) { background: var(--surface-2); }
.header {
  background: var(--surface);
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.header .title { font-size: 17px; color: var(--ink); }
.theme-btn {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  background: var(--surface);
  color: var(--muted);
  border: 1px solid var(--border);
  border-radius: 9px;
  padding: 7px 12px;
  font-family: var(--mono);
  font-size: 12px;
  cursor: pointer;
  transition: border-color .12s ease, color .12s ease;
}
.theme-btn:hover { border-color: var(--border-strong); color: var(--ink); }
.main {
  background: var(--ground);
  padding: 24px;
}
</style>
