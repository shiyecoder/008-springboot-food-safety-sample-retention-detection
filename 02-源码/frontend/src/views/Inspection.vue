<template>
  <div>
    <div class="page-h">
      <div>
        <h1>检测登记</h1>
        <p>共 {{ stats.total || 0 }} 条检测记录 · 已合格 {{ stats.QUALIFIED || 0 }} · 不合格 {{ stats.UNQUALIFIED || 0 }} · 待复检 {{ stats.PENDING_REVIEW || 0 }}</p>
      </div>
      <div class="acts">
        <button class="btn btn-primary" @click="openCreate">登记抽检</button>
      </div>
    </div>

    <div class="tabs" style="margin-bottom: 14px">
      <a v-for="t in tabs" :key="t.value" :class="{ on: q.tab === t.value }" @click="switchTab(t.value)">
        {{ t.label }}<span class="cnt">{{ t.count }}</span>
      </a>
    </div>

    <div class="filters">
      <div class="fi"><label>关键字</label>
        <input v-model="q.keyword" class="inp wide" placeholder="检测编号 / 留样编号 / 食品" @keyup.enter="search" />
      </div>
      <div class="fi"><label>检测类型</label>
        <select v-model="q.type" class="sel" style="min-width: 120px">
          <option value="">全部类型</option>
          <option v-for="t in types" :key="t">{{ t }}</option>
        </select>
      </div>
      <div class="sp">
        <button class="btn btn-ghost" @click="reset">重置</button>
        <button class="btn btn-primary" @click="search">查询</button>
      </div>
    </div>

    <div class="tbl-wrap">
      <table class="tbl">
        <thead>
          <tr>
            <th>检测编号</th><th>留样</th><th>类型 / 方式</th><th class="r">项目数</th>
            <th>检测人</th><th>检测时间</th><th>结论</th><th>状态</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in rows" :key="r.id">
            <td class="id nowrap">{{ r.inspectNo }}</td>
            <td>
              <div class="food-cell">
                <div><b>{{ r.foodName }}</b><span>{{ r.sampleNo }}</span></div>
              </div>
            </td>
            <td>{{ r.inspectType }}<span class="weak"> · {{ r.inspectMethod }}</span></td>
            <td class="r mono-num">{{ r.itemCount }}</td>
            <td>{{ r.inspectorName }}</td>
            <td class="nowrap muted">{{ formatTime(r.inspectTime) }}</td>
            <td><span class="tag" :class="resultTone(r.result)">{{ r.resultLabel || resultLabel(r.result) }}</span></td>
            <td><span class="tag" :class="toneOf(r.status)">{{ r.statusLabel }}</span></td>
            <td>
              <div class="op">
                <button class="btn btn-text btn-sm" @click="openDetail(r)">详情</button>
                <button v-if="r.status === 'PENDING_REVIEW' && canReview" class="btn btn-text btn-sm" @click="openReview(r)">复核结论</button>
              </div>
            </td>
          </tr>
          <tr v-if="!rows.length"><td colspan="9" class="empty">暂无检测记录</td></tr>
        </tbody>
      </table>
      <div class="pager">
        <span>共 {{ total }} 条 · 每页 {{ q.size }} 条</span>
        <div class="pgs">
          <span class="pg" @click="page(-1)">‹</span>
          <span v-for="p in pages" :key="p" class="pg" :class="{ on: p === q.page }" @click="goto(p)">{{ p }}</span>
          <span class="pg" @click="page(1)">›</span>
        </div>
      </div>
    </div>

    <!-- 登记抽检 -->
    <el-dialog v-model="createVisible" title="登记抽检" width="720">
      <div class="form-row">
        <label><span class="req">*</span>选择留样</label>
        <div class="ctrl">
          <el-select v-model="form.sampleId" filterable placeholder="按留样编号或食品搜索" style="width: 100%">
            <el-option v-for="s in samples" :key="s.id" :label="`${s.sampleNo} · ${s.foodName}（${s.statusLabel}）`" :value="s.id" />
          </el-select>
        </div>
      </div>
      <div class="form-row">
        <label>检测类型</label>
        <div class="ctrl">
          <select v-model="form.inspectType" class="sel" style="max-width: 180px">
            <option v-for="t in types" :key="t">{{ t }}</option>
          </select>
        </div>
      </div>
      <div class="form-row">
        <label>检测方式</label>
        <div class="ctrl">
          <select v-model="form.inspectMethod" class="sel" style="max-width: 200px">
            <option>随机抽检</option><option>指定抽检</option><option>专项抽检</option>
          </select>
        </div>
      </div>
      <div class="group-t">检测项目</div>
      <table class="tbl" style="margin-bottom: 8px">
        <thead><tr><th style="width: 34%">项目</th><th style="width: 22%">标准值</th><th style="width: 22%">实测值</th><th style="width: 22%">判定</th></tr></thead>
        <tbody>
          <tr v-for="(it, i) in form.items" :key="i">
            <td><input v-model="it.itemName" class="inp" style="min-width: 0; width: 100%" /></td>
            <td><input v-model="it.standardValue" class="inp" style="min-width: 0; width: 100%" /></td>
            <td><input v-model="it.actualValue" class="inp" style="min-width: 0; width: 100%" /></td>
            <td>
              <select v-model="it.judge" class="sel" style="min-width: 0; width: 100%">
                <option value="合格">合格</option><option value="不合格">不合格</option>
              </select>
            </td>
          </tr>
        </tbody>
      </table>
      <button class="btn btn-ghost btn-sm" @click="addItem">+ 增加项目</button>
      <div class="form-row" style="margin-top: 14px">
        <label>检测结论</label>
        <div class="ctrl">
          <el-radio-group v-model="form.result">
            <el-radio value="QUALIFIED">合格</el-radio>
            <el-radio value="UNQUALIFIED">不合格（自动生成事件并锁定留样）</el-radio>
            <el-radio value="RECHECK">待复检</el-radio>
          </el-radio-group>
        </div>
      </div>
      <div class="form-row">
        <label>备注</label>
        <div class="ctrl"><el-input v-model="form.remark" type="textarea" :rows="2" /></div>
      </div>
      <template #footer>
        <button class="btn btn-ghost" @click="createVisible = false">取消</button>
        <button class="btn btn-primary" style="margin-left: 8px" @click="submitCreate">提交检测</button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" title="检测详情" width="680">
      <div v-if="detail">
        <div class="summary">
          <div class="s">检测编号<b>{{ detail.inspectNo }}</b></div>
          <div class="s">留样<b>{{ detail.sampleNo }}</b></div>
          <div class="s">结论<b>{{ detail.resultLabel }}</b></div>
          <div class="s">检测人<b>{{ detail.inspectorName }}</b></div>
        </div>
        <table class="tbl">
          <thead><tr><th>检测项目</th><th>标准值</th><th>实测值</th><th>判定</th></tr></thead>
          <tbody>
            <tr v-for="it in detail.items || []" :key="it.id">
              <td>{{ it.itemName }}</td><td class="muted">{{ it.standardValue }}</td>
              <td class="mono-num">{{ it.actualValue }}</td>
              <td><span class="tag" :class="it.judge === '合格' ? 'ok' : 'bad'">{{ it.judge }}</span></td>
            </tr>
            <tr v-if="!detail.items?.length"><td colspan="4" class="empty">暂无项目明细</td></tr>
          </tbody>
        </table>
        <div class="note" style="margin-top: 12px">{{ detail.remark || '无备注' }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/utils/request'
import { formatTime, toneOf } from '@/utils/format'
import { userStore } from '@/store/user'

const types = ['感官', '微生物', '添加剂', '综合']
const rows = ref([])
const total = ref(0)
const stats = ref({})
const samples = ref([])
const q = reactive({ tab: 'all', keyword: '', type: '', page: 1, size: 10 })
const createVisible = ref(false)
const detailVisible = ref(false)
const detail = ref(null)
const form = reactive({ sampleId: null, inspectType: '微生物', inspectMethod: '随机抽检', result: 'QUALIFIED', remark: '', items: [] })

const canReview = computed(() => ['SUPERVISOR', 'ADMIN'].includes(userStore.info?.roleCode))
const tabs = computed(() => [
  { label: '全部', value: 'all', count: stats.value.total || 0 },
  { label: '待检测', value: 'PENDING', count: stats.value.PENDING || 0 },
  { label: '检测中', value: 'TESTING', count: stats.value.TESTING || 0 },
  { label: '待复核', value: 'PENDING_REVIEW', count: stats.value.PENDING_REVIEW || 0 },
  { label: '已合格', value: 'QUALIFIED', count: stats.value.QUALIFIED || 0 },
  { label: '不合格', value: 'UNQUALIFIED', count: stats.value.UNQUALIFIED || 0 }
])
const pages = computed(() => Array.from({ length: Math.min(6, Math.max(1, Math.ceil(total.value / q.size))) }, (_, i) => i + 1))
const resultTone = (v) => ({ QUALIFIED: 'ok', UNQUALIFIED: 'bad', RECHECK: 'warn' }[v] || 'plain')
const resultLabel = (v) => ({ QUALIFIED: '合格', UNQUALIFIED: '不合格', RECHECK: '待复检' }[v] || '待判定')

async function loadStats() {
  stats.value = await http.get('/api/inspection/stats')
}

async function load() {
  const res = await http.get('/api/inspection/page', {
    params: { page: q.page, size: q.size, keyword: q.keyword || undefined, type: q.type || undefined, tab: q.tab === 'all' ? undefined : q.tab }
  })
  rows.value = res.records
  total.value = res.total
}

function switchTab(v) {
  q.tab = v
  q.page = 1
  load()
}
function search() {
  q.page = 1
  load()
}
function reset() {
  q.keyword = ''
  q.type = ''
  q.tab = 'all'
  search()
}
function goto(p) {
  q.page = p
  load()
}
function page(d) {
  const n = Math.max(1, Math.ceil(total.value / q.size))
  goto(Math.min(n, Math.max(1, q.page + d)))
}

function addItem() {
  form.items.push({ itemName: '', standardValue: '', actualValue: '合格', judge: '合格' })
}

async function openCreate() {
  const res = await http.get('/api/food-sample/page', { params: { page: 1, size: 50 } })
  samples.value = res.records.filter((s) => ['RETAINING', 'PENDING_REVIEW'].includes(s.status))
  form.sampleId = samples.value[0]?.id || null
  form.items = [
    { itemName: '菌落总数', standardValue: '≤ 10⁵ CFU/g', actualValue: '合格', judge: '合格' },
    { itemName: '大肠菌群', standardValue: '≤ 100 MPN/100g', actualValue: '合格', judge: '合格' }
  ]
  createVisible.value = true
}

async function submitCreate() {
  if (!form.sampleId) {
    ElMessage.warning('请选择留样')
    return
  }
  await http.post('/api/inspection', { ...form })
  ElMessage.success('检测已登记，等待主管复核')
  createVisible.value = false
  load()
  loadStats()
}

async function openDetail(r) {
  detail.value = await http.get(`/api/inspection/${r.id}`)
  detailVisible.value = true
}

async function openReview(r) {
  try {
    const { value } = await ElMessageBox.prompt('复核意见（不合格将自动生成事件并锁定留样）', `复核 ${r.inspectNo}`, {
      inputValue: '项目复测与原始记录一致，结论有效',
      inputPlaceholder: '复核意见'
    })
    await http.post(`/api/inspection/${r.id}/review`, { pass: r.result !== 'UNQUALIFIED', remark: value })
    ElMessage.success('复核完成')
    load()
    loadStats()
  } catch (e) {
    /* 取消 */
  }
}

onMounted(() => {
  load()
  loadStats()
})
</script>
