<template>
  <div class="tab-pane">
    <!-- 录入 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <div class="head-text">
            <span class="eyebrow"><span class="dot"></span>Forward</span>
            <span class="head-title">录入转发邮箱</span>
          </div>
        </div>
      </template>

      <el-form label-width="120px">
        <el-form-item label="Apple ID">
          <el-radio-group v-model="idMode" class="type-chips" @change="onIdModeChange">
            <el-radio-button value="select">从已有选</el-radio-button>
            <el-radio-button value="create">新增</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="idMode === 'select'" label="选择账号">
          <el-select
            v-model="form.appleAccountId"
            filterable
            remote
            clearable
            reserve-keyword
            placeholder="按 Apple ID / 备注搜索"
            :remote-method="searchAccounts"
            :loading="accLoading"
            style="width: 100%; max-width: 460px"
          >
            <el-option v-for="a in accountOptions" :key="a.id" :label="a.appleId" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="Apple ID">
          <el-input v-model="form.appleId" placeholder="xxx@icloud.com" style="max-width: 460px" />
        </el-form-item>

        <el-form-item label="转发邮箱">
          <el-input v-model="form.forwardEmail" placeholder="转发到哪个邮箱，如 xxx@gmail.com" style="max-width: 460px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.note" placeholder="可选" style="max-width: 460px" />
        </el-form-item>
        <el-form-item label="设为当前">
          <el-switch v-model="form.setCurrent" />
          <span class="field-tip">同一个 Apple ID 下只会有一个当前转发邮箱</span>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button type="primary" :loading="saving" :disabled="!canSave" @click="handleSave">
          {{ saving ? '录入中…' : '录入' }}
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
            <span class="head-title">已录转发邮箱</span>
          </div>
          <span class="result-count">{{ total }} 条</span>
        </div>
      </template>

      <div class="filters">
        <el-input
          v-model="query.keyword"
          placeholder="搜索转发邮箱 / 备注"
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
        <el-table-column prop="appleId" label="Apple ID" min-width="190" />
        <el-table-column prop="forwardEmail" label="转发邮箱" min-width="210" />
        <el-table-column label="当前" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isCurrent" type="success" size="small">当前</el-tag>
            <el-button v-else text size="small" @click="handleSetCurrent(row.id)">设为当前</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="hideEmailCount" label="隐藏邮箱数" width="100" align="center" />
        <el-table-column prop="note" label="备注" min-width="140">
          <template #default="{ row }">
            <span v-if="row.note">{{ row.note }}</span>
            <span v-else class="muted-cell">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="录入人" width="100" />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" :loading="checkingId === row.id" @click="handleCheck(row)">检测</el-button>
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
    <el-dialog v-model="editVisible" title="编辑转发邮箱" width="500px">
      <el-form label-width="100px">
        <el-form-item label="Apple ID">
          <span class="mono-text">{{ editForm.appleId || '—' }}</span>
        </el-form-item>
        <el-form-item label="转发邮箱">
          <el-input v-model="editForm.forwardEmail" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.note" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" :disabled="!EMAIL_RE.test(editForm.forwardEmail.trim())" @click="handleUpdate">
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
  listForwardEmails,
  saveForwardEmail,
  setCurrentForward,
  updateForwardEmail,
  deleteForwardEmail,
  checkForwardEmail
} from '../../api/apple'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const idMode = ref('select')
const saving = ref(false)
const form = reactive({ appleAccountId: null, appleId: '', forwardEmail: '', note: '', setCurrent: false })

/* —— Apple ID 下拉 —— */
const accountOptions = ref([])
const accLoading = ref(false)

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

function onIdModeChange() {
  form.appleAccountId = null
  form.appleId = ''
}

const canSave = computed(() => {
  const hasId = idMode.value === 'select' ? !!form.appleAccountId : EMAIL_RE.test(form.appleId.trim())
  return hasId && EMAIL_RE.test(form.forwardEmail.trim())
})

const disabledReason = computed(() => {
  if (canSave.value) return ''
  if (idMode.value === 'select' && !form.appleAccountId) return '请先选择 Apple ID'
  if (idMode.value === 'create' && !EMAIL_RE.test(form.appleId.trim())) return '请填写完整的 Apple ID 账号'
  return '请填写完整的转发邮箱地址'
})

async function handleSave() {
  saving.value = true
  try {
    const payload = {
      forwardEmail: form.forwardEmail.trim(),
      note: form.note.trim(),
      setCurrent: form.setCurrent
    }
    if (idMode.value === 'select') payload.appleAccountId = form.appleAccountId
    else payload.appleId = form.appleId.trim()

    await saveForwardEmail(payload)
    ElMessage.success('录入成功')
    form.forwardEmail = ''
    form.note = ''
    await reload()
  } catch {
    // 错误信息已由 request 拦截器统一提示
  } finally {
    saving.value = false
  }
}

function resetForm() {
  form.appleAccountId = null
  form.appleId = ''
  form.forwardEmail = ''
  form.note = ''
  form.setCurrent = false
}

/* —— 列表 —— */
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ appleAccountId: null, keyword: '', page: 1, size: 10 })

async function load() {
  loading.value = true
  try {
    const res = await listForwardEmails({
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

async function handleSetCurrent(id) {
  try {
    await setCurrentForward(id)
    ElMessage.success('已设为当前转发邮箱')
    await load()
  } catch {
    // 同上
  }
}

/** 探活：只看这个转发邮箱现在能不能正常取件，结果同时记入日志。 */
const checkingId = ref(null)

async function handleCheck(row) {
  checkingId.value = row.id
  try {
    const res = await checkForwardEmail({ forwardEmailId: row.id })
    const r = (res.data || [])[0] || {}
    if (r.success) ElMessage.success(`${row.forwardEmail} 取件正常`)
    else ElMessage.warning(`${row.forwardEmail} 异常：${r.message || '未知原因'}`)
  } catch {
    // 错误信息已由 request 拦截器统一提示
  } finally {
    checkingId.value = null
  }
}

async function handleDelete(id) {
  try {
    await deleteForwardEmail(id)
    ElMessage.success('已删除')
    await load()
  } catch {
    // 仍被隐藏邮箱引用时服务端会拒绝并给出条数
  }
}

/* —— 编辑 —— */
const editVisible = ref(false)
const editSaving = ref(false)
const editForm = reactive({ id: null, appleId: '', forwardEmail: '', note: '' })

function openEdit(row) {
  editForm.id = row.id
  editForm.appleId = row.appleId || ''
  editForm.forwardEmail = row.forwardEmail || ''
  editForm.note = row.note || ''
  editVisible.value = true
}

async function handleUpdate() {
  editSaving.value = true
  try {
    await updateForwardEmail(editForm.id, {
      forwardEmail: editForm.forwardEmail.trim(),
      note: editForm.note.trim()
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
