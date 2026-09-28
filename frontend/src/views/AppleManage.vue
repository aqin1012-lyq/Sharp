<template>
  <div class="apple-manage">
    <!-- 页头 -->
    <div class="page-head">
      <span class="eyebrow">Apple</span>
      <h1 class="display">Apple ID 录入</h1>
      <p class="lede">
        ①②③ 负责录入：Apple ID 的转发邮箱（指定当前在用的那个）→ 隐藏邮箱 → 黑客邮箱（一对一）。
        ④⑤ 反过来用：给一个隐藏邮箱或黑客邮箱，自动倒推到能收信的邮箱并接码；
        转发邮箱失效时自动切换，过程记在 ⑥ 日志里。
      </p>
    </div>

    <el-tabs v-model="tab" class="apple-tabs">
      <el-tab-pane name="forward">
        <template #label><span class="tab-label">① 转发邮箱</span></template>
        <ForwardEmailTab v-if="loaded.forward" />
      </el-tab-pane>
      <el-tab-pane name="hide">
        <template #label><span class="tab-label">② 隐藏邮箱</span></template>
        <HideEmailTab v-if="loaded.hide" />
      </el-tab-pane>
      <el-tab-pane name="hacker">
        <template #label><span class="tab-label">③ 黑客邮箱</span></template>
        <HackerEmailTab v-if="loaded.hacker" />
      </el-tab-pane>
      <el-tab-pane name="hideCode">
        <template #label><span class="tab-label">④ 隐藏邮箱接码</span></template>
        <CodeFetchPanel v-if="loaded.hideCode" entry-type="hide" />
      </el-tab-pane>
      <el-tab-pane name="hackerCode">
        <template #label><span class="tab-label">⑤ 黑客邮箱接码</span></template>
        <CodeFetchPanel v-if="loaded.hackerCode" entry-type="hacker" />
      </el-tab-pane>
      <el-tab-pane name="log">
        <template #label><span class="tab-label">⑥ 检测与日志</span></template>
        <CodeLogTab v-if="loaded.log" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import ForwardEmailTab from './apple/ForwardEmailTab.vue'
import HideEmailTab from './apple/HideEmailTab.vue'
import HackerEmailTab from './apple/HackerEmailTab.vue'
import CodeFetchPanel from './apple/CodeFetchPanel.vue'
import CodeLogTab from './apple/CodeLogTab.vue'

const tab = ref('forward')
// 懒挂载：没点开的 Tab 不请求接口；挂载后保留，切回来不再重新加载
const loaded = reactive({
  forward: true,
  hide: false,
  hacker: false,
  hideCode: false,
  hackerCode: false,
  log: false
})

watch(tab, (v) => {
  loaded[v] = true
})
</script>

<style scoped>
.page-head { margin-bottom: 14px; }
.page-head .eyebrow { display: block; margin-bottom: 6px; }
.page-head h1 { font-size: clamp(24px, 4vw, 32px); margin: 0; color: var(--ink); }
.lede { color: var(--muted); margin: 8px 0 0; max-width: 78ch; line-height: 1.6; }

.apple-tabs :deep(.el-tabs__header) { margin-bottom: 18px; }
.apple-tabs :deep(.el-tabs__nav-wrap::after) { background-color: var(--border); }
.tab-label { font-size: 14px; font-weight: 600; }
</style>
