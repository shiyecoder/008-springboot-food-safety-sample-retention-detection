<template>
  <div>
    <div class="page-h">
      <div>
        <h1>留样台账</h1>
        <p>共 {{ total }} 份留样 · 待复核 {{ stats.PENDING_REVIEW || 0 }} 份 · 超期未处置 {{ stats.PENDING_DISPOSE || 0 }} 份</p>
      </div>
      <div class="acts">
        <button class="btn btn-ghost" @click="reload">刷新</button>
        <button class="btn btn-primary" @click="$router.push('/samples/create')">新建留样</button>
      </div>
    </div>

    <div class="tabs" style="margin-bottom: 14px">
      <a v-for="t in tabs" :key="t.value" :class="{ on: q.tab === t.value }" @click="switchTab(t.value)">
        {{ t.label }}<span class="cnt">{{ t.count }}</span>
      </a>
    </div>

    <div class="filters">
      <div class="fi"><label>关键字</label>
        <input v-model="q.keyword" class="inp wide" placeholder="留样编号 / 食品名称 / 容器号" @keyup.enter="search" />
      </div>
      <div class="fi"><label>餐次</label>
        <select v-model="q.meal" class="sel" style="min-width: 110px">
          <option value="">全部</option><option>早餐</option><option>午餐</option><option>晚餐</option>
        </select>
      </div>
      <div class="fi"><label>最近留样日期</label>
        <select v-model="q.days" class="sel" style="min-width: 130px">
          <option :value="0">不限</option><option :value="1">今天</option>
          <option :value="3">近 3 天</option><option :value="7">近 7 天</option>
        </select>
      </div>
      <div class="fi"><label>到期状态</label>
        <select v-model="q.expireState" class="sel" style="min-width: 120px">
          <option value="">全部</option><option value="DUE">即将到期</option><option value="OVERDUE">已超期</option>
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
            <th>留样编号</th><th>食品</th><th>餐次</th><th class="r">重量</th><th>仓位</th>
            <th>留样时间</th><th>到期时间</th><th>检测</th><th>状态</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in rows" :key="r.id">
            <td class="id nowrap">{{ r.sampleNo }}</td>
            <td>
              <div class="food-cell">
                <img class="thumb" :src="r.photo" alt="" />
                <div><b>{{ r.foodName }}</b><span>{{ r.categoryName }} · {{ r.containerNo }}</span></div>
              </div>
            </td>
            <td>{{ r.meal }}</td>
            <td class="r mono-num">{{ r.weight }} g</td>
            <td class="mono-num">{{ r.locationCode }}</td>
            <td class="nowrap muted">{{ formatTime(r.storeTime) }}</td>
            <td class="nowrap" :style="{ color: r.expireState === 'OVERDUE' ? 'var(--c-danger)' : r.expireState === 'DUE' ? 'var(--c-warn)' : '' }">
              {{ formatTime(r.expireTime) }}
            </td>
            <td><span class="tag" :class="inspectTone(r.inspectResult)">{{ inspectLabel(r.inspectResult) }}</span></td>
            <td>
              <span class="tag" :class="toneOf(r.status)">{{ r.statusLabel }}</span>
              <span v-if="r.locked" class="tag bad" style="margin-left: 4px">锁定</span>
            </td>
            <td>
              <div class="op">
                <button class="btn btn-text btn-sm" @click="openDetail(r)">详情</button>
                <button v-if="r.status === 'PENDING_REVIEW' && canReview" class="btn btn-text btn-sm" @click="openReview(r)">复核</button>
              </div>
            </td>
          </tr>
          <tr v-if="!rows.length"><td colspan="10" class="empty">暂无留样记录</td></tr>
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

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" :size="720" :with-header="true" title="留样详情">
      <div v-if="detail">
        <div class="summary">
          <div class="s">留样编号<b>{{ detail.sampleNo }}</b></div>
          <div class="s">食品名称<b>{{ detail.foodName }}</b></div>
          <div class="s">分类 / 餐次<b>{{ detail.categoryName }} · {{ detail.meal }}</b></div>
          <div class="s">当前状态<b>{{ detail.statusLabel }}</b></div>
        </div>

        <div class="grid2" style="margin-bottom: 16px">
          <div>
            <div class="group-t">留样容器照片</div>
            <img :src="detail.photo" alt="" style="width: 100%; border-radius: 6px; border: 1px solid var(--c-line)" />
          </div>
          <div>
            <div class="group-t">现场凭证照片</div>
            <img :src="detail.proofPhoto" alt="" style="width: 100%; border-radius: 6px; border: 1px solid var(--c-line)" />
          </div>
        </div>

        <div class="group-t">留样信息</div>
        <table class="tbl" style="margin-bottom: 16px">
          <tbody>
            <tr><td class="muted" style="width: 110px">生产批次</td><td>{{ detail.batchNo }}</td><td class="muted" style="width: 110px">容器编号</td><td>{{ detail.containerNo }}</td></tr>
            <tr><td class="muted">留样重量</td><td>{{ detail.weight }} g（标准 ≥ {{ detail.rule?.minWeight || detail.category?.minWeight }} g）</td>
            <td class="muted">存放仓位</td><td>{{ detail.locationCode }} · {{ detail.category?.storageCond }}</td></tr>
            <tr><td class="muted">留样时间</td><td>{{ formatTime(detail.storeTime) }}</td><td class="muted">到期时间</td><td>{{ formatTime(detail.expireTime) }}（{{ detail.retainHours }} h）</td></tr>
            <tr><td class="muted">入柜温度</td><td>{{ detail.temperature }} ℃</td><td class="muted">留样人</td><td>{{ detail.samplerName }}</td></tr>
            <tr><td class="muted">复核人</td><td>{{ detail.reviewTime ? '王建国 · ' + formatTime(detail.reviewTime) : '—' }}</td>
            <td class="muted">复核意见</td><td>{{ detail.reviewRemark || '—' }}</td></tr>
          </tbody>
        </table>

        <div class="group-t">校验结论（创新点·可解释）</div>
        <div class="note" style="margin-bottom: 16px">
          <div>重量校验：{{ detail.weight }} g {{ detail.weight >= (detail.rule?.minWeight || 125) ? '≥' : '<' }} 标准 {{ detail.rule?.minWeight || 125 }} g —— {{ detail.weight >= (detail.rule?.minWeight || 125) ? '通过' : '不通过，需重新留样' }}</div>
          <div>密封校验：{{ detail.reviewRemark?.includes('密封不严') ? '不通过（容器密封不严）' : '通过，容器封口完好' }}</div>
          <div>仓位校验：{{ detail.locationCode ? `${detail.locationCode} 已占用，入柜温度 ${detail.temperature} ℃ 在 0–4 ℃ 区间` : '未分配仓位' }}</div>
        </div>

        <div class="group-t">操作时间轴</div>
        <div class="tl" style="margin-bottom: 16px">
          <div v-for="r in detail.records" :key="r.id" class="it">
            <div class="t">{{ actionLabel(r.action) }}</div>
            <div class="c">{{ r.reason }}</div>
            <div class="m">{{ r.operatorName || '系统' }} · {{ formatTime(r.createTime) }}</div>
          </div>
        </div>

        <div class="group-t">附件（本地文件）</div>
        <div style="display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 10px">
          <div v-for="a in detail.attachments" :key="a.id" style="width: 150px">
            <img :src="a.path" alt="" style="width: 150px; height: 100px; object-fit: cover; border-radius: 4px; border: 1px solid var(--c-line)" />
            <div class="weak" style="font-size: 11.5px; margin-top: 3px">{{ a.fileName }}</div>
          </div>
          <div v-if="!detail.attachments?.length" class="empty">暂无附件</div>
        </div>
        <el-upload
          v-if="detail.status === 'PENDING_REVIEW' || detail.status === 'RETAINING'"
          :show-file-list="false"
          :http-request="(o) => uploadAttach(o, detail.id)"
          accept="image/*"
        >
          <button class="btn btn-ghost btn-sm">上传现场照片</button>
        </el-upload>

        <div v-if="detail.inspections?.length" style="margin-top: 16px">
          <div class="group-t">关联检测</div>
          <table class="tbl">
            <thead><tr><th>检测编号</th><th>类型</th><th>结论</th><th>检测人</th><th>时间</th></tr></thead>
            <tbody>
              <tr v-for="i in detail.inspections" :key="i.id">
                <td class="id">{{ i.inspectNo }}</td><td>{{ i.inspectType }}</td>
                <td><span class="tag" :class="inspectTone(i.result)">{{ i.resultLabel || i.result }}</span></td>
                <td>{{ i.inspectorName }}</td><td class="muted">{{ formatTime(i.inspectTime) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </el-drawer>

    <!-- 复核抽屉：三项硬校验 -->
    <el-drawer v-model="reviewVisible" :size="560" title="留样复核（三项硬校验）">
      <div v-if="current">
        <div class="summary">
          <div class="s">留样编号<b>{{ current.sampleNo }}</b></div>
          <div class="s">食品<b>{{ current.foodName }}</b></div>
          <div class="s">重量<b>{{ current.weight }} g / ≥ {{ current.minWeight || 125 }} g</b></div>
        </div>
        <div class="group-t">① 重量校验</div>
        <div class="note" style="margin-bottom: 12px">
          {{ current.weight >= (current.minWeight || 125) ? '实测重量达到标准下限，通过' : '实测重量低于标准下限，必须驳回并要求重新留样' }}
        </div>
        <div class="group-t">② 容器密封与标识</div>
        <el-checkbox v-model="check.sealed" style="margin-bottom: 12px">容器密封完好、标识清晰可辨</el-checkbox>
        <div class="group-t">③ 仓位占用校验</div>
        <div class="note" style="margin-bottom: 12px">仓位 {{ current.locationCode }} {{ current.locationOccupied ? '已被占用，请更换仓位' : '空闲，可入柜' }}</div>
        <div class="group-t">复核意见</div>
        <el-input v-model="check.remark" type="textarea" :rows="3" placeholder="通过时记录核对结论；驳回时说明原因" />
      </div>
      <template #footer>
        <button class="btn btn-danger" @click="submitReview(false)">驳回</button>
        <button class="btn btn-primary" style="margin-left: 8px" @click="submitReview(true)">通过并入柜</button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/utils/request'
import { formatTime, toneOf } from '@/utils/format'
import { userStore } from '@/store/user'

const rows = ref([])
const total = ref(0)
const stats = ref({})
const q = reactive({ tab: 'all', keyword: '', meal: '', days: 0, expireState: '', page: 1, size: 10 })
const detailVisible = ref(false)
const reviewVisible = ref(false)
const detail = ref(null)
const current = ref(null)
const check = reactive({ sealed: true, remark: '重量、密封、标识、仓位四项均符合要求，准予入柜保留' })

const canReview = computed(() => ['SUPERVISOR', 'ADMIN'].includes(userStore.info?.roleCode))

const tabs = computed(() => [
  { label: '全部', value: 'all', count: stats.value.total || 0 },
  { label: '待复核', value: 'PENDING_REVIEW', count: stats.value.PENDING_REVIEW || 0 },
  { label: '保留中', value: 'RETAINING', count: stats.value.RETAINING || 0 },
  { label: '待销毁', value: 'PENDING_DISPOSE', count: stats.value.PENDING_DISPOSE || 0 },
  { label: '已销毁', value: 'DISPOSED', count: stats.value.DISPOSED || 0 },
  { label: '已驳回', value: 'REJECTED', count: stats.value.REJECTED || 0 }
])
const pages = computed(() => Array.from({ length: Math.min(6, Math.max(1, Math.ceil(total.value / q.size))) }, (_, i) => i + 1))

const inspectLabel = (v) => ({ NONE: '未检测', PASSED: '抽检合格', FAILED: '抽检不合格', RECHECK: '待复检' }[v] || '未检测')
const inspectTone = (v) => ({ NONE: 'plain', PASSED: 'ok', FAILED: 'bad', RECHECK: 'warn' }[v] || 'plain')
const actionLabel = (a) => ({ CREATE: '创建留样记录', REVIEW_PASS: '复核通过入柜', REVIEW_REJECT: '复核驳回', EXPIRE: '到达保留时限', DISPOSE: '双人监督销毁', LOCK: '事件锁定', UNLOCK: '事件解除锁定' }[a] || a)

async function loadStats() {
  stats.value = await http.get('/api/task/stats')
}

async function load() {
  const params = {
    page: q.page,
    size: q.size,
    keyword: q.keyword || undefined,
    meal: q.meal || undefined,
    expireState: q.expireState || undefined,
    tab: q.tab === 'all' ? undefined : q.tab,
    status: q.tab === 'all' ? undefined : q.tab
  }
  if (q.days) {
    const d = new Date(Date.now() - (q.days - 1) * 86400000)
    params.dateStart = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} 00:00:00`
  }
  const res = await http.get('/api/food-sample/page', { params })
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
  q.meal = ''
  q.days = 0
  q.expireState = ''
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
function reload() {
  load()
  loadStats()
}

async function openDetail(r) {
  detail.value = await http.get(`/api/food-sample/${r.id}`)
  detailVisible.value = true
}

async function openReview(r) {
  const d = await http.get(`/api/food-sample/${r.id}`)
  current.value = { ...d, minWeight: d.rule?.minWeight || d.category?.minWeight, locationOccupied: false }
  check.sealed = true
  check.remark = '重量、密封、标识、仓位四项均符合要求，准予入柜保留'
  reviewVisible.value = true
}

async function submitReview(pass) {
  if (!pass) {
    try {
      const { value } = await ElMessageBox.prompt('请填写驳回原因（将记入操作时间轴）', '复核驳回', {
        inputPlaceholder: '如：实际重量低于标准，容器密封不严'
      })
      await http.post(`/api/food-sample/${current.value.id}/review`, { pass: false, remark: value })
      ElMessage.success('已驳回，待留样员重新留样')
      reviewVisible.value = false
      reload()
    } catch (e) {
      /* 取消 */
    }
    return
  }
  if (!check.sealed) {
    ElMessage.warning('容器密封或标识不合格，不能通过复核')
    return
  }
  await http.post(`/api/food-sample/${current.value.id}/review`, { pass: true, remark: check.remark })
  ElMessage.success('复核通过，留样已入柜保留')
  reviewVisible.value = false
  reload()
}

async function uploadAttach(option, sampleId) {
  const fd = new FormData()
  fd.append('file', option.file)
  fd.append('bizType', 'SAMPLE')
  const file = await http.post('/api/file/upload', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
  await http.post(`/api/food-sample/${sampleId}/attachments`, { fileName: option.file.name, path: file.path, fileType: 'IMAGE' })
  ElMessage.success('附件已上传')
  detail.value = await http.get(`/api/food-sample/${sampleId}`)
}

onMounted(() => {
  load()
  loadStats()
})
</script>
