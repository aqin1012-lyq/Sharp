<template>
  <div class="tab-pane">
    <!-- 录入 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <div class="head-text">
            <span class="eyebrow"><span class="dot"></span>Hacker</span>
            <span class="head-title">绑定黑客邮箱</span>
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

        <template v-if="entryMode === 'single'">
          <el-form-item label="隐藏邮箱">
            <el-select
              v-model="form.hideEmailId"
              filterable
              remote
              clearable
              reserve-keyword
              placeholder="搜索已录入的隐藏邮箱"
              :remote-method="searchHideEmails"
              :loading="hideLoading"
              style="width: 100%; max-width: 460px"
            >
              <el-option
                v-for="h in hideOptions"
                :key="h.id"
                :label="h.hideEmail"
                :value="h.id"
                :disabled="!!h.hackerEmail"
              >
                <span>{{ h.hideEmail }}</span>
                <span class="opt-note">{{ h.hackerEmail ? '已绑定' : h.appleId }}</span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="黑客邮箱">
            <el-input v-model="form.hackerEmail" placeholder="xxx@wearehackerone.com" style="max-width: 460px" />
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="分隔符">
            <el-radio-group v-model="sepKey" class="type-chips">
              <el-radio-button value="dash">----</el-radio-button>
              <el-radio-button value="comma">逗号</el-radio-button>
              <el-radio-button value="tab">制表符</el-radio-button>
              <el-radio-button value="space">空格</el-radio-button>
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
                :placeholder="batchPlaceholder"
              />
              <div class="batch-hint">
                每行一对「隐藏邮箱 + 黑客邮箱」，顺序随意 —— 含 <b>wearehackerone.com</b> 的识别为黑客邮箱，
                另一个当作隐藏邮箱。隐藏邮箱必须**已经录入过**；任一端已绑定的会自动跳过。
                <el-button text size="small" @click="loadBatchExample">载入示例</el-button>
              </div>
              <div v-if="batchRows.length" class="batch-preview">
                <div class="batch-stat">
                  共 {{ batchRows.length }} 行 ·
                  <span class="ok">可绑定 {{ batchValid.length }}</span>
                  <span v-if="batchBadCount" class="bad"> · 有问题 {{ batchBadCount }}</span>
                </div>
                <el-table :data="batchRows" border size="small" max-height="240" style="width: 100%">
                  <el-table-column type="index" label="#" width="46" align="center" />
                  <el-table-column prop="hideEmail" label="隐藏邮箱" min-width="220" />
                  <el-table-column prop="hackerEmail" label="黑客邮箱" min-width="220" />
                  <el-table-column label="状态" width="116">
                    <template #default="{ row }">
                      <el-tag v-if="row.issue" type="warning" size="small" effect="plain">{{ row.issue }}</el-tag>
                      <el-tag v-else type="success" size="small" effect="plain">可绑定</el-tag>
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
          {{ saving ? '绑定中…' : '绑定' }}
        </el-button>
        <el-button v-else type="primary" :loading="saving" :disabled="!canSaveBatch" @click="handleSaveBatch">
          {{ saving ? '绑定中…' : `批量绑定 ${batchValid.length} 条` }}
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
            <span class="head-title">已绑黑客邮箱</span>
          </div>
          <span class="result-count">{{ total }} 条</span>
        </div>
      </template>

      <div class="filters">
        <el-input
          v-model="query.keyword"
          placeholder="搜索黑客邮箱"
          clearable
          style="max-width: 260px"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-button :loading="loading" @click="reload">查询</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border size="small" style="width: 100%">
        <el-table-column prop="id" label="ID" width="66" />
        <el-table-column prop="hideEmail" label="隐藏邮箱" min-width="230" />
        <el-table-column prop="hackerEmail" label="黑客邮箱" min-width="230" />
        <el-table-column prop="appleId" label="所属 Apple ID" min-width="180" />
        <el-table-column prop="createdBy" label="录入人" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="122" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认解绑该记录？" :width="190" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button text type="danger" size="small">解绑</el-button>
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
    <el-dialog v-model="editVisible" title="编辑黑客邮箱" width="500px">
      <el-form label-width="100px">
        <el-form-item label="隐藏邮箱">
          <span class="mono-text">{{ editForm.hideEmail || '—' }}</span>
        </el-form-item>
        <el-form-item label="黑客邮箱">
          <el-input v-model="editForm.hackerEmail" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" :disabled="!EMAIL_RE.test(editForm.hackerEmail.trim())" @click="handleUpdate">
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
  listHideEmails,
  listHackerEmails,
  saveHackerEmail,
  saveHackerEmailBatch,
  updateHackerEmail,
  deleteHackerEmail
} from '../../api/apple'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const entryMode = ref('single')
const saving = ref(false)
const form = reactive({ hideEmailId: null, hackerEmail: '' })

/* —— 隐藏邮箱下拉（已绑定的置灰） —— */
const hideOptions = ref([])
const hideLoading = ref(false)

async function searchHideEmails(keyword) {
  hideLoading.value = true
  try {
    const res = await listHideEmails({ keyword: keyword || undefined, page: 1, size: 20 })
    hideOptions.value = res.data.records || []
  } catch {
    hideOptions.value = []
  } finally {
    hideLoading.value = false
  }
}

/* —— 批量解析 —— */
const sepKey = ref('dash')
const customSep = ref('|')
const batchRaw = ref('')

const SEPARATORS = { dash: /-{3,}/, comma: /[,，]/, tab: /\t/, space: /\s+/ }
const sepRe = computed(() => {
  if (sepKey.value !== 'custom') return SEPARATORS[sepKey.value]
  const s = customSep.value.trim()
  if (!s) return SEPARATORS.dash
  return new RegExp(s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))
})

const batchPlaceholder = computed(() => {
  const sep = { dash: '----', comma: ',', tab: '\t', space: ' ' }[sepKey.value] || customSep.value || '|'
  return [
    `aaa_bbb@icloud.com${sep}aaabbb@wearehackerone.com`,
    `ccc_ddd@icloud.com${sep}cccddd@wearehackerone.com`
  ].join('\n')
})

function loadBatchExample() {
  batchRaw.value = batchPlaceholder.value
}

const batchRows = computed(() => {
  const seenHide = new Set()
  const seenHacker = new Set()
  const out = []
  for (const line of batchRaw.value.split('\n')) {
    const parts = line.trim().split(sepRe.value).map((s) => s.trim()).filter(Boolean)
    if (!parts.length) continue
    let hackerEmail = parts.find((p) => /wearehackerone\.com$/i.test(p)) || ''
    let hideEmail = parts.find((p) => p !== hackerEmail) || ''
    if (!hackerEmail) {
      // 认不出域名时按「隐藏邮箱 在前、黑客邮箱 在后」的位置取
      hideEmail = parts[0] || ''
      hackerEmail = parts[1] || ''
    }
    let issue = ''
    if (!EMAIL_RE.test(hideEmail) || !EMAIL_RE.test(hackerEmail)) {
      issue = '缺字段或格式错'
    } else if (seenHide.has(hideEmail.toLowerCase()) || seenHacker.has(hackerEmail.toLowerCase())) {
      issue = '本批重复'
    } else {
      seenHide.add(hideEmail.toLowerCase())
      seenHacker.add(hackerEmail.toLowerCase())
    }
    out.push({ hideEmail, hackerEmail, issue })
  }
  return out
})
const batchValid = computed(() => batchRows.value.filter((r) => !r.issue))
const batchBadCount = computed(() => batchRows.value.length - batchValid.value.length)

/* —— 提交 —— */
const canSave = computed(() => !!form.hideEmailId && EMAIL_RE.test(form.hackerEmail.trim()))
const canSaveBatch = computed(() => batchValid.value.length > 0)

const disabledReason = computed(() => {
  if (entryMode.value === 'batch' ? canSaveBatch.value : canSave.value) return ''
  if (entryMode.value === 'batch') {
    return batchRows.value.length ? '没有可绑定的行' : '请粘贴要批量绑定的内容'
  }
  if (!form.hideEmailId) return '请先选择隐藏邮箱'
  return '请填写完整的黑客邮箱地址'
})

async function handleSave() {
  saving.value = true
  try {
    await saveHackerEmail({ hideEmailId: form.hideEmailId, hackerEmail: form.hackerEmail.trim() })
    ElMessage.success('绑定成功')
    form.hideEmailId = null
    form.hackerEmail = ''
    await reload()
  } catch {
    // 一对一冲突时服务端会说明是哪一端被占用
  } finally {
    saving.value = false
  }
}

async function handleSaveBatch() {
  saving.value = true
  try {
    const res = await saveHackerEmailBatch({
      items: batchValid.value.map((r) => ({ hideEmail: r.hideEmail, hackerEmail: r.hackerEmail }))
    })
    const { saved = 0, skipped = 0 } = res.data || {}
    ElMessage.success(skipped ? `成功 ${saved} 条，跳过 ${skipped} 条（未录入或已绑定）` : `成功绑定 ${saved} 条`)
    batchRaw.value = ''
    await reload()
  } catch {
    // 同上
  } finally {
    saving.value = false
  }
}

function resetForm() {
  form.hideEmailId = null
  form.hackerEmail = ''
  batchRaw.value = ''
}

/* —— 列表 —— */
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ keyword: '', page: 1, size: 10 })

async function load() {
  loading.value = true
  try {
    const res = await listHackerEmails({
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
    await deleteHackerEmail(id)
    ElMessage.success('已解绑')
    await load()
  } catch {
    // 同上
  }
}

/* —— 编辑 —— */
const editVisible = ref(false)
const editSaving = ref(false)
const editForm = reactive({ id: null, hideEmail: '', hackerEmail: '' })

function openEdit(row) {
  editForm.id = row.id
  editForm.hideEmail = row.hideEmail || ''
  editForm.hackerEmail = row.hackerEmail || ''
  editVisible.value = true
}

async function handleUpdate() {
  editSaving.value = true
  try {
    await updateHackerEmail(editForm.id, { hackerEmail: editForm.hackerEmail.trim() })
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
  searchHideEmails('')
  load()
})
</script>

<style scoped>
@import '../../styles/apple-tab.css';
</style>
