<template>
  <div class="mail-reader">
    <!-- 页头 -->
    <div class="page-head">
      <span class="eyebrow">Mailbox</span>
      <h1 class="display">邮件取件</h1>
      <p class="lede">
        用 Outlook 的 refreshToken + clientId 通过微软 OAuth2 实时拉取收件箱 / 垃圾箱的最新邮件，并自动提取验证码。
        凭据可粘贴原始串、从已入库账号选择，或手动填写。
      </p>
    </div>

    <!-- 凭据 & 取件 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <div class="head-text">
            <span class="eyebrow"><span class="dot"></span>Credentials</span>
            <span class="head-title">凭据来源</span>
          </div>
        </div>
      </template>

      <el-form label-width="90px" class="entry-form">
        <el-form-item label="来源">
          <el-radio-group v-model="source" class="type-chips">
            <el-radio-button value="paste">粘贴原始串</el-radio-button>
            <el-radio-button value="account">从已存账号</el-radio-button>
            <el-radio-button value="manual">手动填写</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <!-- 粘贴原始串 -->
        <template v-if="source === 'paste'">
          <el-form-item label="原始串">
            <div class="paste-block">
              <el-input
                v-model="rawStr"
                type="textarea"
                :rows="3"
                resize="vertical"
                class="raw-input"
                placeholder="邮箱----密码----refreshToken----clientId----说明----cookie----值"
              />
              <div class="schema-hint">
                按 “----” 拆分：第 1 段为<b>邮箱</b>、第 3 段为 <b>refreshToken</b>、第 4 段为 <b>clientId</b>（与邮箱管理里 Outlook 模板一致）。
                <el-button text size="small" @click="loadPasteExample">载入示例</el-button>
              </div>
              <div v-if="rawStr.trim()" class="parse-echo">
                <span class="echo-item"><i>邮箱</i>{{ parsed.email || '—' }}</span>
                <span class="echo-item"><i>refreshToken</i>{{ mask(parsed.refreshToken) }}</span>
                <span class="echo-item"><i>clientId</i>{{ parsed.clientId || '—' }}</span>
              </div>
            </div>
          </el-form-item>
        </template>

        <!-- 从已存账号 -->
        <template v-else-if="source === 'account'">
          <el-form-item label="选择账号">
            <el-select
              v-model="accountId"
              filterable
              remote
              clearable
              reserve-keyword
              placeholder="按邮箱 / 备用邮箱 / UUID 搜索已入库的 Outlook 账号"
              :remote-method="searchAccounts"
              :loading="accLoading"
              style="width: 100%; max-width: 460px"
              @change="onAccountChange"
            >
              <el-option
                v-for="a in accountOptions"
                :key="a.id"
                :label="`#${a.id} · ${a.email}`"
                :value="a.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="accountId" label="已选">
            <span class="parse-echo">
              <span class="echo-item"><i>邮箱</i>{{ parsed.email || '—' }}</span>
              <span class="echo-item"><i>refreshToken</i>{{ mask(parsed.refreshToken) }}</span>
              <span class="echo-item"><i>clientId</i>{{ parsed.clientId || '—' }}</span>
            </span>
          </el-form-item>
        </template>

        <!-- 手动填写 -->
        <template v-else>
          <el-form-item label="邮箱">
            <el-input v-model="manual.email" placeholder="xxx@outlook.com" style="max-width: 460px" />
          </el-form-item>
          <el-form-item label="refreshToken">
            <el-input v-model="manual.refreshToken" placeholder="M.C5..." style="max-width: 460px" />
          </el-form-item>
          <el-form-item label="clientId">
            <el-input v-model="manual.clientId" placeholder="9e5f94bc-..." style="max-width: 460px" />
          </el-form-item>
        </template>

        <el-form-item label="文件夹">
          <el-radio-group v-model="folder" class="type-chips">
            <el-radio-button value="all">全部</el-radio-button>
            <el-radio-button value="inbox">收件箱</el-radio-button>
            <el-radio-button value="junk">垃圾箱</el-radio-button>
          </el-radio-group>
          <span class="count-field">
            条数
            <el-input-number v-model="limit" :min="1" :max="30" size="small" controls-position="right" />
          </span>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button type="primary" :loading="loading" :disabled="!canFetch" @click="handleFetch">
          {{ loading ? '取件中…' : '取件' }}
        </el-button>
        <el-button :disabled="loading" @click="clearAll">清空</el-button>
        <span class="spacer"></span>
        <span v-if="fetchedAt" class="fetched-at">上次取件：{{ fetchedAt }}</span>
      </div>
    </el-card>

    <!-- 结果 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <div class="head-text">
            <span class="eyebrow"><span class="dot good"></span>Inbox</span>
            <span class="head-title">取件结果</span>
          </div>
          <span class="result-count">{{ messages.length }} 封</span>
        </div>
      </template>

      <!-- 最新验证码高亮 -->
      <div v-if="latestCode" class="code-hero">
        <div class="code-hero-label">最新验证码</div>
        <div class="code-hero-val mono">{{ latestCode }}</div>
        <el-button size="small" @click="copyText(latestCode, '已复制验证码')">复制</el-button>
        <span class="code-hero-from">来自：{{ codeFrom }}</span>
      </div>

      <!-- 邮件列表 -->
      <div v-if="messages.length" class="mail-list">
        <div v-for="(m, i) in messages" :key="i" class="mail-item">
          <div class="mail-row" @click="toggle(i)">
            <el-icon class="chev" :class="{ open: expanded[i] }"><ArrowRight /></el-icon>
            <div class="mail-main">
              <div class="mail-top">
                <span class="mail-subject">{{ m.subject || '（无主题）' }}</span>
                <el-tag :type="m.folder === '垃圾箱' ? 'warning' : 'info'" size="small" effect="plain">
                  {{ m.folder }}
                </el-tag>
                <span
                  v-if="m.verifyCode"
                  class="code-badge mono"
                  title="点击复制验证码"
                  @click.stop="copyText(m.verifyCode, '已复制验证码')"
                >
                  {{ m.verifyCode }}
                  <el-icon><CopyDocument /></el-icon>
                </span>
              </div>
              <div class="mail-meta">
                <span class="mail-from">{{ m.fromName ? `${m.fromName} <${m.from}>` : (m.from || '未知发件人') }}</span>
                <span class="mail-date">{{ fmtDate(m.date) }}</span>
              </div>
              <div v-if="!expanded[i]" class="mail-preview">{{ m.preview || '（无正文）' }}</div>
            </div>
          </div>
          <div v-if="expanded[i]" class="mail-body">
            <div class="mail-body-tools">
              <el-button text size="small" @click="copyText(m.body, '已复制正文')">复制正文</el-button>
            </div>
            <pre class="mail-body-text">{{ m.body || '（无正文）' }}</pre>
          </div>
        </div>
      </div>
      <div v-else class="empty-state">
        {{ loading ? '正在从 Outlook 拉取最新邮件…' : '填好凭据后点「取件」，最新邮件会显示在这里；含验证码的邮件会自动高亮。' }}
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchMail } from '../api/mail'
import { listEmail } from '../api/email'

const source = ref('account')
const folder = ref('all')
const limit = ref(10)
const loading = ref(false)
const messages = ref([])
const expanded = reactive({})
const fetchedAt = ref('')

/* —— 粘贴原始串 —— */
const rawStr = ref('')
const PASTE_EXAMPLE = [
  'TimothyFuller1177@outlook.com',
  'levdsh925786',
  'M.C525_SN1.0.U.MsaArtifacts-ChhjhmrdVl8aj7fSiHaDYME',
  '9e5f94bc-e8a4-4e73-b8be-63364c29d753',
  '请复制前面所有数据到 2fa.run/mail 粘贴',
  'cookie',
  'sk-ant-sid02-abc123'
].join('----')
function loadPasteExample() {
  rawStr.value = PASTE_EXAMPLE
}

/* —— 从已存账号 —— */
const accountId = ref(null)
const accountOptions = ref([])
const accLoading = ref(false)
const selectedAccount = ref(null)

async function searchAccounts(keyword) {
  accLoading.value = true
  try {
    const res = await listEmail({ emailType: 'outlook', keyword: keyword || undefined, page: 1, size: 20 })
    accountOptions.value = res.data.records || []
  } catch {
    accountOptions.value = []
  } finally {
    accLoading.value = false
  }
}
function onAccountChange(id) {
  selectedAccount.value = accountOptions.value.find((a) => a.id === id) || null
}

/* —— 手动填写 —— */
const manual = reactive({ email: '', refreshToken: '', clientId: '' })

/* —— 归一化出当前凭据 —— */
const parsed = computed(() => {
  if (source.value === 'paste') {
    const segs = rawStr.value.trim().split(/-{3,}/)
    return {
      email: (segs[0] || '').trim(),
      refreshToken: (segs[2] || '').trim(),
      clientId: (segs[3] || '').trim()
    }
  }
  if (source.value === 'account') {
    const a = selectedAccount.value
    return {
      email: a?.email || '',
      refreshToken: a?.refreshToken || '',
      clientId: a?.clientId || ''
    }
  }
  return { email: manual.email.trim(), refreshToken: manual.refreshToken.trim(), clientId: manual.clientId.trim() }
})

const canFetch = computed(() => {
  if (source.value === 'account') return !!accountId.value
  const p = parsed.value
  return !!(p.email && p.refreshToken && p.clientId)
})

const latestCode = computed(() => messages.value.find((m) => m.verifyCode)?.verifyCode || '')
const codeFrom = computed(() => {
  const m = messages.value.find((x) => x.verifyCode)
  return m ? (m.fromName || m.from || '未知') : ''
})

function mask(v) {
  if (!v) return '—'
  if (v.length <= 8) return v[0] + '••••'
  return v.slice(0, 4) + '••••' + v.slice(-4)
}

async function handleFetch() {
  loading.value = true
  messages.value = []
  for (const k of Object.keys(expanded)) delete expanded[k]
  try {
    const payload = { folder: folder.value, limit: limit.value }
    if (source.value === 'account') {
      payload.accountId = accountId.value
    } else {
      Object.assign(payload, parsed.value)
    }
    const res = await fetchMail(payload)
    messages.value = res.data || []
    if (!messages.value.length) {
      ElMessage.info('该文件夹暂无邮件')
    } else {
      ElMessage.success(`取到 ${messages.value.length} 封邮件`)
    }
    fetchedAt.value = new Date().toLocaleTimeString()
  } catch {
    // 错误信息已由 request 拦截器统一 ElMessage 提示（含微软返回原文）
  } finally {
    loading.value = false
  }
}

function toggle(i) {
  expanded[i] = !expanded[i]
}

function clearAll() {
  rawStr.value = ''
  accountId.value = null
  selectedAccount.value = null
  manual.email = ''
  manual.refreshToken = ''
  manual.clientId = ''
  messages.value = []
  fetchedAt.value = ''
}

function fmtDate(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString()
}

async function copyText(text, msg) {
  if (!text) { ElMessage.warning('无可复制内容'); return }
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(msg || '已复制')
  } catch {
    ElMessage.error('复制失败，请手动选中复制')
  }
}
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

/* 来源 / 文件夹 chip 风格（与邮箱管理一致） */
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

.paste-block { width: 100%; }
.raw-input :deep(.el-textarea__inner) {
  font-family: var(--mono);
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--ink);
}
.schema-hint {
  font-size: 12px;
  color: var(--faint);
  line-height: 1.6;
  margin-top: 6px;
}
.schema-hint b { color: var(--accent); font-weight: 600; }

.parse-echo {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  margin-top: 8px;
}
.echo-item {
  font-family: var(--mono);
  font-size: 12px;
  color: var(--ink);
  word-break: break-all;
}
.echo-item i {
  font-style: normal;
  color: var(--faint);
  margin-right: 6px;
}

.count-field {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-left: 8px;
  font-size: 12.5px;
  color: var(--muted);
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 8px;
}
.toolbar .spacer { flex: 1 1 auto; }
.fetched-at {
  font-family: var(--mono);
  font-size: 11.5px;
  color: var(--faint);
}

/* 最新验证码高亮 */
.code-hero {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  background: var(--accent-soft);
  border: 1px solid var(--accent);
  border-radius: 12px;
  padding: 14px 18px;
  margin-bottom: 16px;
}
.code-hero-label {
  font-family: var(--mono);
  font-size: 11px;
  letter-spacing: .12em;
  text-transform: uppercase;
  color: var(--accent);
}
.code-hero-val {
  font-size: 30px;
  font-weight: 600;
  letter-spacing: .12em;
  color: var(--accent-ink);
  font-variant-numeric: tabular-nums;
  line-height: 1;
}
.code-hero-from {
  font-size: 12px;
  color: var(--muted);
  margin-left: auto;
}

/* 邮件列表 */
.mail-list {
  border: 1px solid var(--border);
  border-radius: 12px;
  overflow: hidden;
}
.mail-item { border-bottom: 1px solid var(--border); }
.mail-item:last-child { border-bottom: 0; }
.mail-row {
  display: flex;
  gap: 10px;
  padding: 13px 16px;
  cursor: pointer;
  transition: background .12s ease;
}
.mail-row:hover { background: var(--surface-2); }
.chev {
  flex: none;
  margin-top: 3px;
  color: var(--faint);
  transition: transform .15s ease;
}
.chev.open { transform: rotate(90deg); }
.mail-main { min-width: 0; flex: 1; }
.mail-top {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.mail-subject {
  font-weight: 600;
  font-size: 13.5px;
  color: var(--ink);
}
.code-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: .06em;
  color: var(--good);
  background: var(--good-soft);
  border-radius: 7px;
  padding: 2px 8px;
  cursor: pointer;
}
.mail-meta {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 4px;
  font-size: 12px;
  color: var(--muted);
}
.mail-from { font-family: var(--mono); word-break: break-all; }
.mail-date { color: var(--faint); white-space: nowrap; }
.mail-preview {
  margin-top: 6px;
  font-size: 12.5px;
  color: var(--faint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mail-body {
  border-top: 1px dashed var(--border);
  background: var(--surface-2);
  padding: 12px 16px 16px;
}
.mail-body-tools { margin-bottom: 6px; }
.mail-body-text {
  margin: 0;
  font-family: var(--mono);
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--ink);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 420px;
  overflow: auto;
}

.empty-state {
  color: var(--faint);
  font-size: 13px;
  text-align: center;
  padding: 36px 0;
}

@media (max-width: 720px) {
  .code-hero-from { margin-left: 0; width: 100%; }
}
</style>
