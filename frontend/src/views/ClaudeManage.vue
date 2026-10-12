<template>
  <div class="claude-manage">
    <!-- 页头 -->
    <div class="page-head">
      <span class="eyebrow">Claude Accounts</span>
      <h1 class="display">Claude 账号</h1>
      <p class="lede">按「claude账号 ---- 邮箱账号 ---- 邮箱密码 ---- SK ---- ID」格式批量粘贴，自动拆分字段入库。</p>
    </div>

    <!-- 统计磁贴 -->
    <div class="stats">
      <div class="stat"><span class="n display">{{ total }}</span><span class="k">总记录</span></div>
      <div class="stat"><span class="n display">{{ lineTotal }}</span><span class="k">当前输入行</span></div>
      <div class="stat"><span class="n display">{{ previewList.length }}</span><span class="k">预览条数</span></div>
    </div>

    <!-- 录入 / 解析 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <div class="head-text">
            <span class="eyebrow"><span class="dot"></span>Import &amp; Parse</span>
            <span class="head-title">账号录入</span>
          </div>
        </div>
      </template>

      <!-- 字段说明 -->
      <div class="schema">
        <template v-for="(f, i) in fields" :key="f.key">
          <span class="chip"><span class="pos">{{ i + 1 }}</span>{{ f.label }}</span>
          <span v-if="i < fields.length - 1" class="sep">----</span>
        </template>
      </div>
      <div class="schema-hint">
        一行一条，按 2 个及以上连字符（<code>----</code>）切段，第 N 段写入上面第 N 个字段；
        段数不够的字段留空，最后一段（ID）吞掉剩余原文。SK 内部的单个 <code>-</code> 不受影响。
      </div>

      <!-- 操作栏 -->
      <div class="toolbar">
        <el-button type="primary" :loading="parsing" :disabled="!lineTotal" @click="handleParse">解析预览</el-button>
        <el-button type="success" :loading="saving" :disabled="!previewList.length" @click="handleSave">保存入库</el-button>
        <el-button @click="loadExample">载入示例</el-button>
        <el-button @click="clearAll">清空</el-button>
      </div>

      <!-- 输入 / 输出 双栏 -->
      <div class="panes">
        <div class="pane">
          <div class="pane-head">
            <span class="label"><span class="pane-dot"></span>原始信息</span>
            <span class="count">{{ lineTotal }} 行</span>
          </div>
          <el-input
            v-model="rawData"
            type="textarea"
            :rows="12"
            :placeholder="placeholder"
            class="raw-input"
            resize="vertical"
          />
          <div class="hint">一行一条，空行自动忽略，每行首尾空白自动清除。</div>
        </div>

        <div class="pane out">
          <div class="pane-head">
            <span class="label"><span class="pane-dot"></span>解析预览</span>
            <span class="count">{{ previewList.length }} 条</span>
          </div>
          <div class="pane-body">
            <el-table v-if="previewList.length" :data="previewList" border size="small" style="width: 100%">
              <el-table-column type="index" label="#" width="46" align="center" />
              <el-table-column
                v-for="col in fields"
                :key="col.key"
                :label="col.label"
                :min-width="col.width || 140"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  <span v-if="row[col.key]" class="cell-val">
                    <span class="cell-txt">{{ row[col.key] }}</span>
                    <el-icon class="copy-icon" title="复制" @click="copy(row[col.key])"><CopyDocument /></el-icon>
                  </span>
                  <span v-else class="cell-empty">—</span>
                </template>
              </el-table-column>
            </el-table>
            <div v-else class="pane-empty">点「解析预览」后，按字段拆出的结果会显示在这里；预览结果未入库。</div>
          </div>
          <div class="hint">改动原始信息后需重新解析。</div>
        </div>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <div class="head-text">
            <span class="eyebrow"><span class="dot good"></span>Accounts</span>
            <span class="head-title">账号列表</span>
          </div>
          <div class="filters">
            <el-input
              v-model="query.keyword"
              placeholder="按 Claude 账号 / 邮箱 / ID 搜索"
              clearable
              style="width: 240px"
              @keyup.enter="onSearch"
            />
            <el-button type="primary" style="margin-left: 8px" @click="onSearch">查询</el-button>
          </div>
        </div>
      </template>

      <el-table :data="tableData" border v-loading="loading" size="small" style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" fixed />
        <el-table-column
          v-for="col in fields"
          :key="col.key"
          :prop="col.key"
          :label="col.label"
          :min-width="col.width || 140"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span v-if="row[col.key]" class="data-cell">
              {{ row[col.key] }}
              <el-icon class="copy-icon" @click="copy(row[col.key])"><CopyDocument /></el-icon>
            </span>
            <span v-else class="empty">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="录入人" width="110" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-popconfirm title="确认删除该记录？" :width="190" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button type="danger" text size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pagination"
        layout="total, prev, pager, next, sizes"
        :total="total"
        :page-size="query.size"
        :current-page="query.page"
        :page-sizes="[10, 20, 50]"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { parseClaude, saveClaude, listClaude, deleteClaude } from '../api/claude'

// 字段定义：key 与后端 ClaudeAccount 同名，顺序即解析顺序
const fields = [
  { key: 'claudeAccount', label: 'Claude 账号', width: 200 },
  { key: 'email', label: '邮箱账号', width: 200 },
  { key: 'emailPassword', label: '邮箱密码', width: 140 },
  { key: 'sk', label: 'SK', width: 260 },
  { key: 'accountId', label: 'ID', width: 160 }
]

const placeholder =
  '示例：\nuser@claude.ai----user@gmail.com----MailPw123----sk-ant-sid02-abc123----acc_0001'
const EXAMPLE = [
  'alice@claude.ai----alice@gmail.com----Mail!a1b2----sk-ant-sid02-abc123xyz----acc_1001',
  'bob@claude.ai----bob@outlook.com----Bob#2024----sk-ant-sid02-def456uvw----acc_1002'
].join('\n')

const rawData = ref('')
const parsing = ref(false)
const saving = ref(false)
const previewList = ref([])

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ keyword: '', page: 1, size: 10 })

// 本地行数统计（空行忽略，与后端一致）
const lineTotal = computed(
  () => rawData.value.split('\n').filter((l) => l.replace(/\r$/, '').trim() !== '').length
)

function loadExample() {
  rawData.value = EXAMPLE
  previewList.value = []
}

function clearAll() {
  rawData.value = ''
  previewList.value = []
}

async function handleParse() {
  if (!lineTotal.value) {
    ElMessage.warning('请输入原始信息')
    return
  }
  parsing.value = true
  try {
    const res = await parseClaude({ rawData: rawData.value })
    previewList.value = res.data || []
    if (!previewList.value.length) {
      ElMessage.warning('未解析出有效数据')
    } else {
      ElMessage.success(`解析成功 ${previewList.value.length} 条`)
    }
  } finally {
    parsing.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    const res = await saveClaude({ rawData: rawData.value })
    ElMessage.success(`已入库 ${res.data.length} 条`)
    rawData.value = ''
    previewList.value = []
    query.page = 1
    await loadList()
  } finally {
    saving.value = false
  }
}

async function loadList() {
  loading.value = true
  try {
    const res = await listClaude({
      keyword: query.keyword || undefined,
      page: query.page,
      size: query.size
    })
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  loadList()
}

async function handleDelete(id) {
  await deleteClaude(id)
  ElMessage.success('删除成功')
  await loadList()
}

function onPageChange(p) {
  query.page = p
  loadList()
}

function onSizeChange(s) {
  query.size = s
  query.page = 1
  loadList()
}

async function copy(text) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

onMounted(loadList)
</script>

<style scoped>
.page-head { margin-bottom: 18px; }
.page-head .eyebrow { display: block; margin-bottom: 6px; }
.page-head h1 { font-size: clamp(24px, 4vw, 32px); margin: 0; color: var(--ink); }
.lede { color: var(--muted); margin: 8px 0 0; max-width: 60ch; }

.stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}
.stat {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: var(--shadow);
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.stat .n { font-size: 26px; color: var(--ink); font-variant-numeric: tabular-nums; line-height: 1.1; }
.stat .k { font-size: 12px; color: var(--muted); }

.section { margin-bottom: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.head-text { display: flex; flex-direction: column; gap: 2px; }
.head-text .eyebrow { display: flex; align-items: center; gap: 7px; }
.head-title { font-weight: 600; font-size: 15px; color: var(--ink); }
.dot { width: 8px; height: 8px; border-radius: 50%; background: var(--accent); display: inline-block; }
.dot.good { background: var(--good); }

/* 字段说明 chip */
.schema { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding: 2px 0; }
.chip {
  display: inline-flex;
  align-items: center;
  font-family: var(--mono);
  font-size: 12.5px;
  font-weight: 500;
  padding: 5px 11px;
  border-radius: 8px;
  border: 1px solid var(--border-strong);
  background: var(--surface-2);
  color: var(--ink);
  white-space: nowrap;
}
.chip .pos { color: var(--faint); font-size: 10px; margin-right: 6px; }
.sep { font-family: var(--mono); color: var(--faint); font-size: 12px; }
.schema-hint { font-size: 12px; color: var(--faint); line-height: 1.6; margin-top: 6px; }
.schema-hint code {
  font-family: var(--mono);
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 0 4px;
}

.toolbar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin: 14px 0 12px; }

.panes { display: grid; grid-template-columns: minmax(0, 420px) minmax(0, 1fr); gap: 16px; }
.pane {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 14px;
  box-shadow: var(--shadow);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}
.pane-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 15px;
  border-bottom: 1px solid var(--border);
}
.pane-head .label { display: flex; align-items: center; gap: 9px; font-weight: 600; font-size: 13px; color: var(--ink); }
.pane-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--accent); flex: none; }
.pane.out .pane-dot { background: var(--good); }
.pane-head .count { font-family: var(--mono); font-size: 11.5px; color: var(--faint); font-variant-numeric: tabular-nums; }
.pane-body { flex: 1; min-height: 0; overflow: auto; padding: 12px 15px; }
.pane-empty { color: var(--faint); font-size: 12.5px; padding: 28px 0; text-align: center; }
.hint { font-size: 11.5px; color: var(--faint); padding: 0 15px 12px; }

.raw-input :deep(.el-textarea__inner) {
  font-family: var(--mono);
  font-size: 12.5px;
  line-height: 1.7;
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none !important;
  color: var(--ink);
  padding: 14px 15px;
  white-space: pre-wrap;
  word-break: break-all;
  overflow: auto;
  tab-size: 2;
}
.raw-input :deep(.el-textarea__inner::placeholder) { color: var(--faint); }
.raw-input :deep(.el-textarea__inner:focus-visible) { outline: 2px solid var(--accent); outline-offset: -2px; }

.cell-val { display: inline-flex; align-items: baseline; gap: 5px; min-width: 0; }
.cell-txt { font-family: var(--mono); font-size: 12px; word-break: break-all; }
.cell-empty { color: var(--faint); opacity: .5; }

.filters { display: flex; align-items: center; }
.pagination { margin-top: 16px; justify-content: flex-end; }
.empty { color: var(--faint); opacity: .5; }
.copy-icon {
  cursor: pointer;
  color: var(--accent);
  margin-left: 4px;
  vertical-align: middle;
  opacity: 0;
  transition: opacity .12s ease;
}
:deep(.el-table__row:hover) .copy-icon { opacity: 1; }
.cell-val .copy-icon { opacity: 1; }

@media (max-width: 1000px) {
  .panes { grid-template-columns: 1fr; }
}
@media (max-width: 720px) {
  .stats { grid-template-columns: 1fr; }
}
</style>
