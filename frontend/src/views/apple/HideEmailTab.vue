<template>
  <div class="tab-pane">
    <!-- 录入 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <div class="head-text">
            <span class="eyebrow"><span class="dot"></span>Hide</span>
            <span class="head-title">录入隐藏邮箱</span>
          </div>
        </div>
      </template>

      <el-form label-width="120px">
        <el-form-item label="录入方式">
          <el-radio-group v-model="entryMode" class="type-chips">
            <el-radio-button value="single">单条</el-radio-button>
            <el-radio-button value="batch">批量粘贴</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="Apple ID">
          <el-select
            v-model="form.appleAccountId"
            filterable
            remote
            clearable
            reserve-keyword
            placeholder="选择已录入的 Apple ID"
            :remote-method="searchAccounts"
            :loading="accLoading"
            style="width: 100%; max-width: 460px"
            @change="onAccountChange"
          >
            <el-option v-for="a in accountOptions" :key="a.id" :label="a.appleId" :value="a.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="所属转发邮箱">
          <el-select
            v-model="form.forwardEmailId"
            clearable
            placeholder="先选 Apple ID，默认带出当前转发邮箱"
            :loading="forwardLoading"
            style="width: 100%; max-width: 460px"
          >
            <el-option
              v-for="f in forwardOptions"
              :key="f.id"
              :label="f.isCurrent ? `${f.forwardEmail}（当前）` : f.forwardEmail"
              :value="f.id"
            />
          </el-select>
          <span v-if="form.appleAccountId && !forwardOptions.length" class="field-tip">
            该 Apple ID 还没录转发邮箱，可先去「转发邮箱」页录入
          </span>
        </el-form-item>

        <el-form-item v-if="entryMode === 'single'" label="隐藏邮箱">
          <el-input v-model="form.hideEmail" placeholder="xxx@privaterelay.appleid.com" style="max-width: 460px" />
        </el-form-item>

        <template v-else>
          <el-form-item label="分隔符">
            <el-radio-group v-model="sepKey" class="type-chips">
              <el-radio-button value="line">每行一个</el-radio-button>
              <el-radio-button value="comma">逗号</el-radio-button>
              <el-radio-button value="space">空格</el-radio-button>
              <el-radio-button value="dash">----</el-radio-button>
              <el-radio-button value="custom">自定义</el-radio-button>
            </el-radio-group>
            <el-input v-if="sepKey === 'custom'" v-model="customSep" placeholder="如 |" style="width: 120px; margin-left: 8px" />
          </el-form-item>

          <el-form-item label="批量内容">
            <div class="batch-block">
              <el-input
                v-model="batchRaw"
                type="textarea"
                :rows="7"
                resize="vertical"
                class="batch-input"
                placeholder="每行一个隐藏邮箱；选了其他分隔符时，一行里也可以放多个"
              />
              <div class="batch-hint">
                整批都挂在上面选的 Apple ID 与转发邮箱下。已录过的隐藏邮箱会自动跳过。
              </div>
              <div v-if="batchRows.length" class="batch-preview">
                <div class="batch-stat">
                  共 {{ batchRows.length }} 个 ·
                  <span class="ok">可录入 {{ batchValid.length }}</span>
                  <span v-if="batchBadCount" class="bad"> · 有问题 {{ batchBadCount }}</span>
                </div>
                <el-table :data="batchRows" border size="small" max-height="240" style="width: 100%">
                  <el-table-column type="index" label="#" width="46" align="center" />
                  <el-table-column prop="email" label="隐藏邮箱" min-width="240" />
                  <el-table-column label="状态" width="116">
                    <template #default="{ row }">
                      <el-tag v-if="row.issue" type="warning" size="small" effect="plain">{{ row.issue }}</el-tag>
                      <el-tag v-else type="success" size="small" effect="plain">可录入</el-tag>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </div>
          </el-form-item>
        </template>
      </el-form>

      <div class="toolbar">
        <el-button v-if="entryMode === 'single'" type="primary" :loading="saving" :disabled="!canSave" @click="handleSave">
          {{ saving ? '录入中…' : '录入' }}
        </el-button>
        <el-button v-else type="primary" :loading="saving" :disabled="!canSaveBatch" @click="handleSaveBatch">
          {{ saving ? '录入中…' : `批量录入 ${batchValid.length} 条` }}
        </el-button>
        <el-button :disabled="saving" @click="resetForm">清空</el-button>
        <span v-if="disabledReason" class="disabled-reason">{{ disabledReason }}</span>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <div class="head-text">
            <span class="eyebrow"><span class="dot good"></span>Records</span>
            <span class="head-title">已录隐藏邮箱</span>
          </div>
          <span class="result-count">{{ total }} 条</span>
        </div>
      </template>

      <div class="filters">
        <el-input
          v-model="query.keyword"
          placeholder="搜索隐藏邮箱"
          clearable
          style="max-width: 260px"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-select
          v-model="query.appleAccountId"
          filterable
          remote
          clearable
          reserve-keyword
          placeholder="按 Apple ID 筛选"
          :remote-method="searchAccounts"
          :loading="accLoading"
          style="max-width: 260px"
          @change="reload"
        >
          <el-option v-for="a in accountOptions" :key="a.id" :label="a.appleId" :value="a.id" />
        </el-select>
        <el-button :loading="loading" @click="reload">查询</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border size="small" style="width: 100%">
        <el-table-column prop="id" label="ID" width="66" />
        <el-table-column prop="hideEmail" label="隐藏邮箱" min-width="220" />
        <el-table-column prop="appleId" label="Apple ID" min-width="180" />
        <el-table-column prop="forwardEmail" label="所属转发邮箱" min-width="190">
          <template #default="{ row }">
            <span v-if="row.forwardEmail">{{ row.forwardEmail }}</span>
            <span v-else class="muted-cell">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="hackerEmail" label="黑客邮箱" min-width="190">
          <template #default="{ row }">
            <span v-if="row.hackerEmail">{{ row.hackerEmail }}</span>
            <span v-else class="muted-cell">未绑定</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="录入人" width="100" />
        <el-table-column label="操作" width="122" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该记录？" :width="190" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button text type="danger" size="small">删除</el-button>
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

    <!-- 编辑 -->
    <el-dialog v-model="editVisible" title="编辑隐藏邮箱" width="500px">
      <el-form label-width="110px">
        <el-form-item label="Apple ID">
          <span class="mono-text">{{ editForm.appleId || '—' }}</span>
        </el-form-item>
        <el-form-item label="隐藏邮箱">
          <el-input v-model="editForm.hideEmail" />
        </el-form-item>
        <el-form-item label="所属转发邮箱">
          <el-select v-model="editForm.forwardEmailId" clearable style="width: 100%">
            <el-option
              v-for="f in editForwardOptions"
              :key="f.id"
              :label="f.isCurrent ? `${f.forwardEmail}（当前）` : f.forwardEmail"
              :value="f.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" :disabled="!EMAIL_RE.test(editForm.hideEmail.trim())" @click="handleUpdate">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listAppleAccounts,
  listForwardEmailsOf,
  listHideEmails,
  saveHideEmail,
  saveHideEmailBatch,
  updateHideEmail,
  deleteHideEmail
} from '../../api/apple'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const entryMode = ref('single')
const saving = ref(false)
const form = reactive({ appleAccountId: null, forwardEmailId: null, hideEmail: '' })

/* —— Apple ID 与其转发邮箱 —— */
const accountOptions = ref([])
const accLoading = ref(false)
const forwardOptions = ref([])
const forwardLoading = ref(false)

async function searchAccounts(keyword) {
  accLoading.value = true
  try {
    const res = await listAppleAccounts({ keyword: keyword || undefined, page: 1, size: 20 })
    accountOptions.value = res.data.records || []
  } catch {
    accountOptions.value = []
  } finally {
    accLoading.value = false
  }
}

async function loadForwards(appleAccountId) {
  if (!appleAccountId) {
    forwardOptions.value = []
    return []
  }
  forwardLoading.value = true
  try {
    const res = await listForwardEmailsOf(appleAccountId)
    forwardOptions.value = res.data || []
    return forwardOptions.value
  } catch {
    forwardOptions.value = []
    return []
  } finally {
    forwardLoading.value = false
  }
}

async function onAccountChange(id) {
  form.forwardEmailId = null
  const list = await loadForwards(id)
  // 默认选中当前转发邮箱
  const current = list.find((f) => f.isCurrent)
  if (current) form.forwardEmailId = current.id
}

/* —— 批量解析 —— */
const sepKey = ref('line')
const customSep = ref('|')
const batchRaw = ref('')

const SEPARATORS = { comma: /[,，]/, space: /\s+/, dash: /-{3,}/ }
function splitLine(line) {
  if (sepKey.value === 'line') return [line]
  if (sepKey.value === 'custom') {
    const s = customSep.value.trim()
    if (!s) return [line]
    return line.split(new RegExp(s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')))
  }
  return line.split(SEPARATORS[sepKey.value])
}

const batchRows = computed(() => {
  const seen = new Set()
  const out = []
  for (const line of batchRaw.value.split('\n')) {
    for (const raw of splitLine(line.trim())) {
      const email = raw.trim()
      if (!email) continue
      let issue = ''
      if (!EMAIL_RE.test(email)) issue = '格式错'
      else if (seen.has(email.toLowerCase())) issue = '本批重复'
      else seen.add(email.toLowerCase())
      out.push({ email, issue })
    }
  }
  return out
})
const batchValid = computed(() => batchRows.value.filter((r) => !r.issue))
const batchBadCount = computed(() => batchRows.value.length - batchValid.value.length)

/* —— 提交 —— */
const canSave = computed(() => !!form.appleAccountId && EMAIL_RE.test(form.hideEmail.trim()))
const canSaveBatch = computed(() => !!form.appleAccountId && batchValid.value.length > 0)

const disabledReason = computed(() => {
  if (entryMode.value === 'batch' ? canSaveBatch.value : canSave.value) return ''
  if (!form.appleAccountId) return '请先选择 Apple ID'
  if (entryMode.value === 'batch') {
    return batchRows.value.length ? '没有可录入的隐藏邮箱' : '请粘贴要批量录入的内容'
  }
  return '请填写完整的隐藏邮箱地址'
})

async function handleSave() {
  saving.value = true
  try {
    await saveHideEmail({
      appleAccountId: form.appleAccountId,
      forwardEmailId: form.forwardEmailId,
      hideEmail: form.hideEmail.trim()
    })
    ElMessage.success('录入成功')
    form.hideEmail = ''
    await reload()
  } catch {
    // 错误信息已由 request 拦截器统一提示
  } finally {
    saving.value = false
  }
}

async function handleSaveBatch() {
  saving.value = true
  try {
    const res = await saveHideEmailBatch({
      appleAccountId: form.appleAccountId,
      forwardEmailId: form.forwardEmailId,
      hideEmails: batchValid.value.map((r) => r.email)
    })
    const { saved = 0, skipped = 0 } = res.data || {}
    ElMessage.success(skipped ? `成功 ${saved} 条，跳过 ${skipped} 条（已存在）` : `成功录入 ${saved} 条`)
    batchRaw.value = ''
    await reload()
  } catch {
    // 同上
  } finally {
    saving.value = false
  }
}

function resetForm() {
  form.appleAccountId = null
  form.forwardEmailId = null
  form.hideEmail = ''
  batchRaw.value = ''
  forwardOptions.value = []
}

/* —— 列表 —— */
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ appleAccountId: null, keyword: '', page: 1, size: 10 })

async function load() {
  loading.value = true
  try {
    const res = await listHideEmails({
      appleAccountId: query.appleAccountId || undefined,
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

async function handleDelete(id) {
  try {
    await deleteHideEmail(id)
    ElMessage.success('已删除')
    await load()
  } catch {
    // 已绑黑客邮箱时服务端会拒绝并提示先解绑
  }
}

/* —— 编辑 —— */
const editVisible = ref(false)
const editSaving = ref(false)
const editForwardOptions = ref([])
const editForm = reactive({ id: null, appleId: '', hideEmail: '', forwardEmailId: null })

async function openEdit(row) {
  editForm.id = row.id
  editForm.appleId = row.appleId || ''
  editForm.hideEmail = row.hideEmail || ''
  editForm.forwardEmailId = row.forwardEmailId || null
  editVisible.value = true
  try {
    const res = await listForwardEmailsOf(row.appleAccountId)
    editForwardOptions.value = res.data || []
  } catch {
    editForwardOptions.value = []
  }
}

async function handleUpdate() {
  editSaving.value = true
  try {
    await updateHideEmail(editForm.id, {
      hideEmail: editForm.hideEmail.trim(),
      forwardEmailId: editForm.forwardEmailId
    })
    ElMessage.success('已保存')
    editVisible.value = false
    await load()
  } catch {
    // 同上
  } finally {
    editSaving.value = false
  }
}

onMounted(() => {
  searchAccounts('')
  load()
})
</script>

<style scoped>
@import '../../styles/apple-tab.css';
</style>
