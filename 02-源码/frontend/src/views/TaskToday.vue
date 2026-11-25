<template>
  <div>
    <div class="portal-hero">
      <div>
        <h2>{{ greet }}，{{ info?.realName }}</h2>
        <p>{{ info?.orgName }} · 今日待留样 {{ today.pending }} 项，其中 {{ today.urgent }} 项距截止不足 60 分钟 · {{ info?.roleName }}</p>
      </div>
      <div class="stat">
        <div><b>{{ today.pending }}</b><span>待留样</span></div>
        <div><b>{{ today.urgent }}</b><span>即将超时</span></div>
        <div><b>{{ today.review }}</b><span>待复核</span></div>
        <div><b>{{ today.monthDone }}</b><span>本月已完成</span></div>
      </div>
    </div>

    <div v-if="today.todo && today.todo.length" class="note" style="margin-bottom: 14px; display: flex; align-items: center; gap: 10px">
      <span>
        <b style="color: var(--c-danger)">{{ today.todo.length }} 项待执行：</b>
        {{ urgentText }}，超时后任务自动标记逾期并计入完成率。
      </span>
      <button class="btn btn-danger btn-sm" style="margin-left: auto" @click="goRegister(today.todo[0])">立即处理</button>
    </div>

    <div class="filters">
      <div class="fi"><label>任务状态</label>
        <select v-model="q.status" class="sel">
          <option value="">全部状态</option>
          <option v-for="(v, k) in STATUS" :key="k" :value="k">{{ v }}</option>
        </select>
      </div>
      <div class="fi"><label>餐次</label>
        <select v-model="q.meal" class="sel" style="min-width: 120px">
          <option value="">全部餐次</option><option>早餐</option><option>午餐</option><option>晚餐</option>
        </select>
      </div>
      <div class="fi"><label>食品 / 任务编号</label>
        <input v-model="q.keyword" class="inp" placeholder="如 红烧 / RW2026" @keyup.enter="search" />
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
            <th>任务编号</th><th>食品名称</th><th>餐次</th><th>生产批次</th>
            <th class="r">标准重量</th><th>存放要求</th><th>截止时间</th><th>状态</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in rows" :key="r.id">
            <td class="id nowrap">{{ r.taskNo }}</td>
            <td>
              <div class="food-cell">
                <img class="thumb" :src="dishOf(r)" alt="" />
                <div><b>{{ r.foodName }}</b><span>{{ r.categoryName }} · {{ r.storageCond }}</span></div>
              </div>
            </td>
            <td>{{ r.meal }}</td>
            <td class="muted">{{ r.batchNo }}</td>
            <td class="r mono-num">≥ {{ r.minWeight }} g</td>
            <td class="muted">{{ r.storageCond }}</td>
            <td class="nowrap" :style="{ color: isUrgent(r) ? 'var(--c-danger)' : '' }">{{ formatTime(r.deadline) }}</td>
            <td><span class="tag" :class="toneOf(r.status)">{{ r.statusLabel }}</span></td>
            <td>
              <div class="op">
                <button v-if="r.status === 'PENDING'" class="btn btn-text btn-sm" @click="goRegister(r)">去留样</button>
                <button v-else-if="r.sampleId" class="btn btn-text btn-sm" @click="$router.push('/samples')">查看</button>
                <span v-else class="weak">—</span>
              </div>
            </td>
          </tr>
          <tr v-if="!rows.length"><td colspan="9" class="empty">没有符合条件的任务</td></tr>
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

    <div class="grid3" style="margin-top: 14px; grid-template-columns: 1.4fr 1fr 1fr">
      <div class="card">
        <div class="card-h"><h3>本周留样量</h3><span class="more" @click="$router.push('/samples')">查看明细</span></div>
        <div class="stat-strip">
          <div class="s"><b>{{ today.weekDone }}</b>本周完成留样</div>
          <div class="s"><b>{{ today.monthDone }}</b>本月完成</div>
          <div class="s"><b>{{ today.avgWeight }} g</b>平均重量</div>
          <div class="s"><b>{{ today.overdue }}</b>逾期任务</div>
        </div>
      </div>
      <div class="card">
        <div class="card-h"><h3>常去仓位</h3></div>
        <div style="display: flex; flex-direction: column; gap: 8px; font-size: 12.5px">
          <div v-for="c in hotSlots" :key="c.code" style="display: flex">
            <span class="muted">{{ c.code }}　{{ c.desc }}</span>
            <span style="margin-left: auto" class="mono-num">{{ c.times }} 次</span>
          </div>
          <div v-if="!hotSlots.length" class="empty">暂无仓位使用记录</div>
        </div>
      </div>
      <div class="card">
        <div class="card-h"><h3>状态概览</h3></div>
        <div class="stat-strip" style="flex-wrap: wrap; gap: 14px">
          <div class="s"><b>{{ today.pending }}</b>待执行</div>
          <div class="s"><b>{{ today.review }}</b>待复核</div>
          <div class="s"><b>{{ today.done }}</b>已完成</div>
          <div class="s"><b>{{ today.overdue }}</b>已逾期</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '@/utils/request'
import { formatTime, toneOf } from '@/utils/format'
import { userStore } from '@/store/user'

const router = useRouter()
const info = computed(() => userStore.info)
const STATUS = { PENDING: '待执行', PENDING_REVIEW: '待复核', RETAINING: '保留中', PENDING_DISPOSE: '待销毁', DISPOSED: '已销毁', REJECTED: '已驳回', OVERDUE: '已逾期' }

const today = ref({ pending: 0, urgent: 0, review: 0, done: 0, overdue: 0, monthDone: 0, weekDone: 0, avgWeight: 0, todo: [] })
const rows = ref([])
const total = ref(0)
const q = reactive({ status: '', meal: '', keyword: '', page: 1, size: 10 })
const hotSlots = ref([])

const greet = computed(() => {
  const h = new Date().getHours()
  return h < 11 ? '上午好' : h < 14 ? '中午好' : h < 18 ? '下午好' : '晚上好'
})
const urgentText = computed(() => (today.value.todo || []).map((t) => t.foodName).slice(0, 3).join('、'))
const pages = computed(() => {
  const n = Math.max(1, Math.ceil(total.value / q.size))
  return Array.from({ length: Math.min(n, 6) }, (_, i) => i + 1)
})
const isUrgent = (r) => r.urgent || r.status === 'OVERDUE'

function dishOf(r) {
  const i = (Number(r.id) * 7) % 12 + 1
  return `/uploads/seed/dish/${String(i).padStart(2, '0')}.jpg`
}

async function loadToday() {
  today.value = await http.get('/api/task/today')
}

async function load() {
  const res = await http.get('/api/task/page', { params: { ...q } })
  rows.value = res.records
  total.value = res.total
}

function search() {
  q.page = 1
  load()
}
function reset() {
  q.status = ''
  q.meal = ''
  q.keyword = ''
  search()
}
function goto(p) {
  q.page = p
  load()
}
function page(d) {
  const n = Math.max(1, Math.ceil(total.value / q.size))
  const p = Math.min(n, Math.max(1, q.page + d))
  if (p !== q.page) goto(p)
}

function goRegister(task) {
  router.push({ path: '/samples/create', query: { taskId: task.id, food: task.foodName } })
}

onMounted(async () => {
  await Promise.all([loadToday(), load()])
  try {
    const res = await http.get('/api/base/cabinets')
    const used = []
    Object.values(res.cabinets || {}).forEach((list) => list.forEach((c) => c.occupied && used.push(c)))
    const map = new Map()
    used.forEach((c) => map.set(c.code, (map.get(c.code) || 0) + 1))
    hotSlots.value = [...map.entries()]
      .sort((a, b) => b[1] - a[1])
      .slice(0, 3)
      .map(([code, times]) => ({ code, times, desc: '冷藏柜 ' + code.slice(0, 1) }))
  } catch (e) {
    hotSlots.value = []
  }
})
</script>
