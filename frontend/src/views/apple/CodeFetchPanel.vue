<template>
  <div class="tab-pane">
    <!-- 接码 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <div class="head-text">
            <span class="eyebrow"><span class="dot"></span>{{ isHacker ? 'Hacker' : 'Hide' }} → Code</span>
            <span class="head-title">{{ isHacker ? '黑客邮箱倒推接码' : '隐藏邮箱倒推接码' }}</span>
          </div>
        </div>
      </template>

      <p class="panel-lede">
        {{ isHacker
          ? '输入黑客邮箱，自动推到绑定的隐藏邮箱 → 所属 Apple ID 的当前转发邮箱 → 已录入的邮箱账号，取件并提验证码。'
          : '输入隐藏邮箱，自动推到所属 Apple ID 的当前转发邮箱 → 已录入的邮箱账号，取件并提验证码。' }}
        当前转发邮箱接不到码时会自动换该 Apple ID 下的其他转发邮箱重试。
      </p>

      <el-form label-width="110px">
        <el-form-item :label="isHacker ? '黑客邮箱' : '隐藏邮箱'">
          <el-select
            v-model="entryEmail"
            filterable
            remote
            clearable
            allow-create
            default-first-option
            reserve-keyword
            :placeholder="isHacker ? '搜索或直接输入黑客邮箱' : '搜索或直接输入隐藏邮箱'"
            :remote-method="searchEntries"
            :loading="entryLoading"
            style="width: 100%; max-width: 460px"
          >
            <el-option v-for="e in entryOptions" :key="e.id" :label="e.label" :value="e.label">
              <span>{{ e.label }}</span>
              <span class="opt-note">{{ e.note }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="取件条数">
          <el-input-number v-model="limit" :min="1" :max="30" size="small" controls-position="right" />
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button type="primary" :loading="loading" :disabled="!entryEmail" @click="handleFetch">
          {{ loading ? '接码中…' : '接码' }}
        </el-button>
        <el-button :disabled="loading" @click="clearAll">清空</el-button>
        <span v-if="!entryEmail" class="disabled-reason">请先选择或输入{{ isHacker ? '黑客邮箱' : '隐藏邮箱' }}</span>
      </div>
    </el-card>

    <!-- 结果 -->
    <el-card v-if="result" shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <div class="head-text">
            <span class="eyebrow"><span class="dot good"></span>Result</span>
            <span class="head-title">接码结果</span>
          </div>
          <span class="result-count">{{ (result.messages || []).length }} 封</span>
        </div>
      </template>

      <!-- 链路 -->
      <div class="chain">
        <span v-if="isHacker" class="chain-node">{{ result.entryEmail }}<i>黑客邮箱</i></span>
        <span v-if="isHacker" class="chain-arrow">→</span>
        <span class="chain-node">{{ result.hideEmail }}<i>隐藏邮箱</i></span>
        <span class="chain-arrow">→</span>
        <span class="chain-node">{{ result.appleId }}<i>Apple ID</i></span>
        <span class="chain-arrow">→</span>
        <span class="chain-node hl">{{ result.forwardEmail || '—' }}<i>当前转发邮箱</i></span>
        <span class="chain-arrow">→</span>
        <span class="chain-node">{{ result.accountEmail || '—' }}<i>取件账号 {{ result.accountType || '' }}</i></span>
      </div>

      <el-alert
        v-if="result.switched"
        type="warning"
        show-icon
        :closable="false"
        class="switch-alert"
        :title="`原转发邮箱 ${result.previousForwardEmail} 接不到码，已自动切换到 ${result.forwardEmail} 并重试成功`"
      />
      <el-alert
        v-if="!result.success"
        type="error"
        show-icon
        :closable="false"
        class="switch-alert"
        :title="result.message || '没能接到验证码'"
      />

      <!-- 验证码 -->
      <div v-if="result.verifyCode" class="code-hero">
        <div class="code-hero-label">验证码</div>
        <div class="code-hero-val mono">{{ result.verifyCode }}</div>
        <el-button size="small" @click="copyText(result.verifyCode)">复制</el-button>
      </div>

      <!-- 尝试记录 -->
      <div v-if="(result.attempts || []).length > 1" class="attempts">
        <div v-for="(a, i) in result.attempts" :key="i" class="attempt-row">
          <el-tag :type="a.success ? 'success' : 'info'" size="small" effect="plain">
            {{ a.success ? '成功' : '失败' }}
          </el-tag>
          <span class="mono">{{ a.forwardEmail }}</span>
          <span class="attempt-msg">{{ a.message }}</span>
          <span class="attempt-cost">{{ a.durationMs }}ms</span>
        </div>
      </div>

      <!-- 邮件列表 -->
      <div v-if="(result.messages || []).length" class="mail-list">
        <div v-for="(m, i) in result.messages" :key="i" class="mail-item">
          <div class="mail-row" @click="toggle(i)">
            <el-icon class="chev" :class="{ open: expanded[i] }"><ArrowRight /></el-icon>
            <div class="mail-main">
              <div class="mail-top">
                <span class="mail-subject">{{ m.subject || '（无主题）' }}</span>
                <span v-if="m.verifyCode" class="code-badge mono" @click.stop="copyText(m.verifyCode)">
                  {{ m.verifyCode }}
                </span>
              </div>
              <div class="mail-meta">
                <span class="mail-from">{{ m.fromName ? `${m.fromName} <${m.from}>` : m.from }}</span>
                <span class="mail-date">{{ fmtDate(m.date) }}</span>
              </div>
              <div v-if="!expanded[i]" class="mail-preview">{{ m.preview || '（无正文）' }}</div>
            </div>
          </div>
          <div v-if="expanded[i]" class="mail-body">
            <iframe
              v-if="m.bodyHtml"
              class="mail-html"
              sandbox="allow-same-origin allow-popups allow-popups-to-escape-sandbox"
              :srcdoc="htmlDoc(m.bodyHtml)"
              @load="autoHeight"
            ></iframe>
            <pre v-else class="mail-body-text">{{ m.body || '（无正文）' }}</pre>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { listHideEmails, listHackerEmails, fetchAppleCode } from '../../api/apple'

const props = defineProps({
  entryType: { type: String, default: 'hide' }   // hide | hacker
})

const isHacker = computed(() => props.entryType === 'hacker')

const entryEmail = ref('')
const limit = ref(10)
const loading = ref(false)
const result = ref(null)
const expanded = reactive({})

/* —— 入口下拉：已录入的隐藏 / 黑客邮箱 —— */
const entryOptions = ref([])
const entryLoading = ref(false)

async function searchEntries(keyword) {
  entryLoading.value = true
  try {
    const res = isHacker.value
      ? await listHackerEmails({ keyword: keyword || undefined, page: 1, size: 20 })
      : await listHideEmails({ keyword: keyword || undefined, page: 1, size: 20 })
    entryOptions.value = (res.data.records || []).map((r) => ({
      id: r.id,
      label: isHacker.value ? r.hackerEmail : r.hideEmail,
      note: isHacker.value ? r.hideEmail : r.appleId
    }))
  } catch {
    entryOptions.value = []
  } finally {
    entryLoading.value = false
  }
}

async function handleFetch() {
  loading.value = true
  result.value = null
  for (const k of Object.keys(expanded)) delete expanded[k]
  try {
    const payload = { entryType: props.entryType, limit: limit.value }
    if (isHacker.value) payload.hackerEmail = entryEmail.value.trim()
    else payload.hideEmail = entryEmail.value.trim()

    const res = await fetchAppleCode(payload)
    result.value = res.data
    if (res.data.switched) {
      ElMessage.warning(`已自动切换到 ${res.data.forwardEmail}`)
    } else if (res.data.success) {
      ElMessage.success('接码成功')
    } else {
      ElMessage.warning(res.data.message || '没能接到验证码')
    }
  } catch {
    // 错误信息已由 request 拦截器统一提示
  } finally {
    loading.value = false
  }
}

function clearAll() {
  entryEmail.value = ''
  result.value = null
  for (const k of Object.keys(expanded)) delete expanded[k]
}

function toggle(i) {
  expanded[i] = !expanded[i]
}

/** 与「邮件取件」页一致：沙箱 iframe 渲染原文，不给 allow-scripts。 */
function htmlDoc(bodyHtml) {
  return `<!doctype html><html><head><meta charset="utf-8">
<meta http-equiv="Content-Security-Policy" content="script-src 'none'">
<base target="_blank">
<style>
  html,body{margin:0;padding:0;background:#fff;color:#1f2328;}
  body{font:14px/1.6 -apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC",sans-serif;padding:12px;word-break:break-word;}
  img{max-width:100%;height:auto;}
  img[src^="cid:"]{display:none;}
  a{color:#2563eb;}
</style></head><body>${bodyHtml}</body></html>`
}

function autoHeight(e) {
  const el = e.target
  const measure = () => {
    try {
      const h = el.contentDocument?.documentElement?.scrollHeight
      el.style.height = h ? `${Math.min(h + 16, 560)}px` : '380px'
    } catch {
      el.style.height = '380px'
    }
  }
  measure()
  setTimeout(measure, 600)
}

function fmtDate(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString()
}

async function copyText(text) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败，请手动选中复制')
  }
}
</script>

<style scoped>
@import '../../styles/apple-tab.css';

.panel-lede { color: var(--muted); font-size: 12.5px; line-height: 1.6; margin: 0 0 14px; max-width: 76ch; }

/* 链路示意 */
.chain {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 12px 14px;
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: 10px;
  margin-bottom: 14px;
}
.chain-node {
  display: flex;
  flex-direction: column;
  font-family: var(--mono);
  font-size: 12px;
  color: var(--ink);
  word-break: break-all;
}
.chain-node i { font-style: normal; font-size: 11px; color: var(--faint); margin-top: 2px; }
.chain-node.hl { color: var(--accent); font-weight: 600; }
.chain-arrow { color: var(--faint); }

.switch-alert { margin-bottom: 14px; }

.code-hero {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  background: var(--accent-soft);
  border: 1px solid var(--accent);
  border-radius: 12px;
  padding: 14px 18px;
  margin-bottom: 14px;
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

.attempts { margin-bottom: 14px; }
.attempt-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--muted);
  padding: 4px 0;
}
.attempt-msg { color: var(--faint); }
.attempt-cost { margin-left: auto; font-family: var(--mono); color: var(--faint); }

/* 邮件列表（与「邮件取件」页同构的精简版） */
.mail-list { border: 1px solid var(--border); border-radius: 12px; overflow: hidden; }
.mail-item { border-bottom: 1px solid var(--border); }
.mail-item:last-child { border-bottom: 0; }
.mail-row { display: flex; gap: 10px; padding: 12px 14px; cursor: pointer; }
.mail-row:hover { background: var(--surface-2); }
.chev { flex: none; margin-top: 3px; color: var(--faint); transition: transform .15s ease; }
.chev.open { transform: rotate(90deg); }
.mail-main { min-width: 0; flex: 1; }
.mail-top { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.mail-subject { font-weight: 600; font-size: 13.5px; color: var(--ink); }
.code-badge {
  font-size: 13px;
  font-weight: 600;
  letter-spacing: .06em;
  color: var(--good);
  background: var(--good-soft);
  border-radius: 7px;
  padding: 2px 8px;
  cursor: pointer;
}
.mail-meta { display: flex; gap: 12px; flex-wrap: wrap; margin-top: 4px; font-size: 12px; color: var(--muted); }
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
.mail-body { border-top: 1px dashed var(--border); background: var(--surface-2); padding: 12px 14px; }
.mail-html { display: block; width: 100%; height: 380px; border: 1px solid var(--border); border-radius: 8px; background: #fff; }
.mail-body-text {
  margin: 0;
  font-family: var(--mono);
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--ink);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 380px;
  overflow: auto;
}
</style>
