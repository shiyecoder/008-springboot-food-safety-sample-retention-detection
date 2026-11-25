<template>
  <div>
    <div class="page-h">
      <div>
        <h1>不合格事件</h1>
        <p>处置中 {{ stats.handling || 0 }} 起 · 已关闭 {{ stats.closed || 0 }} 起 · 被锁定留样 {{ stats.lockedSamples || 0 }} 份</p>
      </div>
      <div class="acts">
        <button class="btn btn-ghost" @click="reload">刷新</button>
      </div>
    </div>

    <div class="filters">
      <div class="fi"><label>关键字</label>
        <input v-model="q.keyword" class="inp wide" placeholder="事件编号 / 留样编号 / 食品" @keyup.enter="search" />
      </div>
      <div class="fi"><label>严重程度</label>
        <select v-model="q.severity" class="sel" style="min-width: 130px">
          <option value="">全部</option><option value="SERIOUS">严重</option><option value="NORMAL">一般</option><option value="CRITICAL">重大</option>
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
            <th>事件编号</th><th>留样 / 食品</th><th>事件类型</th><th>严重程度</th>
            <th>发生时间</th><th>发现人</th><th>封存位置</th><th>状态</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in rows" :key="r.id">
            <td class="id nowrap">{{ r.incidentNo }}</td>
            <td><div class="food-cell"><div><b>{{ r.foodName }}</b><span>{{ r.sampleNo }}</span></div></div></td>
            <td>{{ r.incidentType }}</td>
            <td><span class="tag" :class="severityTone(r.severity)">{{ r.severityLabel }}</span></td>
            <td class="nowrap muted">{{ formatTime(r.occurTime) }}</td>
            <td>{{ r.discoverer }}</td>
            <td class="mono-num">{{ r.sealLocation }}</td>
            <td><span class="tag" :class="toneOf(r.status)">{{ r.statusLabel }}</span></td>
            <td>
              <div class="op">
                <button class="btn btn-text btn-sm" @click="openDetail(r)">详情</button>
                <button v-if="r.status !== 'CLOSED' && canHandle" class="btn btn-text btn-sm" @click="close(r)">关闭事件</button>
              </div>
            </td>
          </tr>
          <tr v-if="!rows.length"><td colspan="9" class="empty">暂无不合格事件</td></tr>
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

    <el-drawer v-model="visible" :size="680" title="事件详情与追溯">
      <div v-if="current">
        <div class="summary">
          <div class="s">事件编号<b>{{ current.incidentNo }}</b></div>
          <div class="s">严重程度<b>{{ current.severityLabel }}</b></div>
          <div class="s">状态<b>{{ current.statusLabel }}</b></div>
        </div>
        <div class="group-t">事件信息</div>
        <table class="tbl" style="margin-bottom: 14px">
          <tbody>
            <tr><td class="muted" style="width: 110px">留样</td><td>{{ current.sampleNo }} · {{ current.foodName }}</td></tr>
            <tr><td class="muted">事件类型</td><td>{{ current.incidentType }}</td></tr>
            <tr><td class="muted">发生时间</td><td>{{ formatTime(current.occurTime) }}</td></tr>
            <tr><td class="muted">发现人</td><td>{{ current.discoverer }}</td></tr>
            <tr><td class="muted">封存位置</td><td>{{ current.sealLocation }}</td></tr>
          </tbody>
        </table>
        <div class="group-t">停止供餐与批次追溯</div>
        <div class="note" style="margin-bottom: 8px">{{ current.stopSupply }}</div>
        <div class="note" style="margin-bottom: 14px">{{ current.traceBatch }}</div>
        <div class="group-t">处理结论</div>
        <el-input v-model="conclusion" type="textarea" :rows="3" placeholder="填写跟进措施与关闭结论，如：同批次复检合格，恢复供餐" />
        <div class="note" style="margin-top: 10px">
          事件未关闭时，关联留样处于锁定状态，禁止进入销毁流程；关闭后自动解锁。
        </div>
      </div>
      <template #footer>
        <button class="btn btn-ghost" @click="visible = false">关闭弹层</button>
        <button v-if="current && current.status !== 'CLOSED'" class="btn btn-ghost" style="margin-left: 8px" @click="follow">保存跟进</button>
        <button v-if="current && current.status !== 'CLOSED'" class="btn btn-primary" style="margin-left: 8px" @click="close(current)">关闭事件并解锁</button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/utils/request'
import { formatTime, toneOf } from '@/utils/format'
import { userStore } from '@/store/user'

const rows = ref([])
const total = ref(0)
const stats = ref({})
const q = reactive({ keyword: '', severity: '', page: 1, size: 10 })
const visible = ref(false)
const current = ref(null)
const conclusion = ref('')

const canHandle = computed(() => ['SUPERVISOR', 'ADMIN', 'INSPECTOR'].includes(userStore.info?.roleCode))
const pages = computed(() => Array.from({ length: Math.min(6, Math.max(1, Math.ceil(total.value / q.size))) }, (_, i) => i + 1))
const severityTone = (v) => ({ CRITICAL: 'bad', SERIOUS: 'bad', NORMAL: 'warn', LOW: 'plain' }[v] || 'warn')

async function load() {
  const res = await http.get('/api/incident/page', { params: { ...q, severity: q.severity || undefined, keyword: q.keyword || undefined } })
  rows.value = res.records
  total.value = res.total
}
async function loadStats() {
  stats.value = await http.get('/api/incident/stats')
}
function search() {
  q.page = 1
  load()
}
function reset() {
  q.keyword = ''
  q.severity = ''
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
async function reload() {
  load()
  loadStats()
}

function openDetail(r) {
  current.value = r
  conclusion.value = r.conclusion || ''
  visible.value = true
}

async function follow() {
  await http.post(`/api/incident/${current.value.id}/follow`, { conclusion: conclusion.value })
  ElMessage.success('跟进记录已保存')
  reload()
}

async function close(r) {
  const text = conclusion.value || '同批次复检合格，整改完成，恢复供餐'
  await http.post(`/api/incident/${r.id}/close`, { conclusion: text })
  ElMessage.success('事件已关闭，关联留样已解锁')
  visible.value = false
  reload()
}

onMounted(reload)
</script>
