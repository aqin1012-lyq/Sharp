<template>
  <div class="email-manage">
    <!-- 页头 -->
    <div class="page-head">
      <span class="eyebrow">Email Accounts</span>
      <h1 class="display">邮箱管理</h1>
      <p class="lede">解析 Gmail / 012e / Outlook 原始串，自动拆分字段入库，支持批量粘贴与关键字检索。</p>
    </div>

    <!-- 统计磁贴 -->
    <div class="stats">
      <div class="stat"><span class="n display">{{ stats.total }}</span><span class="k">总记录</span></div>
      <div class="stat"><span class="n display">{{ stats.gmail }}</span><span class="k">Gmail</span></div>
      <div class="stat"><span class="n display">{{ stats.e012 }}</span><span class="k">012e</span></div>
      <div class="stat"><span class="n display">{{ stats.outlook }}</span><span class="k">Outlook</span></div>
    </div>

    <!-- 录入 / 解析 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <div class="head-text">
            <span class="eyebrow"><span class="dot"></span>Import &amp; Parse</span>
            <span class="head-title">邮箱录入</span>
          </div>
        </div>
      </template>

      <el-form label-width="90px" class="entry-form">
        <el-form-item label="选择邮箱">
          <el-radio-group v-model="emailType" @change="onTypeChange" class="type-chips">
            <el-radio-button value="gmail">@gmail.com</el-radio-button>
            <el-radio-button value="012e">@012e.com</el-radio-button>
            <el-radio-button value="outlook">@outlook.com</el-radio-button>
            <el-radio-button value="auto">自动识别</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="字段顺序">
          <div class="schema-block">
            <div class="schema-row">
              <div ref="schemaEl" class="schema">
                <template v-for="(f, i) in currentFields" :key="f.key">
                  <span
                    class="chip"
                    :class="{
                      dragging: dragIndex === i,
                      over: overIndex === i && dragIndex !== i && dragIndex !== -1,
                      ghost: f.key === 'ignore'
                    }"
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
                  <span v-if="i < currentFields.length - 1" class="sep">{{ currentSep }}</span>
                </template>
              </div>
              <el-button v-if="isCustomOrder" text size="small" class="reset-btn" @click="resetOrder">
                恢复默认
              </el-button>
            </div>
            <div class="schema-hint">
              拖拽色块调整顺序（也可聚焦后按 ← / →）；解析时第 N 段写入这里的第 N 个字段，段数不够的字段留空。
              <span v-if="emailType === 'gmail'">gmail 按原始串里有没有 “|” 自动切换两套布局。</span>
              <span v-if="emailType === 'auto'">自动识别：含数据表全部字段，可自定义顺序；逐行按邮箱后缀归类（不限三种，其余记为该域名），
              每行含 “|” 按 “|” 否则按 “----” 切段。</span>
              <span v-if="isCustomOrder" class="custom-flag">已自定义</span>
            </div>
          </div>
        </el-form-item>

      </el-form>

      <!-- 操作栏 -->
      <div class="toolbar">
        <el-button type="primary" :loading="parsing" :disabled="!keptLines.length" @click="handleParse">
          解析预览
        </el-button>
        <el-button
          type="success"
          :loading="saving"
          :disabled="!previewList.length"
          @click="handleSave"
        >
          保存入库
        </el-button>
        <el-button :disabled="!previewList.length" @click="copyPreview">复制预览</el-button>
        <el-button @click="loadExample">载入示例</el-button>
        <el-button @click="clearAll">清空</el-button>
        <span class="spacer"></span>
        <el-switch
          v-model="keepBad"
          size="small"
          active-text="保留异常行"
          title="开启后异常行也会一起解析 / 入库；关闭则从结果中剔除，仅在下方列出。"
        />
      </div>

      <!-- 输入 / 输出 双栏 -->
      <div class="panes">
        <div class="pane">
          <div class="pane-head">
            <span class="label"><span class="pane-dot"></span>原始信息</span>
            <span class="count">{{ lineStats.total }} 行</span>
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
            <template v-if="previewList.length">
              <div class="preview-tools">
                <el-switch v-model="previewMasked" size="small" active-text="隐藏敏感字段" />
                <span class="spacer"></span>
                <el-button size="small" @click="copyAllLines">复制全部（---- 格式）</el-button>
                <el-button size="small" @click="copyTsv">复制为表格</el-button>
              </div>
              <el-table :data="previewList" border size="small" style="width: 100%">
                <el-table-column type="index" label="#" width="46" align="center" />
                <el-table-column v-if="emailType === 'auto'" label="类型" width="90">
                  <template #default="{ row }">
                    <el-tag :type="tagType(row.emailType)" size="small">{{ typeLabel(row.emailType) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column
                  v-for="col in previewColumns"
                  :key="col.key"
                  :label="col.label"
                  :min-width="col.width || 140"
                >
                  <template #header>
                    <span class="col-head">
                      {{ col.label }}
                      <el-icon class="col-copy" title="复制整列" @click="copyColumn(col.key, col.label)">
                        <CopyDocument />
                      </el-icon>
                    </span>
                  </template>
                  <template #default="{ row, $index }">
                    <!-- 2FA 密钥列：密钥 + 实时 TOTP 验证码 -->
                    <div v-if="col.key === 'authKey'" class="fa-cell">
                      <span class="cell-val">
                        <span v-if="row.authKey" class="cell-txt">{{ maskIf('authKey', row.authKey) }}</span>
                        <span v-else class="cell-empty">—</span>
                        <el-icon v-if="row.authKey" class="copy-icon" title="复制密钥" @click="copyText(row.authKey, '已复制密钥')">
                          <CopyDocument />
                        </el-icon>
                      </span>
                      <div v-if="isTotp(row.authKey)" class="otp">
                        <span class="otp-ring" :style="ringStyle"><i>{{ totpLeft }}</i></span>
                        <span class="otp-code" :class="{ invalid: otpCodes[$index] === 'invalid' }">
                          {{ fmtOtp(otpCodes[$index]) }}
                        </span>
                        <el-icon
                          v-if="otpCodes[$index] && otpCodes[$index] !== 'invalid'"
                          class="copy-icon always"
                          title="复制验证码"
                          @click="copyText(otpCodes[$index], '已复制验证码')"
                        >
                          <CopyDocument />
                        </el-icon>
                      </div>
                    </div>
                    <!-- 链接列：可点击 + 复制 -->
                    <span v-else-if="col.key === 'extraUrl'" class="cell-val">
                      <a v-if="row.extraUrl" :href="row.extraUrl" target="_blank" rel="noopener noreferrer" class="cell-link">
                        {{ row.extraUrl }}
                      </a>
                      <span v-else class="cell-empty">—</span>
                      <el-icon v-if="row.extraUrl" class="copy-icon" title="复制链接" @click="copyText(row.extraUrl, '已复制链接')">
                        <CopyDocument />
                      </el-icon>
                    </span>
                    <!-- 通用列 -->
                    <span v-else class="cell-val">
                      <span v-if="row[col.key]" class="cell-txt">{{ maskIf(col.key, row[col.key]) }}</span>
                      <span v-else class="cell-empty">—</span>
                      <el-icon v-if="row[col.key]" class="copy-icon" title="复制" @click="copyText(row[col.key], '已复制')">
                        <CopyDocument />
                      </el-icon>
                    </span>
                  </template>
                </el-table-column>
                <el-table-column label="行" width="58" align="center">
                  <template #default="{ row }">
                    <el-icon class="copy-icon always" title="复制整行（---- 格式）" @click="copyText(previewToLine(row), '已复制整行')">
                      <CopyDocument />
                    </el-icon>
                  </template>
                </el-table-column>
              </el-table>
            </template>
            <div v-else class="pane-empty">
              点「解析预览」后，按当前字段顺序拆出的结果会显示在这里；含 2FA 密钥的行会实时生成验证码。
            </div>
          </div>
          <div class="hint">预览结果未入库；改动字段顺序或原始信息后需重新解析。2FA 验证码按 TOTP 标准（30 秒 · 6 位）实时计算。</div>
        </div>
      </div>

      <!-- 逐行校验统计 -->
      <div class="check-stats">
        <div class="stat"><span class="n display">{{ lineStats.total }}</span><span class="k">输入有效行</span></div>
        <div class="stat ok"><span class="n display">{{ lineStats.ok }}</span><span class="k">可解析</span></div>
        <div class="stat err" :class="{ zero: lineStats.bad === 0 }">
          <span class="n display">{{ lineStats.bad }}</span><span class="k">异常 / 警告</span>
        </div>
      </div>

      <!-- 异常明细 -->
      <details class="issues" :open="issuesOpen" @toggle="issuesOpen = $event.target.open">
        <summary>
          <span class="chev">▸</span>
          异常明细
          <span class="badge" :class="badge.cls">{{ badge.text }}</span>
        </summary>
        <div class="issue-list">
          <div v-for="it in shownIssues" :key="it.lineNo" class="issue" :class="it.kind">
            <span class="ln">第 {{ it.lineNo }} 行</span>
            <span class="body">
              <span class="reason">{{ it.reasons.join('；') }}</span>
              <div class="issue-preview">{{ it.short }}</div>
            </span>
          </div>
          <div v-if="issues.length > MAX_ISSUES_SHOWN" class="issue-note">
            另有 {{ issues.length - MAX_ISSUES_SHOWN }} 条未显示（结果已全部处理）。
          </div>
          <div v-if="!issues.length" class="issue-note">
            {{ lineStats.total ? '没有发现异常，所有行都能按当前字段顺序解析。' : '尚无输入数据。' }}
          </div>
        </div>
      </details>
    </el-card>

    <!-- 邮箱提取 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <div class="head-text">
            <span class="eyebrow"><span class="dot"></span>Extract</span>
            <span class="head-title">邮箱提取</span>
          </div>
        </div>
      </template>

      <p class="ex-desc">
        从每行数据中提取邮箱（第一段，即第一个 “----” 之前的内容），实时批量提取并校验格式；无 “----” 的行取整行。
        邮箱格式不合格的行不混入结果，而在下方「异常明细」按行号列出——<b>有效 + 异常 = 输入行</b>，一条不丢。
      </p>

      <!-- 操作栏 -->
      <div class="toolbar">
        <el-button type="primary" :disabled="!exOutput" @click="exCopy">复制结果</el-button>
        <el-button :disabled="!exOutput" @click="exDownload">下载 .txt</el-button>
        <el-button @click="exLoadExample">载入示例</el-button>
        <el-button @click="exInput = ''">清空</el-button>
        <span class="spacer"></span>
        <span class="ex-field">
          分隔
          <el-select v-model="exJoin" size="small" style="width: 156px">
            <el-option label="换行（每行一个）" value="newline" />
            <el-option label="逗号 ," value="comma" />
            <el-option label="逗号+空格 , " value="commaspace" />
            <el-option label="分号 ;" value="semicolon" />
            <el-option label="空格" value="space" />
          </el-select>
        </span>
        <el-switch
          v-model="exDedup"
          size="small"
          active-text="去重（忽略大小写）"
          title="开启后，重复的邮箱只保留第一次出现（忽略大小写）。"
        />
      </div>

      <!-- 输入 / 输出 双栏 -->
      <div class="panes">
        <div class="pane">
          <div class="pane-head">
            <span class="label"><span class="pane-dot"></span>输入</span>
            <span class="count">{{ exLineTotal }} 行</span>
          </div>
          <el-input
            v-model="exInput"
            type="textarea"
            :rows="12"
            resize="vertical"
            class="raw-input"
            placeholder="在此粘贴数据，每行一条：&#10;邮箱----密码----……"
          />
          <div class="hint">每行取第一个 “----” 之前为邮箱；空行自动忽略，首尾空白自动清除。</div>
        </div>

        <div class="pane out">
          <div class="pane-head">
            <span class="label"><span class="pane-dot"></span>提取的邮箱</span>
            <span class="count">{{ exResultCount }} 个</span>
          </div>
          <el-input
            :model-value="exOutput"
            type="textarea"
            :rows="12"
            resize="vertical"
            readonly
            class="raw-input"
            placeholder="提取结果会自动出现在这里"
          />
          <div class="hint">{{ exOutHint }}</div>
        </div>
      </div>

      <!-- 统计 -->
      <div class="extract-stats">
        <div class="stat"><span class="n display">{{ exLineTotal }}</span><span class="k">输入有效行</span></div>
        <div class="stat ok"><span class="n display">{{ exValidCount }}</span><span class="k">提取邮箱</span></div>
        <div class="stat dup" :class="{ zero: exRemoved === 0 }">
          <span class="n display">{{ exRemoved }}</span><span class="k">去重移除</span>
        </div>
        <div class="stat err" :class="{ zero: exIssues.length === 0 }">
          <span class="n display">{{ exIssues.length }}</span><span class="k">异常行</span>
        </div>
      </div>

      <!-- 异常明细 -->
      <details class="issues" :open="exIssuesOpen" @toggle="exIssuesOpen = $event.target.open">
        <summary>
          <span class="chev">▸</span>
          异常明细（未提取到有效邮箱的行）
          <span class="badge" :class="exIssues.length ? 'err' : 'none'">
            {{ exIssues.length ? `${exIssues.length} 行未提取` : (exLineTotal ? '全部正常' : '等待输入') }}
          </span>
        </summary>
        <div class="issue-list">
          <div v-for="it in exShownIssues" :key="it.lineNo" class="issue err">
            <span class="ln">第 {{ it.lineNo }} 行</span>
            <span class="body">
              <span class="reason">{{ it.reason }}</span>
              <div class="issue-preview">{{ it.short }}</div>
            </span>
          </div>
          <div v-if="exIssues.length > MAX_ISSUES_SHOWN" class="issue-note">
            另有 {{ exIssues.length - MAX_ISSUES_SHOWN }} 条未显示（已全部计入统计）。
          </div>
          <div v-if="!exIssues.length" class="issue-note">
            {{ exLineTotal ? '每一行都提取到了有效邮箱。' : '尚无输入数据。' }}
          </div>
        </div>
      </details>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="section">
      <template #header>
        <div class="card-header">
          <div class="head-text">
            <span class="eyebrow"><span class="dot good"></span>Accounts</span>
            <span class="head-title">邮箱列表</span>
          </div>
          <div class="filters">
            <el-select v-model="query.emailType" placeholder="全部类型" clearable style="width: 140px" @change="loadList">
              <el-option label="@gmail.com" value="gmail" />
              <el-option label="@012e.com" value="012e" />
              <el-option label="@outlook.com" value="outlook" />
              <el-option label="其他" value="other" />
            </el-select>
            <el-input
              v-model="query.keyword"
              placeholder="按邮箱 / 备用邮箱 / UUID 搜索"
              clearable
              style="width: 220px; margin-left: 8px"
              @keyup.enter="loadList"
            />
            <el-button type="primary" style="margin-left: 8px" @click="loadList">查询</el-button>
          </div>
        </div>
      </template>

      <el-table :data="tableData" border v-loading="loading" size="small" style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" fixed />
        <el-table-column prop="emailType" label="类型" width="90" fixed>
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
            <span v-if="row[col.prop]" class="data-cell">
              {{ row[col.prop] }}
              <el-icon class="copy-icon" @click="copy(row[col.prop])"><CopyDocument /></el-icon>
            </span>
            <span v-else class="empty">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column prop="updateTime" label="更新时间" width="170" />
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
import { ref, reactive, computed, nextTick, watch, onMounted, onBeforeUnmount } from 'vue'
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
const stats = reactive({ total: 0, gmail: 0, e012: 0, outlook: 0 })

// 解析模板：默认字段顺序 + 分隔符 + 列宽。色块就是解析依据，第 N 段写入第 N 个字段。
// key 与后端 EmailAccount 字段同名；"ignore" 是占位段（原始串里固定出现的 "cookie" 标记），不入库也不成列。
// gmail 有两套布局（"|" 8 段 / "----" 5 段），按输入框里的原始串自动切换，各自记各自的顺序。
const UUID_TOKEN = [
  { key: 'uuid', label: 'UUID', width: 200 },
  { key: 'token', label: 'token', width: 220 }
]

const fieldDefs = {
  gmail: {
    sep: '|',
    sepRe: /(\|)/,
    fields: [
      { key: 'email', label: '邮箱', width: 200 },
      { key: 'password', label: '密码', width: 120 },
      { key: 'recoveryEmail', label: '备用邮箱', width: 200 },
      { key: 'recoveryKey', label: 'key', width: 180 },
      { key: 'regYear', label: '年份', width: 80 },
      { key: 'country', label: '国家', width: 100 },
      { key: 'authKey', label: '辅助验证码', width: 200 },
      { key: 'extraUrl', label: '链接', width: 200 },
      { key: 'note', label: '说明', width: 200 },
      ...UUID_TOKEN
    ]
  },
  'gmail-dash': {
    sep: '----',
    sepRe: /(----)/,
    fields: [
      { key: 'email', label: '邮箱', width: 200 },
      { key: 'password', label: '密码', width: 120 },
      { key: 'recoveryEmail', label: '备用邮箱', width: 200 },
      { key: 'authKey', label: '2FA备用码', width: 200 },
      { key: 'extraUrl', label: '2FA链接', width: 240 },
      { key: 'note', label: '说明', width: 200 },
      ...UUID_TOKEN
    ]
  },
  '012e': {
    sep: '----',
    sepRe: /(-{3,})/,
    fields: [
      { key: 'email', label: '邮箱', width: 200 },
      { key: 'password', label: '密码', width: 120 },
      { key: 'extraUrl', label: '取件链接', width: 320 },
      { key: 'note', label: '说明', width: 200 },
      ...UUID_TOKEN
    ]
  },
  outlook: {
    sep: '----',
    sepRe: /(----)/,
    fields: [
      { key: 'email', label: '邮箱', width: 200 },
      { key: 'password', label: '密码', width: 120 },
      { key: 'refreshToken', label: 'refreshToken', width: 200 },
      { key: 'clientId', label: 'clientId', width: 200 },
      { key: 'note', label: '说明', width: 200 },
      { key: 'ignore', label: '固定标记 cookie' },
      { key: 'cookie', label: 'cookie', width: 220 },
      { key: 'authKey', label: '2FA密钥', width: 200 },
      ...UUID_TOKEN
    ]
  },
  // 自动识别：含数据表全部字段，用户自定义顺序；按每行分隔符切段，类型按邮箱后缀判定（不限三种）。
  auto: {
    sep: '----',
    sepRe: /(-{3,})/,
    fields: [
      { key: 'email', label: '邮箱', width: 200 },
      { key: 'password', label: '密码', width: 120 },
      { key: 'recoveryEmail', label: '备用邮箱', width: 200 },
      { key: 'recoveryKey', label: 'key', width: 180 },
      { key: 'regYear', label: '年份', width: 80 },
      { key: 'country', label: '国家', width: 100 },
      { key: 'authKey', label: '辅助验证码 / 2FA', width: 220 },
      { key: 'extraUrl', label: '链接', width: 220 },
      { key: 'refreshToken', label: 'refreshToken', width: 200 },
      { key: 'clientId', label: 'clientId', width: 180 },
      { key: 'note', label: '说明', width: 180 },
      { key: 'cookie', label: 'cookie', width: 200 },
      ...UUID_TOKEN
    ]
  }
}

// 「载入示例」按当前模板给一条可直接解析的样例
const examples = {
  gmail: 'smi23edl21@gmail.com|bG8Rg22M|4ln532j332488c3@fastmailapp.com|ysfsdcdf3lqgmmq5w6y5|2021|India|4O5G S1X2 2NCX Z6CB|https://2af.example.com/#NG8z',
  'gmail-dash': 'gfaxvrunnllk5468@gmail.com----m61obw4oAwqfC----FarahSterrett55@outlook.com----6rza nf53 za5f bei2----https://2fas.example.com/#NnJ6',
  '012e': '2w4vsfe430@012e.com----0m87----http://mail.012e.com/api/getcode.php?token=Mnc0djNz',
  outlook: [
    'TimothyFuller1177@outlook.com',
    'levdsh925786',
    'M.C525_SN1.0.U.MsaArtifacts-ChhjhmrdVl8aj7fSiHaDYME',
    '9e5f94bc-e8a4-4e73-b8be-63364c29d753',
    '请复制前面所有数据到 2fa.run/mail 粘贴',
    'cookie',
    'sk-ant-sid02-abc123',
    '550e8400-e29b-41d4-a716-446655440000',
    'tk_live_x1'
  ].join('----'),
  // 混合示例：三种类型各一行
  auto: [
    'smi23edl21@gmail.com|bG8Rg22M|4ln532j@fastmailapp.com|ysfsdcdf3lq|2021|India|4O5G S1X2 2NCX Z6CB|https://2af.example.com/#NG8z',
    '2w4vsfe430@012e.com----0m87----http://mail.012e.com/api/getcode.php?token=Mnc0djNz',
    'stktcf85773@outlook.com----tntmbg78880----M.C541_x$$----9e5f94bc----请复制前面数据----cookie----sk-ant-sid02-abc123'
  ].join('\n')
}

const placeholders = {
  gmail: '示例：smi23edl21@gmail.com|bG8Rg22M|4ln...@fastmailapp.com|ysf...|2021|India|4O5G S1X2 ...|https://...',
  '012e': '示例：2w4vsfe430@012e.com----0m87---http://mail.012e.com/api/getcode.php?token=...',
  outlook: '示例：stktcf85773@outlook.com----tntmbg78880----M.C541_...$$----9e5f...----请复制...----cookie----sk-ant-...',
  auto: '混合粘贴，一行一条，类型自动识别：\n邮箱@gmail.com|密码|备用邮箱|key|年份|国家|辅助验证码|链接\n邮箱@012e.com----密码----取件链接\n邮箱@outlook.com----密码----refreshToken----clientId----说明----cookie----值'
}

const placeholder = computed(() => placeholders[emailType.value])

/* —— 字段顺序：可拖拽调整，按解析模板分别记在 localStorage —— */
const ORDER_KEY = 'sharp:field-order:v1'
const defaultKeys = (tpl) => fieldDefs[tpl].fields.map((f) => f.key)

const samePermutation = (a, b) =>
  Array.isArray(a) && a.length === b.length && [...a].sort().join() === [...b].sort().join()

function loadOrder() {
  const base = {}
  for (const tpl of Object.keys(fieldDefs)) {
    base[tpl] = defaultKeys(tpl)
  }
  try {
    const saved = JSON.parse(localStorage.getItem(ORDER_KEY) || '{}')
    for (const tpl of Object.keys(base)) {
      // 只接受“同一批字段换了顺序”的记录，模板字段增删过就退回默认
      if (samePermutation(saved[tpl], base[tpl])) {
        base[tpl] = saved[tpl]
      }
    }
  } catch {
    // 存储损坏 / 不可用时用默认顺序
  }
  return base
}

const fieldOrder = reactive(loadOrder())
const schemaEl = ref(null)
const dragIndex = ref(-1)
const overIndex = ref(-1)

// 模板选择：auto → 全字段模板；gmail 按有无 "|" 切两套布局；其余即类型名
const templateKey = computed(() => {
  if (emailType.value === 'auto') {
    return 'auto'
  }
  if (emailType.value !== 'gmail') {
    return emailType.value
  }
  return rawData.value.includes('|') ? 'gmail' : 'gmail-dash'
})

const currentSep = computed(() => fieldDefs[templateKey.value].sep)
const currentFields = computed(() => {
  const byKey = new Map(fieldDefs[templateKey.value].fields.map((f) => [f.key, f]))
  return fieldOrder[templateKey.value].map((k) => byKey.get(k))
})
// 预览列跟着拖拽后的顺序走（占位段不成列）；auto 的"类型"列在表格里单独渲染
const previewColumns = computed(() => currentFields.value.filter((f) => f.key !== 'ignore'))
const isCustomOrder = computed(
  () => fieldOrder[templateKey.value].join() !== defaultKeys(templateKey.value).join()
)

// 布局切换（比如粘进来的 gmail 串从 "----" 换成 "|"）后，旧预览已经对不上
watch(templateKey, () => {
  previewList.value = []
})

function persistOrder() {
  try {
    localStorage.setItem(ORDER_KEY, JSON.stringify(fieldOrder))
  } catch {
    // 无痕模式等场景下写不进去，不影响本次使用
  }
}

function moveField(from, to) {
  const list = fieldOrder[templateKey.value]
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
  // Firefox 必须 setData 才会真正进入拖拽
  e.dataTransfer.setData('text/plain', String(i))
}

function onDragOver(i, e) {
  e.preventDefault()
  e.dataTransfer.dropEffect = 'move'
  overIndex.value = i
}

function onDragLeave(i) {
  if (overIndex.value === i) {
    overIndex.value = -1
  }
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
  if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') {
    return
  }
  const to = e.key === 'ArrowLeft' ? i - 1 : i + 1
  if (to < 0 || to >= currentFields.value.length) {
    return
  }
  e.preventDefault()
  moveField(i, to)
  // 焦点跟着色块一起移动，方便连续按键调整
  nextTick(() => schemaEl.value?.querySelectorAll('.chip')[to]?.focus())
}

function resetOrder() {
  fieldOrder[templateKey.value] = defaultKeys(templateKey.value)
  persistOrder()
  previewList.value = []
}

/** 色块即解析依据：当前顺序总是随请求发给后端。auto 也发送字段顺序，后端按邮箱后缀归类。 */
function buildPayload() {
  return {
    emailType: emailType.value,
    rawData: keptLines.value.map((c) => c.raw).join('\n'),
    fields: [...fieldOrder[templateKey.value]]
  }
}

/* —— 逐行校验：统计 + 异常明细 —— */
const UUID_RE = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
// uuid / token 是"有就解析、没有就空着"的尾部字段，缺了不算问题；ignore 是占位段
const OPTIONAL_KEYS = new Set(['uuid', 'token', 'ignore'])
const MAX_ISSUES_SHOWN = 300

const keepBad = ref(false)
const issuesOpen = ref(false)

/**
 * 按当前模板切段。分隔符用捕获组保留，最后一个字段吞掉剩余原文，
 * 与后端 split(regex, limit) 的语义一致 —— token / cookie 内部含分隔符也不会被截断。
 * auto 逐行判定：含 "|" 按 "|"，否则按 3+ 连字符。
 */
function splitByTemplate(line, tpl) {
  const def = fieldDefs[tpl]
  const sepRe = tpl === 'auto' ? (line.includes('|') ? /(\|)/ : /(-{3,})/) : def.sepRe
  const max = def.fields.length
  const pieces = line.split(sepRe) // [段, 分隔符, 段, 分隔符, ...]
  const segs = []
  for (let i = 0; i < pieces.length; i += 2) {
    if (segs.length === max - 1) {
      segs.push(pieces.slice(i).join(''))
      return segs
    }
    segs.push(pieces[i])
  }
  return segs
}

const lineChecks = computed(() => {
  const tpl = templateKey.value
  const order = fieldOrder[tpl]
  const labelOf = new Map(fieldDefs[tpl].fields.map((f) => [f.key, f.label]))
  const emailIdx = order.indexOf('email')
  const uuidIdx = order.indexOf('uuid')
  // 最后一个"核心字段"的下标：只有核心字段没对应数据才提示
  let lastCore = -1
  order.forEach((k, i) => {
    if (!OPTIONAL_KEYS.has(k)) lastCore = i
  })

  const out = []
  const lines = rawData.value.split('\n')
  for (let i = 0; i < lines.length; i++) {
    const raw = lines[i].replace(/\r$/, '').trim()
    if (raw === '') {
      continue // 空行忽略
    }
    const segs = splitByTemplate(raw, tpl)
    const reasons = []
    let kind = 'ok'
    const warn = (msg) => {
      if (kind === 'ok') kind = 'warn'
      reasons.push(msg)
    }

    if (emailIdx >= 0 && !EMAIL_RE.test(segs[emailIdx] || '')) {
      kind = 'err'
      reasons.push(`第 ${emailIdx + 1} 段不是邮箱格式`)
    }
    if (segs.length <= lastCore) {
      warn(`仅 ${segs.length} 段，第 ${lastCore + 1} 个字段「${labelOf.get(order[lastCore])}」起没有对应数据`)
    }
    if (uuidIdx >= 0 && segs[uuidIdx] && !UUID_RE.test(segs[uuidIdx])) {
      warn(`第 ${uuidIdx + 1} 段不是标准 UUID 格式（已按位置映射，请核对）`)
    }
    // gmail 两套布局按整份输入判定，混着粘会错位
    if (emailType.value === 'gmail') {
      const hasPipe = raw.includes('|')
      if (tpl === 'gmail' && !hasPipe) {
        warn('该行不含 “|”，与当前 “|” 布局不符')
      } else if (tpl === 'gmail-dash' && hasPipe) {
        warn('该行含 “|”，与当前 “----” 布局不符')
      }
    }

    out.push({
      lineNo: i + 1,
      raw,
      kind,
      reasons,
      short: raw.length > 120 ? `${raw.slice(0, 120)}…` : raw
    })
  }
  return out
})

const lineStats = computed(() => {
  const all = lineChecks.value
  const errors = all.filter((c) => c.kind === 'err').length
  const warns = all.filter((c) => c.kind === 'warn').length
  // 与参考稿一致：警告行仍算可解析，只有错误行才被剔除
  return { total: all.length, errors, warns, ok: all.length - errors, bad: errors + warns }
})

const issues = computed(() => lineChecks.value.filter((c) => c.kind !== 'ok'))
const shownIssues = computed(() => issues.value.slice(0, MAX_ISSUES_SHOWN))

const badge = computed(() => {
  const { total, errors, warns } = lineStats.value
  if (!errors && !warns) {
    return { cls: 'none', text: total > 0 ? '全部正常' : '等待输入' }
  }
  const parts = []
  if (errors) parts.push(`${errors} 个异常`)
  if (warns) parts.push(`${warns} 个警告`)
  return { cls: errors > 0 ? 'err' : 'warn', text: parts.join(' · ') }
})

/** 实际送去解析 / 入库的行：异常行按开关决定保留还是剔除。 */
const keptLines = computed(() =>
  lineChecks.value.filter((c) => keepBad.value || c.kind !== 'err')
)
const droppedCount = computed(() => lineChecks.value.length - keptLines.value.length)

// 出现异常时自动展开明细（只展开，不强制收起）
watch(
  () => lineStats.value.bad,
  (n) => {
    if (n > 0) issuesOpen.value = true
  }
)

function loadExample() {
  rawData.value = emailType.value === 'auto' ? examples.auto : examples[templateKey.value]
  previewList.value = []
}

function clearAll() {
  rawData.value = ''
  previewList.value = []
}

async function copyPreview() {
  const cols = previewColumns.value
  const header = cols.map((c) => c.label).join('\t')
  const body = previewList.value
    .map((row) => cols.map((c) => row[c.key] ?? '').join('\t'))
    .join('\n')
  try {
    await navigator.clipboard.writeText(`${header}\n${body}`)
    ElMessage.success(`已复制 ${previewList.value.length} 行到剪贴板`)
  } catch {
    ElMessage.error('复制失败，请手动选中复制')
  }
}

// 邮箱提取：从每行取第一段（"----" 之前）作为邮箱，实时批量提取
const JOIN = { newline: '\n', comma: ',', commaspace: ', ', semicolon: ';', space: ' ' }
const EX_EXAMPLE = [
  'TimothyFuller1177@outlook.com----levdsh925786----M.C525_SN1.0.U.Sample----9e5f94bc-e8a4-4e73-b8be-63364c29d753',
  'jane.doe93@outlook.com----pw83kfd----M.C525_SN1.0.U.Sample----1a2b3c4d-5e6f-7081-92a3-b4c5d6e7f809',
  'TIMOTHYFULLER1177@outlook.com----dup4test----M.C525_Duplicate----00000000-0000-0000-0000-000000000000',
  '----missing-email----M.C525_None----11111111-1111-1111-1111-111111111111'
].join('\n')

const exInput = ref('')
const exDedup = ref(false)
const exJoin = ref('newline')
const exIssuesOpen = ref(false)

const exShort = (s) => (s.length > 120 ? `${s.slice(0, 120)}…` : s)

// 逐行解析：有效邮箱进 valid，其余按行号进 issues（有效 + 异常 = 输入行）
const exParsed = computed(() => {
  const valid = []
  const issues = []
  let total = 0
  for (const [i, line] of exInput.value.split('\n').entries()) {
    const raw = line.replace(/\r$/, '').trim()
    if (raw === '') continue
    total++
    const first = raw.split('----')[0].trim()
    if (first === '') {
      issues.push({ lineNo: i + 1, reason: '行首为空，未找到邮箱', short: exShort(raw) })
    } else if (!EMAIL_RE.test(first)) {
      const head = first.length > 40 ? `${first.slice(0, 40)}…` : first
      issues.push({ lineNo: i + 1, reason: `第一段不是有效邮箱格式：${head}`, short: exShort(raw) })
    } else {
      valid.push(first)
    }
  }
  return { total, valid, issues }
})

const exLineTotal = computed(() => exParsed.value.total)
const exValidCount = computed(() => exParsed.value.valid.length)
const exIssues = computed(() => exParsed.value.issues)
const exShownIssues = computed(() => exIssues.value.slice(0, MAX_ISSUES_SHOWN))

// 去重（忽略大小写，保留首次出现）
const exDeduped = computed(() => {
  if (!exDedup.value) return { list: exParsed.value.valid, removed: 0 }
  const seen = new Set()
  const uniq = []
  for (const e of exParsed.value.valid) {
    const k = e.toLowerCase()
    if (!seen.has(k)) { seen.add(k); uniq.push(e) }
  }
  return { list: uniq, removed: exParsed.value.valid.length - uniq.length }
})
const exRemoved = computed(() => exDeduped.value.removed)
const exResultCount = computed(() => exDeduped.value.list.length)
const exOutput = computed(() => exDeduped.value.list.join(JOIN[exJoin.value] ?? '\n'))
const exOutHint = computed(() =>
  exDedup.value
    ? `已去重：${exValidCount.value} 个 → ${exResultCount.value} 个（移除 ${exRemoved.value} 个重复）`
    : '结果随输入实时更新，只读。'
)

// 出现异常时自动展开明细
watch(() => exIssues.value.length, (n) => { if (n > 0) exIssuesOpen.value = true })

function exLoadExample() {
  exInput.value = EX_EXAMPLE
}

async function exCopy() {
  if (!exOutput.value) { ElMessage.warning('没有可复制的结果'); return }
  try {
    await navigator.clipboard.writeText(exOutput.value)
    ElMessage.success(`已复制 ${exResultCount.value} 个邮箱到剪贴板`)
  } catch {
    ElMessage.error('复制失败，请手动选中复制')
  }
}

function exStamp() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}_${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`
}

function exDownload() {
  if (!exOutput.value) { ElMessage.warning('没有可下载的内容'); return }
  const blob = new Blob([`${exOutput.value}\n`], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `emails_${exStamp()}.txt`
  document.body.appendChild(a)
  a.click()
  setTimeout(() => { URL.revokeObjectURL(url); a.remove() }, 800)
  ElMessage.success(`已下载 ${a.download}`)
}

/* —— 解析预览增强：2FA 实时验证码 / 掩码 / 批量复制 —— */

// base32 解码（TOTP 密钥常见编码，忽略空格与大小写）
function base32Decode(input) {
  const A = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'
  const clean = (input || '').toUpperCase().replace(/[^A-Z2-7]/g, '')
  let bits = 0
  let value = 0
  const out = []
  for (const ch of clean) {
    const idx = A.indexOf(ch)
    if (idx < 0) continue
    value = (value << 5) | idx
    bits += 5
    if (bits >= 8) { bits -= 8; out.push((value >>> bits) & 0xff) }
  }
  return new Uint8Array(out)
}
// 密钥是否能作为 TOTP 密钥（能解出字节）
function isTotp(secret) {
  return !!secret && base32Decode(secret).length > 0
}
function counterBytes(counter) {
  const b = new Uint8Array(8)
  let c = counter
  for (let i = 7; i >= 0; i--) { b[i] = c & 0xff; c = Math.floor(c / 256) }
  return b
}
// TOTP：30 秒周期、SHA-1、6 位；用 Web Crypto 算 HMAC
async function totp(secret) {
  const key = base32Decode(secret)
  if (!key.length) return null
  try {
    const counter = Math.floor(Date.now() / 1000 / 30)
    const ck = await crypto.subtle.importKey('raw', key, { name: 'HMAC', hash: 'SHA-1' }, false, ['sign'])
    const sig = new Uint8Array(await crypto.subtle.sign('HMAC', ck, counterBytes(counter)))
    const off = sig[sig.length - 1] & 0x0f
    const bin = ((sig[off] & 0x7f) << 24) | ((sig[off + 1] & 0xff) << 16) |
      ((sig[off + 2] & 0xff) << 8) | (sig[off + 3] & 0xff)
    return (bin % 1000000).toString().padStart(6, '0')
  } catch {
    return null
  }
}

const nowMs = ref(Date.now())
const otpCodes = reactive({})           // 行下标 -> '123456' | 'invalid'
let lastCounter = -1
let totpTimer = null

// 当前 30 秒周期剩余秒数（各行同步）
const totpLeft = computed(() => 30 - (Math.floor(nowMs.value / 1000) % 30))
// 倒计时环：conic-gradient 表示剩余比例，最后 5 秒转红
const ringStyle = computed(() => {
  const deg = (totpLeft.value / 30) * 360
  const color = totpLeft.value <= 5 ? 'var(--bad)' : 'var(--accent)'
  return { background: `conic-gradient(${color} ${deg}deg, var(--border) 0)` }
})

const fmtOtp = (code) => {
  if (!code) return '••• •••'
  if (code === 'invalid') return '密钥无效'
  return `${code.slice(0, 3)} ${code.slice(3)}`
}

async function refreshOtps() {
  for (const [i, row] of previewList.value.entries()) {
    if (isTotp(row.authKey)) {
      otpCodes[i] = (await totp(row.authKey)) || 'invalid'
    }
  }
}
function totpTick() {
  nowMs.value = Date.now()
  const counter = Math.floor(nowMs.value / 1000 / 30)
  if (counter !== lastCounter) { lastCounter = counter; refreshOtps() }
}
onMounted(() => { totpTimer = setInterval(totpTick, 1000) })
onBeforeUnmount(() => { if (totpTimer) clearInterval(totpTimer) })
// 预览变化时清空旧验证码并立即重算
watch(previewList, () => {
  for (const k of Object.keys(otpCodes)) delete otpCodes[k]
  lastCounter = -1
  totpTick()
})

// 敏感字段掩码
const previewMasked = ref(false)
const SENSITIVE = new Set(['password', 'recoveryKey', 'authKey', 'cookie', 'token', 'refreshToken'])
function maskVal(v) {
  if (!v) return ''
  if (v.length <= 2) return '••'
  return v[0] + '•'.repeat(Math.min(8, Math.max(3, v.length - 2))) + v.slice(-1)
}
function maskIf(key, v) {
  return previewMasked.value && SENSITIVE.has(key) ? maskVal(v) : v
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
// 整列复制（跳过空值）
function copyColumn(key, label) {
  const vals = previewList.value.map((r) => r[key]).filter((v) => v)
  if (!vals.length) { ElMessage.warning(`「${label}」整列无数据`); return }
  copyText(vals.join('\n'), `已复制整列：${label}（${vals.length} 项）`)
}
// 按当前字段顺序拼成 ---- 行，去掉末尾连续空字段
function previewToLine(row) {
  const arr = previewColumns.value.map((c) => row[c.key] || '')
  let last = arr.length - 1
  while (last >= 0 && arr[last] === '') last--
  return arr.slice(0, last + 1).join('----')
}
function copyAllLines() {
  copyText(previewList.value.map(previewToLine).join('\n'), `已复制全部 ${previewList.value.length} 条（---- 格式）`)
}
// 复制为表格（TSV，可直接粘进 Excel）
function copyTsv() {
  const cols = previewColumns.value
  const header = cols.map((c) => c.label).join('\t')
  const body = previewList.value.map((r) => cols.map((c) => r[c.key] ?? '').join('\t')).join('\n')
  copyText(`${header}\n${body}`, '已复制为表格，可粘贴到 Excel')
}

// 列表列：展示数据库所有字段（id / emailType / createTime / updateTime 在模板中单独成列）
const fullColumns = [
  { prop: 'email', label: '邮箱', width: 200 },
  { prop: 'password', label: '密码', width: 130 },
  { prop: 'recoveryEmail', label: '备用邮箱', width: 200 },
  { prop: 'recoveryKey', label: 'key', width: 180 },
  { prop: 'regYear', label: '年份', width: 80 },
  { prop: 'country', label: '国家', width: 100 },
  { prop: 'authKey', label: '辅助验证码', width: 200 },
  { prop: 'extraUrl', label: '链接', width: 240 },
  { prop: 'refreshToken', label: 'refreshToken', width: 220 },
  { prop: 'clientId', label: 'clientId', width: 200 },
  { prop: 'note', label: '说明', width: 200 },
  { prop: 'cookie', label: 'cookie', width: 220 },
  { prop: 'uuid', label: 'UUID', width: 200 },
  { prop: 'token', label: 'token', width: 220 },
  { prop: 'rawData', label: '原始信息', width: 300 }
]
const listColumns = computed(() => fullColumns)

const typeLabel = (t) => ({ gmail: 'gmail', '012e': '012e', outlook: 'outlook' }[t] || t)
const tagType = (t) => ({ gmail: 'danger', '012e': 'success', outlook: 'primary' }[t] || 'info')

function onTypeChange() {
  previewList.value = []
}

/** 异常行被剔除时补一句说明，让数量对得上。 */
function droppedSuffix() {
  return droppedCount.value ? `，跳过 ${droppedCount.value} 个异常行` : ''
}

async function handleParse() {
  if (!keptLines.value.length) {
    ElMessage.warning(lineStats.value.total ? '所有行都是异常行，已全部剔除' : '请输入原始信息')
    return
  }
  parsing.value = true
  try {
    const res = await parseEmail(buildPayload())
    previewList.value = res.data || []
    if (!previewList.value.length) {
      ElMessage.warning('未解析出有效数据')
    } else {
      ElMessage.success(`解析成功 ${previewList.value.length} 条${droppedSuffix()}`)
    }
  } finally {
    parsing.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    const res = await saveEmail(buildPayload())
    ElMessage.success(`已入库 ${res.data.length} 条${droppedSuffix()}`)
    rawData.value = ''
    previewList.value = []
    query.page = 1
    // auto 混合入库后按"全部类型"刷新（库里存的是识别后的具体类型，没有 auto）
    query.emailType = emailType.value === 'auto' ? '' : emailType.value
    await loadList()
  } finally {
    saving.value = false
  }
}

async function loadStats() {
  const totalOf = (emailType) => listEmail({ emailType, page: 1, size: 1 })
    .then((r) => r.data.total || 0).catch(() => 0)
  const [all, gmail, e012, outlook] = await Promise.all([
    totalOf(undefined), totalOf('gmail'), totalOf('012e'), totalOf('outlook')
  ])
  stats.total = all
  stats.gmail = gmail
  stats.e012 = e012
  stats.outlook = outlook
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
  loadStats()
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
.page-head { margin-bottom: 18px; }
.page-head .eyebrow { display: block; margin-bottom: 6px; }
.page-head h1 { font-size: clamp(24px, 4vw, 32px); margin: 0; color: var(--ink); }
.lede { color: var(--muted); margin: 8px 0 0; max-width: 60ch; }

.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
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
.stat .n {
  font-size: 26px;
  color: var(--ink);
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
}
.stat .k { font-size: 12px; color: var(--muted); }

.section {
  margin-bottom: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.head-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.head-text .eyebrow { display: flex; align-items: center; gap: 7px; }
.head-title {
  font-weight: 600;
  font-size: 15px;
  color: var(--ink);
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent);
  display: inline-block;
}
.dot.good { background: var(--good); }

/* —— 字段顺序 chip 图例（可拖拽排序） —— */
.schema-block { width: 100%; }
.schema-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.schema {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 2px 0;
  flex: 1;
  min-width: 0;
}
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
.chip:focus-visible {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
}
.chip:active { cursor: grabbing; }
.chip.dragging { opacity: .4; }
/* 松手会落到这个位置 */
.chip.over {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
  transform: translateY(-1px);
}
/* 占位段：原始串里固定出现、但不入库的那一段 */
.chip.ghost {
  border-style: dashed;
  color: var(--faint);
}
.chip .grip {
  font-size: 11px;
  color: var(--faint);
  margin-right: 6px;
}
.chip .pos { color: var(--faint); font-size: 10px; margin-right: 6px; }
.sep { font-family: var(--mono); color: var(--faint); font-size: 12px; user-select: none; }
.reset-btn { flex: none; }
.schema-hint {
  font-size: 12px;
  color: var(--faint);
  line-height: 1.6;
  margin-top: 4px;
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
.auto-hint {
  line-height: 1.7;
  max-width: 78ch;
}
.auto-hint b { color: var(--accent); font-weight: 600; }

/* —— 类型选择做成 chip 风格 —— */
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

/* —— 原始信息 textarea：等宽、无边框，融进 pane —— */
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
.raw-input :deep(.el-textarea__inner:focus-visible) {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

/* —— 操作栏 —— */
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin: 4px 0 12px;
}
.toolbar .spacer { flex: 1 1 auto; }
.toolbar :deep(.el-switch__label) {
  font-size: 12.5px;
  color: var(--muted);
  font-weight: 400;
}
.toolbar :deep(.el-switch__label.is-active) { color: var(--accent); }

/* —— 输入 / 输出 双栏 —— */
.panes {
  display: grid;
  grid-template-columns: minmax(0, 400px) minmax(0, 1fr);
  gap: 16px;
}
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
.pane-head .label {
  display: flex;
  align-items: center;
  gap: 9px;
  font-weight: 600;
  font-size: 13px;
  color: var(--ink);
}
.pane-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent);
  flex: none;
}
.pane.out .pane-dot { background: var(--good); }
.pane-head .count {
  font-family: var(--mono);
  font-size: 11.5px;
  color: var(--faint);
  font-variant-numeric: tabular-nums;
}
.pane-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 12px 15px;
}
.pane-empty {
  color: var(--faint);
  font-size: 12.5px;
  padding: 28px 0;
  text-align: center;
}
.hint {
  font-size: 11.5px;
  color: var(--faint);
  padding: 0 15px 12px;
}

/* —— 解析预览增强：工具条 / 整列复制 / 2FA 验证码 —— */
.preview-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.preview-tools .spacer { flex: 1 1 auto; }
.col-head {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.col-copy {
  cursor: pointer;
  color: var(--faint);
  font-size: 12px;
  transition: color .12s ease;
}
.col-copy:hover { color: var(--accent); }
.cell-val {
  display: inline-flex;
  align-items: baseline;
  gap: 5px;
  min-width: 0;
}
.cell-txt {
  font-family: var(--mono);
  font-size: 12px;
  word-break: break-all;
}
.cell-empty { color: var(--faint); opacity: .5; }
.cell-link {
  font-family: var(--mono);
  font-size: 12px;
  color: var(--accent);
  text-decoration: none;
  word-break: break-all;
}
.cell-link:hover { text-decoration: underline; }
.copy-icon.always { opacity: 1; }

/* 2FA 密钥 + 实时验证码 */
.fa-cell {
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-width: 180px;
}
.otp {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 5px 8px;
  width: fit-content;
}
.otp-ring {
  position: relative;
  flex: none;
  width: 22px;
  height: 22px;
  border-radius: 50%;
}
/* 中间镂空，露出秒数 */
.otp-ring::after {
  content: '';
  position: absolute;
  inset: 3px;
  border-radius: 50%;
  background: var(--surface-2);
}
.otp-ring i {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  font-family: var(--mono);
  font-size: 9px;
  font-style: normal;
  color: var(--muted);
  z-index: 1;
}
.otp-code {
  font-family: var(--mono);
  font-size: 16px;
  font-weight: 600;
  letter-spacing: .04em;
  color: var(--ink);
  font-variant-numeric: tabular-nums;
  line-height: 1;
}
.otp-code.invalid {
  font-size: 12px;
  font-weight: 500;
  color: var(--bad);
  letter-spacing: 0;
}

.check-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 16px;
}
.check-stats .stat.ok .n { color: var(--good); }
.check-stats .stat.err .n { color: var(--bad); }
.check-stats .stat.err.zero .n { color: var(--faint); }

/* —— 邮箱提取卡片 —— */
.ex-desc {
  color: var(--muted);
  font-size: 12.5px;
  line-height: 1.6;
  margin: 0 0 16px;
  max-width: 72ch;
}
.ex-desc b { color: var(--accent); font-weight: 600; }
.ex-field {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12.5px;
  color: var(--muted);
}
.extract-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-top: 16px;
}
.extract-stats .stat.ok .n { color: var(--good); }
.extract-stats .stat.dup .n { color: var(--swap); }
.extract-stats .stat.dup.zero .n { color: var(--faint); }
.extract-stats .stat.err .n { color: var(--bad); }
.extract-stats .stat.err.zero .n { color: var(--faint); }

/* —— 异常明细 —— */
.issues {
  margin-top: 16px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 14px;
  overflow: hidden;
}
.issues summary {
  list-style: none;
  cursor: pointer;
  padding: 13px 16px;
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
  font-size: 13px;
  color: var(--ink);
}
.issues summary::-webkit-details-marker { display: none; }
.issues summary .chev {
  transition: transform .15s ease;
  color: var(--faint);
}
.issues[open] summary .chev { transform: rotate(90deg); }
.badge {
  font-family: var(--mono);
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 99px;
  font-weight: 500;
}
.badge.err { background: var(--bad-soft); color: var(--bad); }
.badge.warn { background: var(--swap-soft); color: var(--swap); }
.badge.none { background: var(--good-soft); color: var(--good); }
.issue-list {
  border-top: 1px solid var(--border);
  max-height: 320px;
  overflow: auto;
}
.issue {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 12px;
  align-items: baseline;
  padding: 10px 16px;
  border-bottom: 1px solid var(--border);
  border-left: 3px solid transparent;
}
.issue:last-child { border-bottom: 0; }
.issue.err { border-left-color: var(--bad); }
.issue.warn { border-left-color: var(--swap); }
.issue .ln {
  font-family: var(--mono);
  font-size: 11.5px;
  color: var(--faint);
  white-space: nowrap;
}
.issue .body { min-width: 0; }
.issue .reason { font-size: 12.5px; }
.issue.err .reason { color: var(--bad); }
.issue.warn .reason { color: var(--swap); }
.issue-preview {
  font-family: var(--mono);
  font-size: 11.5px;
  color: var(--muted);
  margin-top: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.issue-note {
  padding: 18px 16px;
  color: var(--muted);
  font-size: 12.5px;
}

.filters {
  display: flex;
  align-items: center;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
.empty {
  color: var(--faint);
  opacity: .5;
}
.copy-icon {
  cursor: pointer;
  color: var(--accent);
  margin-left: 4px;
  vertical-align: middle;
  opacity: 0;
  transition: opacity .12s ease;
}
:deep(.el-table__row:hover) .copy-icon { opacity: 1; }

@media (max-width: 1000px) {
  .panes { grid-template-columns: 1fr; }
}
@media (max-width: 820px) {
  .extract-stats { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 720px) {
  .stats { grid-template-columns: repeat(2, 1fr); }
  .check-stats { grid-template-columns: 1fr; }
}
</style>
