<template>
  <div class="apple-manage">
    <!-- 页头 -->
    <div class="page-head">
      <span class="eyebrow">Apple</span>
      <h1 class="display">Apple ID 录入</h1>
      <p class="lede">
        按「选或新增 Apple ID → 录谷歌别名邮箱 → 录隐藏邮箱 → 录 HackerOne 邮箱」四步录入。
        一个 Apple ID 下可挂多个隐藏邮箱；两个转发邮箱都可从已录入的邮箱里选，也可直接手输。
      </p>
    </div>

    <!-- 录入 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <div class="head-text">
            <span class="eyebrow"><span class="dot"></span>Entry</span>
            <span class="head-title">录入隐藏邮箱</span>
          </div>
        </div>
      </template>

      <el-form label-width="130px" class="entry-form">
        <!-- ① Apple ID -->
        <el-form-item label="① Apple ID">
          <el-radio-group v-model="idMode" class="type-chips" @change="onIdModeChange">
            <el-radio-button value="select">从已有选</el-radio-button>
            <el-radio-button value="create">新增</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <template v-if="idMode === 'select'">
          <el-form-item label="选择账号">
            <el-select
              v-model="form.appleAccountId"
              filterable
              remote
              clearable
              reserve-keyword
              placeholder="按 Apple ID / 备注搜索已录入的账号"
              :remote-method="searchAccounts"
              :loading="accLoading"
              style="width: 100%; max-width: 460px"
            >
              <el-option
                v-for="a in accountOptions"
                :key="a.id"
                :label="`#${a.id} · ${a.appleId}`"
                :value="a.id"
              >
                <span>{{ a.appleId }}</span>
                <span v-if="a.note" class="opt-note">{{ a.note }}</span>
              </el-option>
            </el-select>
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="Apple ID">
            <el-input v-model="form.appleId" placeholder="xxx@icloud.com / 谷歌 Apple ID xxx@gmail.com" style="max-width: 460px" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="form.note" placeholder="可选：用途、来源、状态等" style="max-width: 460px" />
          </el-form-item>
        </template>

        <!-- ② 谷歌别名邮箱：可选已有、也可直接输入 -->
        <el-form-item label="② 谷歌别名邮箱">
          <el-select
            v-model="form.googleAliasEmail"
            filterable
            remote
            clearable
            allow-create
            default-first-option
            reserve-keyword
            placeholder="xxx+apple@gmail.com；也可从已录入邮箱里选"
            :remote-method="searchRedirect"
            :loading="redirectLoading"
            style="width: 100%; max-width: 460px"
          >
            <el-option v-for="e in redirectOptions" :key="e.id" :label="e.email" :value="e.email">
              <span>{{ e.email }}</span>
              <span class="opt-note">{{ e.emailType }}</span>
            </el-option>
          </el-select>
        </el-form-item>

        <!-- ③ 隐藏邮箱 -->
        <el-form-item label="③ 隐藏邮箱">
          <el-input
            v-model="form.hideEmail"
            placeholder="xxx@privaterelay.appleid.com"
            style="max-width: 460px"
          />
        </el-form-item>

        <!-- ④ HackerOne 邮箱：可选已有、也可直接输入 -->
        <el-form-item label="④ HackerOne 邮箱">
          <el-select
            v-model="form.redirectEmail"
            filterable
            remote
            clearable
            allow-create
            default-first-option
            reserve-keyword
            placeholder="xxx@wearehackerone.com；也可从已录入邮箱里选"
            :remote-method="searchRedirect"
            :loading="redirectLoading"
            style="width: 100%; max-width: 460px"
          >
            <el-option v-for="e in redirectOptions" :key="e.id" :label="e.email" :value="e.email">
              <span>{{ e.email }}</span>
              <span class="opt-note">{{ e.emailType }}</span>
            </el-option>
          </el-select>
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
            <span class="head-title">已录隐藏邮箱</span>
          </div>
          <span class="result-count">{{ total }} 条</span>
        </div>
      </template>

      <div class="filters">
        <el-input
          v-model="query.keyword"
          placeholder="搜索隐藏邮箱 / HackerOne / 谷歌别名"
          clearable
          style="max-width: 280px"
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
          style="max-width: 280px"
          @change="reload"
        >
          <el-option v-for="a in accountOptions" :key="a.id" :label="a.appleId" :value="a.id" />
        </el-select>
        <el-button :loading="loading" @click="reload">查询</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border size="small" style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="googleAliasEmail" label="谷歌别名邮箱" min-width="210">
          <template #default="{ row }">
            <span v-if="row.googleAliasEmail">{{ row.googleAliasEmail }}</span>
            <span v-else class="muted-cell">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="hideEmail" label="隐藏邮箱" min-width="220" />
        <el-table-column prop="redirectEmail" label="HackerOne 邮箱" min-width="210">
          <template #default="{ row }">
            <span v-if="row.redirectEmail">{{ row.redirectEmail }}</span>
            <span v-else class="muted-cell">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="appleId" label="所属 Apple ID" min-width="200" />
        <el-table-column prop="createdBy" label="录入人" width="110" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-popconfirm title="确认删除该记录？" @confirm="handleDelete(row.id)">
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
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listAppleAccounts, listHideEmails, saveHideEmail, deleteHideEmail } from '../api/apple'
import { listEmail } from '../api/email'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const idMode = ref('select')          // select | create
const saving = ref(false)
const form = reactive({
  appleAccountId: null,
  appleId: '',
  note: '',
  hideEmail: '',
  redirectEmail: '',                  // HackerOne 邮箱
  googleAliasEmail: ''
})

/* —— Apple ID 下拉（录入与列表筛选共用选项） —— */
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
  form.note = ''
}

/* —— 重定向邮箱候选：复用已录入的邮箱账号 —— */
const redirectOptions = ref([])
const redirectLoading = ref(false)

async function searchRedirect(keyword) {
  redirectLoading.value = true
  try {
    const res = await listEmail({ keyword: keyword || undefined, page: 1, size: 20 })
    redirectOptions.value = (res.data.records || []).filter((e) => e.email)
  } catch {
    redirectOptions.value = []
  } finally {
    redirectLoading.value = false
  }
}

const canSave = computed(() => {
  const hasId = idMode.value === 'select' ? !!form.appleAccountId : EMAIL_RE.test(form.appleId.trim())
  // 两个转发邮箱都要录
  return hasId
    && EMAIL_RE.test(form.hideEmail.trim())
    && EMAIL_RE.test((form.redirectEmail || '').trim())
    && EMAIL_RE.test((form.googleAliasEmail || '').trim())
})

/* 录入按钮为何不可点 —— 让缺失项对用户可见 */
const disabledReason = computed(() => {
  if (canSave.value) return ''
  if (idMode.value === 'select' && !form.appleAccountId) return '请先选择 Apple ID'
  if (idMode.value === 'create' && !EMAIL_RE.test(form.appleId.trim())) return '请填写完整的 Apple ID 账号'
  if (!EMAIL_RE.test((form.googleAliasEmail || '').trim())) return '请填写谷歌别名邮箱'
  if (!EMAIL_RE.test(form.hideEmail.trim())) return '请填写完整的隐藏邮箱地址'
  if (!EMAIL_RE.test((form.redirectEmail || '').trim())) return '请填写 HackerOne 邮箱'
  return ''
})

async function handleSave() {
  saving.value = true
  try {
    const payload = {
      hideEmail: form.hideEmail.trim(),
      redirectEmail: form.redirectEmail.trim(),
      googleAliasEmail: form.googleAliasEmail.trim()
    }
    if (idMode.value === 'select') {
      payload.appleAccountId = form.appleAccountId
    } else {
      payload.appleId = form.appleId.trim()
      payload.note = form.note.trim()
    }
    await saveHideEmail(payload)
    ElMessage.success('录入成功')
    // 保留 Apple ID 选择，方便连续录同一账号下的多个隐藏邮箱
    form.hideEmail = ''
    form.redirectEmail = ''
    form.googleAliasEmail = ''
    await reload()
  } catch {
    // 错误信息已由 request 拦截器统一提示（含服务端返回原文，如隐藏邮箱重复）
  } finally {
    saving.value = false
  }
}

function resetForm() {
  form.appleAccountId = null
  form.appleId = ''
  form.note = ''
  form.hideEmail = ''
  form.redirectEmail = ''
  form.googleAliasEmail = ''
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
    // 错误提示同上
  }
}

onMounted(() => {
  searchAccounts('')
  load()
})
</script>

<style scoped>
.page-head { margin-bottom: 18px; }
.page-head .eyebrow { display: block; margin-bottom: 6px; }
.page-head h1 { font-size: clamp(24px, 4vw, 32px); margin: 0; color: var(--ink); }
.lede { color: var(--muted); margin: 8px 0 0; max-width: 68ch; line-height: 1.6; }

.section { margin-bottom: 20px; }
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.head-text { display: flex; flex-direction: column; gap: 2px; }
.head-text .eyebrow { display: flex; align-items: center; gap: 7px; }
.head-title { font-weight: 600; font-size: 15px; color: var(--ink); }
.dot { width: 8px; height: 8px; border-radius: 50%; background: var(--accent); display: inline-block; }
.dot.good { background: var(--good); }
.result-count {
  font-family: var(--mono);
  font-size: 12px;
  color: var(--faint);
  font-variant-numeric: tabular-nums;
}

/* 模式切换 chip 风格（与邮箱管理 / 邮件取件一致） */
.type-chips :deep(.el-radio-button__inner) {
  border-radius: 8px !important;
  border: 1px solid var(--border-strong);
  font-family: var(--mono);
  font-size: 12.5px;
  margin-right: 8px;
  box-shadow: none !important;
  background: var(--surface-2);
  color: var(--muted);
}
.type-chips :deep(.el-radio-button:first-child .el-radio-button__inner) { border-left: 1px solid var(--border-strong); }
.type-chips :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: var(--accent-soft);
  border-color: var(--accent);
  color: var(--accent);
}

.opt-note {
  float: right;
  margin-left: 16px;
  font-size: 11.5px;
  color: var(--faint);
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 8px;
}
.disabled-reason {
  font-size: 12px;
  color: var(--warn, #b8860b);
}

.filters {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.muted-cell { color: var(--faint); }
.pagination { margin-top: 14px; justify-content: flex-end; }
</style>
