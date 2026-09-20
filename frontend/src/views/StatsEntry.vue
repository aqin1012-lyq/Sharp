<template>
  <div class="stats-entry">
    <div class="page-head">
      <span class="eyebrow">Statistics</span>
      <h1 class="display">录入统计</h1>
      <p class="lede">按录入人（登录账号）统计邮箱录入量。录入人来自登录账号，历史数据无归属记为「未知」。</p>
    </div>

    <div class="stat-tiles">
      <div class="stat"><span class="n display">{{ total }}</span><span class="k">总录入量</span></div>
      <div class="stat"><span class="n display">{{ rows.length }}</span><span class="k">录入人数</span></div>
      <div class="stat"><span class="n display">{{ topUser }}</span><span class="k">录入最多</span></div>
    </div>

    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <div class="head-text">
            <span class="eyebrow"><span class="dot"></span>By user</span>
            <span class="head-title">各录入人明细</span>
          </div>
          <el-button text size="small" :loading="loading" @click="load">刷新</el-button>
        </div>
      </template>

      <div v-if="rows.length" class="rank">
        <div v-for="(r, i) in rows" :key="r.user" class="rank-row">
          <span class="rank-no">{{ i + 1 }}</span>
          <span class="rank-user">{{ r.user }}</span>
          <div class="rank-bar-wrap">
            <div class="rank-bar" :style="{ width: pct(r.count) + '%' }"></div>
          </div>
          <span class="rank-count mono">{{ r.count }}</span>
          <span class="rank-pct">{{ pct(r.count) }}%</span>
        </div>
      </div>
      <div v-else class="empty-state">{{ loading ? '加载中…' : '暂无数据' }}</div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { statsByUser } from '../api/email'

const rows = ref([])
const loading = ref(false)

const total = computed(() => rows.value.reduce((s, r) => s + (r.count || 0), 0))
const maxCount = computed(() => rows.value.reduce((m, r) => Math.max(m, r.count || 0), 0))
const topUser = computed(() => (rows.value.length ? rows.value[0].user : '—'))

function pct(count) {
  if (!maxCount.value) return 0
  return Math.round((count / maxCount.value) * 100)
}

async function load() {
  loading.value = true
  try {
    const res = await statsByUser()
    rows.value = (res.data || []).slice().sort((a, b) => b.count - a.count)
  } catch {
    // 错误已由拦截器提示
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-head { margin-bottom: 18px; }
.page-head .eyebrow { display: block; margin-bottom: 6px; }
.page-head h1 { font-size: clamp(24px, 4vw, 32px); margin: 0; color: var(--ink); }
.lede { color: var(--muted); margin: 8px 0 0; max-width: 68ch; line-height: 1.6; }

.stat-tiles {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 14px;
  margin-bottom: 20px;
}
.stat {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 14px;
  box-shadow: var(--shadow);
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.stat .n { font-size: 26px; color: var(--ink); font-variant-numeric: tabular-nums; }
.stat .k { font-size: 12.5px; color: var(--muted); }

.section { margin-bottom: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.head-text { display: flex; flex-direction: column; gap: 2px; }
.head-text .eyebrow { display: flex; align-items: center; gap: 7px; }
.head-title { font-weight: 600; font-size: 15px; color: var(--ink); }
.dot { width: 8px; height: 8px; border-radius: 50%; background: var(--accent); display: inline-block; }

.rank { display: flex; flex-direction: column; gap: 10px; }
.rank-row { display: flex; align-items: center; gap: 12px; }
.rank-no {
  width: 22px; height: 22px; flex: none;
  display: grid; place-items: center;
  font-size: 12px; color: var(--faint);
  background: var(--surface-2); border-radius: 6px;
  font-variant-numeric: tabular-nums;
}
.rank-user {
  width: 140px; flex: none;
  font-size: 13px; color: var(--ink);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.rank-bar-wrap {
  flex: 1; min-width: 0;
  background: var(--surface-2);
  border-radius: 6px; height: 14px; overflow: hidden;
}
.rank-bar {
  height: 100%;
  background: var(--accent);
  border-radius: 6px;
  transition: width .3s ease;
  min-width: 2px;
}
.rank-count { width: 56px; text-align: right; color: var(--ink); font-size: 13px; }
.rank-pct { width: 46px; text-align: right; color: var(--faint); font-size: 12px; }

.empty-state { color: var(--faint); font-size: 13px; text-align: center; padding: 36px 0; }
</style>
