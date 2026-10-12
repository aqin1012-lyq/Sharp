<template>
  <div class="supplier-manage">
    <!-- 页头 -->
    <div class="page-head">
      <span class="eyebrow">Suppliers</span>
      <h1 class="display">供应商管理</h1>
      <p class="lede">按「IP : 端口 : 用户名 : 密码 : 地区 : 到期时间」格式批量粘贴，自动拆分字段入库。</p>
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
            <span class="head-title">供应商录入</span>
          </div>
        </div>
      </template>

      <!-- 字段顺序：可拖拽调整，第 N 段写入第 N 个字段 -->
      <div class="schema-row">
        <div ref="schemaEl" class="schema">
          <template v-for="(f, i) in orderedFields" :key="f.key">
            <span
              class="chip"
              :class="{ dragging: dragIndex === i, over: overIndex === i && dragIndex !== i && dragIndex !== -1 }"
              draggable="true"
              tabindex="0"
              :title="`${f.label}：拖拽调整顺序，或聚焦后按 ← / →`"
              @dragstart="onDragStart(i, $event)"
              @dragover="onDragOver(i, $event)"
              @dragleave="onDragLeave(i)"
              @drop.prevent="onDrop(i)"
              @dragend="onDragEnd"
              @keydown="onChipKeydown(i, $event)"
            >
              <el-icon class="grip"><Rank /></el-icon>
              <span class="pos">{{ i + 1 }}</span>{{ f.label }}
            </span>
            <span v-if="i < orderedFields.length - 1" class="sep">:</span>
          </template>
        </div>
        <el-button v-if="isCustomOrder" text size="small" class="reset-btn" @click="resetOrder">恢复默认</el-button>
      </div>
      <div class="schema-hint">
        拖拽色块调整顺序（也可聚焦后按 ← / →）；一行一条，按冒号（<code>:</code>）切段，
        第 N 段写入上面第 N 个字段；段数不够的字段留空，最后一段吞掉剩余原文（到期时间里的 <code>HH:mm</code> 不会被拆开）。
        <span v-if="isCustomOrder" class="custom-flag">已自定义</span>
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
                v-for="col in orderedFields"
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
            <span class="eyebrow"><span class="dot good"></span>Suppliers</span>
            <span class="head-title">供应商列表</span>
          </div>
          <div class="filters">
            <el-input
              v-model="query.keyword"
              placeholder="按 IP / 用户名 / 地区搜索"
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
          v-for="col in FIELD_DEFS"
          :key="col.key"
          :prop="col.key"
          :label="col.label"
          :min-width="col.width || 140"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span v-if="row[col.key]" class="data-cell" :class="{ expired: col.key === 'expireTime' && isExpired(row[col.key]) }">
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
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Rank } from '@element-plus/icons-vue'
import { parseSupplier, checkSupplier, saveSupplier, listSupplier, deleteSupplier } from '../api/supplier'

// 字段定义：key 与后端 Supplier 同名；FIELD_DEFS 是默认顺序，列表表格按此固定展示
const FIELD_DEFS = [
  { key: 'ip', label: '供应商 IP', width: 150 },
  { key: 'port', label: '端口号', width: 90 },
  { key: 'username', label: '用户名', width: 160 },
  { key: 'password', label: '密码', width: 160 },
  { key: 'region', label: '用户地区', width: 110 },
  { key: 'expireTime', label: '到期时间', width: 170 }
]
const DEFAULT_KEYS = FIELD_DEFS.map((f) => f.key)

/* —— 字段顺序：可拖拽调整，记在 localStorage —— */
const ORDER_KEY = 'sharp:supplier-field-order:v1'
const samePermutation = (a, b) =>
  Array.isArray(a) && a.length === b.length && [...a].sort().join() === [...b].sort().join()

function loadOrder() {
  try {
    const saved = JSON.parse(localStorage.getItem(ORDER_KEY) || 'null')
    // 只接受“同一批字段换了顺序”的记录，字段增删过就退回默认
    if (samePermutation(saved, DEFAULT_KEYS)) return saved
  } catch {
    // 存储损坏 / 不可用时用默认顺序
  }
  return [...DEFAULT_KEYS]
}

const fieldOrder = ref(loadOrder())
const schemaEl = ref(null)
const dragIndex = ref(-1)
const overIndex = ref(-1)

const byKey = new Map(FIELD_DEFS.map((f) => [f.key, f]))
// 解析预览列跟着拖拽后的顺序走
const orderedFields = computed(() => fieldOrder.value.map((k) => byKey.get(k)))
const isCustomOrder = computed(() => fieldOrder.value.join() !== DEFAULT_KEYS.join())

function persistOrder() {
  try {
    localStorage.setItem(ORDER_KEY, JSON.stringify(fieldOrder.value))
  } catch {
    // 无痕模式等场景下写不进去，不影响本次使用
  }
}

function moveField(from, to) {
  const list = fieldOrder.value
  if (from < 0 || to < 0 || from >= list.length || to >= list.length || from === to) {
    return
  }
  list.splice(to, 0, list.splice(from, 1)[0])
  persistOrder()
  // 顺序变了，之前的预览结果已经对不上
  previewList.value = []
}

function onDragStart(i, e) {
  dragIndex.value = i
  e.dataTransfer.effectAllowed = 'move'
  e.dataTransfer.setData('text/plain', String(i))
}
function onDragOver(i, e) {
  e.preventDefault()
  e.dataTransfer.dropEffect = 'move'
  overIndex.value = i
}
function onDragLeave(i) {
  if (overIndex.value === i) overIndex.value = -1
}
function onDrop(i) {
  moveField(dragIndex.value, i)
  onDragEnd()
}
function onDragEnd() {
  dragIndex.value = -1
  overIndex.value = -1
}
function onChipKeydown(i, e) {
  if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return
  const to = e.key === 'ArrowLeft' ? i - 1 : i + 1
  if (to < 0 || to >= fieldOrder.value.length) return
  e.preventDefault()
  moveField(i, to)
  // 焦点跟着色块一起移动，方便连续按键调整
  nextTick(() => schemaEl.value?.querySelectorAll('.chip')[to]?.focus())
}
function resetOrder() {
  fieldOrder.value = [...DEFAULT_KEYS]
  persistOrder()
  previewList.value = []
}

/** 当前字段顺序随请求发给后端，第 N 段写入第 N 个字段。 */
function buildPayload() {
  return { rawData: rawData.value, fields: [...fieldOrder.value] }
}

const placeholder =
  '示例：\n203.0.113.10:1080:user01:Pass123:US:2026-12-31'
const EXAMPLE = [
  '203.0.113.10:1080:alice:Pa55w0rd:US:2026-12-31',
  '198.51.100.24:8000:bob:Bob#2024:JP:2027-01-15 23:59:59'
].join('\n')

/** 到期时间能被识别且早于当前时间则视为已过期（格式不规范的不标红）。 */
function isExpired(v) {
  const t = Date.parse(String(v).trim().replace(' ', 'T'))
  return !Number.isNaN(t) && t < Date.now()
}

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
    const res = await parseSupplier(buildPayload())
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
  if (!lineTotal.value) {
    ElMessage.warning('请输入原始信息')
    return
  }
  saving.value = true
  try {
    // 先查重：六字段完全一致（含批内重复）即算重复
    const chk = (await checkSupplier(buildPayload())).data
    let skipDuplicates = false

    if (chk.dupCount > 0) {
      // 弹框三选一：仅入库新数据 / 全部入库(含重复) / 取消
      const preview = (chk.duplicates || []).slice(0, 5).join('\n')
      const more = chk.duplicates && chk.duplicates.length > 5
        ? `\n…另有 ${chk.duplicates.length - 5} 条` : ''
      let action
      try {
        await ElMessageBox.confirm(
          `检测到 ${chk.dupCount} 条与库中已有记录完全重复，新数据 ${chk.newCount} 条。\n\n重复行：\n${preview}${more}`,
          '发现重复数据',
          {
            confirmButtonText: `仅入库新数据（${chk.newCount}）`,
            cancelButtonText: '全部入库（含重复）',
            distinguishCancelAndClose: true,
            type: 'warning',
            customClass: 'supplier-dup-box'
          }
        )
        action = 'confirm' // 仅入库新数据
      } catch (e) {
        action = e // 'cancel'（全部入库）或 'close'（取消）
      }

      if (action === 'close') {
        ElMessage.info('已取消保存')
        return
      }
      skipDuplicates = action === 'confirm'
      if (skipDuplicates && chk.newCount === 0) {
        ElMessage.info('没有新数据可入库')
        return
      }
    }

    const res = await saveSupplier({ ...buildPayload(), skipDuplicates })
    const saved = res.data.length
    const skipped = chk.total - saved
    ElMessage.success(skipped > 0 ? `已入库 ${saved} 条，跳过 ${skipped} 条重复` : `已入库 ${saved} 条`)
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
    const res = await listSupplier({
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
  await deleteSupplier(id)
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

/* 字段顺序 chip（可拖拽排序） */
.schema-row { display: flex; align-items: flex-start; gap: 8px; }
.schema { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding: 2px 0; flex: 1; min-width: 0; }
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
  cursor: grab;
  user-select: none;
  transition: border-color .12s ease, box-shadow .12s ease, opacity .12s ease, transform .12s ease;
}
.chip:hover { border-color: var(--accent); }
.chip:focus-visible { outline: none; border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.chip:active { cursor: grabbing; }
.chip.dragging { opacity: .4; }
.chip.over { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); transform: translateY(-1px); }
.chip .grip { font-size: 11px; color: var(--faint); margin-right: 6px; }
.chip .pos { color: var(--faint); font-size: 10px; margin-right: 6px; }
.sep { font-family: var(--mono); color: var(--faint); font-size: 12px; user-select: none; }
.reset-btn { flex: none; }
.schema-hint { font-size: 12px; color: var(--faint); line-height: 1.6; margin-top: 6px; }
.schema-hint code {
  font-family: var(--mono);
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 0 4px;
}
.custom-flag {
  font-family: var(--mono);
  font-size: 11px;
  color: var(--accent);
  border: 1px solid var(--accent);
  background: var(--accent-soft);
  border-radius: 6px;
  padding: 1px 6px;
  margin-left: 6px;
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
.data-cell.expired { color: var(--bad); }
.cell-val .copy-icon { opacity: 1; }

@media (max-width: 1000px) {
  .panes { grid-template-columns: 1fr; }
}
@media (max-width: 720px) {
  .stats { grid-template-columns: 1fr; }
}
</style>

<!-- 非 scoped：ElMessageBox 被 teleport 到 body，scoped 选择器匹配不到 -->
<style>
.supplier-dup-box .el-message-box__message {
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 260px;
  overflow: auto;
  font-size: 13px;
  line-height: 1.6;
}
</style>
