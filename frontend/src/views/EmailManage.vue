<template>
  <div class="email-manage">
    <!-- 录入 / 解析 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <span>邮箱录入</span>
        </div>
      </template>

      <el-form label-width="90px">
        <el-form-item label="选择邮箱">
          <el-radio-group v-model="emailType" @change="onTypeChange">
            <el-radio-button value="gmail">@gmail.com</el-radio-button>
            <el-radio-button value="012e">@012e.com</el-radio-button>
            <el-radio-button value="outlook">@outlook.com</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="原始信息">
          <el-input
            v-model="rawData"
            type="textarea"
            :rows="6"
            :placeholder="placeholder"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="parsing" @click="handleParse">解析预览</el-button>
          <el-button type="success" :loading="saving" :disabled="!previewList.length" @click="handleSave">
            保存入库
          </el-button>
          <el-button @click="rawData = ''; previewList = []">清空</el-button>
          <span class="tip">支持多行，一行一条；系统按所选邮箱结构自动拆分（分隔符 “----” 或 “|”）。</span>
        </el-form-item>
      </el-form>

      <div v-if="previewList.length" class="preview">
        <el-divider content-position="left">解析预览（{{ previewList.length }} 条，未入库）</el-divider>
        <el-table :data="previewList" border size="small" style="width: 100%">
          <el-table-column
            v-for="col in columns(emailType)"
            :key="col.prop"
            :prop="col.prop"
            :label="col.label"
            :min-width="col.width || 140"
            show-overflow-tooltip
          />
        </el-table>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <span>邮箱列表</span>
          <div class="filters">
            <el-select v-model="query.emailType" placeholder="全部类型" clearable style="width: 140px" @change="loadList">
              <el-option label="@gmail.com" value="gmail" />
              <el-option label="@012e.com" value="012e" />
              <el-option label="@outlook.com" value="outlook" />
            </el-select>
            <el-input
              v-model="query.keyword"
              placeholder="按邮箱 / 备用邮箱搜索"
              clearable
              style="width: 220px; margin-left: 8px"
              @keyup.enter="loadList"
            />
            <el-button type="primary" style="margin-left: 8px" @click="loadList">查询</el-button>
          </div>
        </div>
      </template>

      <el-table :data="tableData" border v-loading="loading" size="small" style="width: 100%">
        <el-table-column prop="emailType" label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="tagType(row.emailType)" size="small">{{ typeLabel(row.emailType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-for="col in listColumns"
          :key="col.prop"
          :prop="col.prop"
          :label="col.label"
          :min-width="col.width || 140"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span v-if="row[col.prop]">
              {{ row[col.prop] }}
              <el-icon class="copy-icon" @click="copy(row[col.prop])"><CopyDocument /></el-icon>
            </span>
            <span v-else class="empty">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-popconfirm title="确认删除该记录？" @confirm="handleDelete(row.id)">
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
import { parseEmail, saveEmail, listEmail, deleteEmail } from '../api/email'

const emailType = ref('gmail')
const rawData = ref('')
const parsing = ref(false)
const saving = ref(false)
const previewList = ref([])

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const query = reactive({ emailType: '', keyword: '', page: 1, size: 10 })

// 各邮箱类型对应的展示列
const columnDefs = {
  gmail: [
    { prop: 'email', label: '邮箱', width: 200 },
    { prop: 'password', label: '密码', width: 120 },
    { prop: 'recoveryEmail', label: '备用邮箱', width: 200 },
    { prop: 'recoveryKey', label: 'key', width: 180 },
    { prop: 'regYear', label: '年份', width: 80 },
    { prop: 'country', label: '国家', width: 100 },
    { prop: 'authKey', label: '辅助验证码', width: 200 },
    { prop: 'extraUrl', label: '链接', width: 200 }
  ],
  '012e': [
    { prop: 'email', label: '邮箱', width: 200 },
    { prop: 'password', label: '密码', width: 120 },
    { prop: 'extraUrl', label: '取件链接', width: 320 }
  ],
  outlook: [
    { prop: 'email', label: '邮箱', width: 200 },
    { prop: 'password', label: '密码', width: 120 },
    { prop: 'refreshToken', label: 'refreshToken', width: 200 },
    { prop: 'clientId', label: 'clientId', width: 200 },
    { prop: 'note', label: '说明', width: 200 },
    { prop: 'cookie', label: 'cookie', width: 200 }
  ]
}

const placeholders = {
  gmail: '示例：smi23edl21@gmail.com|bG8Rg22M|4ln...@fastmailapp.com|ysf...|2021|India|4O5G S1X2 ...|https://...',
  '012e': '示例：2w4vsfe430@012e.com----0m87---http://mail.012e.com/api/getcode.php?token=...',
  outlook: '示例：stktcf85773@outlook.com----tntmbg78880----M.C541_...$$----9e5f...----请复制...----cookie----sk-ant-...'
}

const placeholder = computed(() => placeholders[emailType.value])
const columns = (type) => columnDefs[type] || []
// 列表列：按筛选类型展示；未筛选时展示通用列
const listColumns = computed(() => {
  if (query.emailType && columnDefs[query.emailType]) {
    return columnDefs[query.emailType]
  }
  return [
    { prop: 'email', label: '邮箱', width: 200 },
    { prop: 'password', label: '密码', width: 140 }
  ]
})

const typeLabel = (t) => ({ gmail: 'gmail', '012e': '012e', outlook: 'outlook' }[t] || t)
const tagType = (t) => ({ gmail: 'danger', '012e': 'success', outlook: 'primary' }[t] || 'info')

function onTypeChange() {
  previewList.value = []
}

async function handleParse() {
  if (!rawData.value.trim()) {
    ElMessage.warning('请输入原始信息')
    return
  }
  parsing.value = true
  try {
    const res = await parseEmail({ emailType: emailType.value, rawData: rawData.value })
    previewList.value = res.data || []
    if (!previewList.value.length) {
      ElMessage.warning('未解析出有效数据')
    } else {
      ElMessage.success(`解析成功，共 ${previewList.value.length} 条`)
    }
  } finally {
    parsing.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    const res = await saveEmail({ emailType: emailType.value, rawData: rawData.value })
    ElMessage.success(`已入库 ${res.data.length} 条`)
    rawData.value = ''
    previewList.value = []
    query.page = 1
    query.emailType = emailType.value
    await loadList()
  } finally {
    saving.value = false
  }
}

async function loadList() {
  loading.value = true
  try {
    const res = await listEmail({
      emailType: query.emailType || undefined,
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

async function handleDelete(id) {
  await deleteEmail(id)
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
.section {
  margin-bottom: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.filters {
  display: flex;
  align-items: center;
}
.tip {
  color: #999;
  font-size: 12px;
  margin-left: 12px;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
.empty {
  color: #ccc;
}
.copy-icon {
  cursor: pointer;
  color: #409eff;
  margin-left: 4px;
  vertical-align: middle;
}
</style>
