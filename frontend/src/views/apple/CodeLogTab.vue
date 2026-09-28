<template>
  <div class="tab-pane">
    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <div class="head-text">
            <span class="eyebrow"><span class="dot"></span>Monitor</span>
            <span class="head-title">转发邮箱可用性检测</span>
          </div>
          <el-button type="primary" size="small" :loading="checking" @click="handleCheckAll">
            {{ checking ? '检测中…' : '一键检测当前转发邮箱' }}
          </el-button>
        </div>
      </template>

      <div v-if="checkResults.length" class="check-list">
        <div v-for="(r, i) in checkResults" :key="i" class="check-row">
          <el-tag :type="r.success ? 'success' : 'danger'" size="small" effect="plain">
            {{ r.success ? '正常' : '异常' }}
          </el-tag>
          <span class="mono">{{ r.forwardEmail }}</span>
          <span class="check-msg">{{ r.message }}</span>
        </div>
      </div>
      <div v-else class="empty-state">
        点上面的按钮，会对每个「当前转发邮箱」各试一次取件，结果同时记入下方日志。
      </div>
    </el-card>

    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <div class="head-text">
            <span class="eyebrow"><span class="dot good"></span>Logs</span>
            <span class="head-title">接码 / 切换 / 检测日志</span>
          </div>
          <span class="result-count">{{ total }} 条</span>
        </div>
      </template>

      <div class="filters">
        <el-radio-group v-model="query.type" class="type-chips" @change="reload">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="fetch">接码</el-radio-button>
          <el-radio-button value="switch">切换</el-radio-button>
          <el-radio-button value="check">检测</el-radio-button>
        </el-radio-group>
        <el-select v-model="query.success" clearable placeholder="结果" style="width: 120px" @change="reload">
          <el-option label="成功" :value="true" />
          <el-option label="失败" :value="false" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="搜索邮箱 / 原因"
          clearable
          style="max-width: 240px"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-button :loading="loading" @click="reload">查询</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border size="small" style="width: 100%">
        <el-table-column prop="id" label="ID" width="66" />
        <el-table-column label="事件" width="86">
          <template #default="{ row }">
            <el-tag :type="TYPE_TAG[row.type] || 'info'" size="small" effect="plain">
              {{ TYPE_LABEL[row.type] || row.type }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="76">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'" size="small">
              {{ row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="entryEmail" label="入口邮箱" min-width="190">
          <template #default="{ row }">
            <span v-if="row.entryEmail">{{ row.entryEmail }}</span>
            <span v-else class="muted-cell">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="forwardEmail" label="转发邮箱" min-width="190" />
        <el-table-column prop="verifyCode" label="验证码" width="96">
          <template #default="{ row }">
            <span v-if="row.verifyCode" class="mono">{{ row.verifyCode }}</span>
            <span v-else class="muted-cell">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column prop="durationMs" label="耗时" width="86">
          <template #default="{ row }">
            <span v-if="row.durationMs != null" class="mono">{{ row.durationMs }}ms</span>
            <span v-else class="muted-cell">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="操作人" width="96" />
        <el-table-column prop="createTime" label="时间" width="170" />
      </el-table>

      <el-pagination
        class="pagination"
        layout="total, prev, pager, next, sizes"
        :total="total"
        :page-size="query.size"
        :current-page="query.page"
        :page-sizes="[20, 50, 100]"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listCodeLogs, checkForwardEmail } from '../../api/apple'

const TYPE_LABEL = { fetch: '接码', switch: '切换', check: '检测' }
const TYPE_TAG = { fetch: 'primary', switch: 'warning', check: 'info' }

/* —— 一键检测 —— */
const checking = ref(false)
const checkResults = ref([])

async function handleCheckAll() {
  checking.value = true
  try {
    const res = await checkForwardEmail({})
    checkResults.value = res.data || []
    const bad = checkResults.value.filter((r) => !r.success).length
    if (bad) ElMessage.warning(`${checkResults.value.length} 个转发邮箱里有 ${bad} 个异常`)
    else ElMessage.success(`${checkResults.value.length} 个转发邮箱全部正常`)
    await load()
  } catch {
    // 错误信息已由 request 拦截器统一提示
  } finally {
    checking.value = false
  }
}

/* —— 日志列表 —— */
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ type: '', success: null, keyword: '', page: 1, size: 20 })

async function load() {
  loading.value = true
  try {
    const res = await listCodeLogs({
      type: query.type || undefined,
      success: query.success === null || query.success === '' ? undefined : query.success,
      keyword: query.keyword || undefined,
      page: query.page,
      size: query.size
    })
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function reload() {
  query.page = 1
  return load()
}
function onPageChange(p) {
  query.page = p
  load()
}
function onSizeChange(s) {
  query.size = s
  query.page = 1
  load()
}

onMounted(load)
</script>

<style scoped>
@import '../../styles/apple-tab.css';

.check-list { display: flex; flex-direction: column; gap: 8px; }
.check-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 12.5px;
  color: var(--ink);
}
.check-msg { color: var(--faint); font-size: 12px; }
.empty-state { color: var(--faint); font-size: 13px; text-align: center; padding: 22px 0; }
</style>
