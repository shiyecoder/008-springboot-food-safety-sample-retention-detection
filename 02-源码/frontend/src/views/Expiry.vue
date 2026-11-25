<template>
  <div>
    <div class="page-h">
      <div>
        <h1>到期处置中心</h1>
        <p>创新点：留样覆盖率与到期处置校验 · 规则版本 {{ rule.ruleVersion }} · 超过保留时限未销毁 {{ stats.pendingDispose || 0 }} 份</p>
      </div>
      <div class="acts">
        <button class="btn btn-ghost" @click="$router.push('/rule')">调整校验规则</button>
        <button class="btn btn-primary" :disabled="!current" @click="openDispose(current)">登记销毁</button>
      </div>
    </div>

    <div class="eval-grid" style="margin-bottom: 14px">
      <div class="card">
        <div class="score-box">
          <div class="k muted" style="font-size: 12px">当日校验得分</div>
          <div class="big">{{ evalData.total ?? '—' }}</div>
          <div class="lv" :style="gradeStyle">{{ evalData.grade || '—' }}</div>
          <div class="weak" style="font-size: 11.5px; margin-top: 8px">{{ latest?.evalNo }} · {{ latest?.bizDate }}</div>
          <div style="display: flex; gap: 8px; margin-top: 12px; justify-content: center">
            <button class="btn btn-ghost btn-sm" @click="openAdjust">人工修正</button>
            <button class="btn btn-primary btn-sm" @click="adopt" :disabled="latest?.adopted === 1">采纳结果</button>
          </div>
        </div>
        <div class="group-t" style="margin-top: 16px">输入快照（可追溯）</div>
        <div v-for="(v, k) in evalData.input || {}" :key="k" style="display: flex; font-size: 12px; padding: 5px 0; border-bottom: 1px dashed #f0e7e3">
          <span class="muted">{{ inputLabel[k] || k }}</span>
          <span style="margin-left: auto" class="mono-num">{{ v }}</span>
        </div>
      </div>

      <div class="card">
        <div class="card-h">
          <h3>六因素明细（权重合计 {{ weightSum }}）</h3>
          <span class="more" @click="$router.push('/rule')">查看规则</span>
        </div>
        <div class="factor" v-for="(f, key) in evalData.detail || {}" :key="key">
          <span class="n">{{ factorLabel[key] || key }}</span>
          <div class="bar"><i :style="{ width: barPct(f), background: f.score >= f.weight * 0.8 ? 'var(--c-teal)' : f.score >= f.weight * 0.6 ? 'var(--c-warn)' : 'var(--c-danger)' }"></i></div>
          <span class="w">权重 {{ f.weight }}</span>
          <span class="sc">{{ f.score }}</span>
        </div>

        <div class="grid2" style="margin-top: 14px">
          <div>
            <div class="group-t">未达成项</div>
            <div v-for="(u, i) in evalData.unmet || []" :key="i" class="note" style="margin-bottom: 8px">{{ u }}</div>
            <div v-if="!evalData.unmet?.length" class="empty">全部达成</div>
          </div>
          <div>
            <div class="group-t">整改建议</div>
            <div v-for="(u, i) in evalData.improve || []" :key="i" class="note" style="margin-bottom: 8px; border-left-color: var(--c-teal)">{{ u }}</div>
            <div v-if="!evalData.improve?.length" class="empty">暂无建议</div>
          </div>
        </div>

        <div v-if="evalData.adjusts?.length" style="margin-top: 12px">
          <div class="group-t">人工修正记录（不覆盖原始计算结果）</div>
          <table class="tbl">
            <thead><tr><th>原等级</th><th>修正为</th><th>原因</th><th>操作人</th><th>时间</th></tr></thead>
            <tbody>
              <tr v-for="a in evalData.adjusts" :key="a.id">
                <td>{{ a.beforeGrade }}</td><td><b>{{ a.afterGrade }}</b></td>
                <td class="muted">{{ a.reason }}</td><td>{{ a.operator }}</td><td class="muted">{{ formatTime(a.createTime) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="tbl-wrap">
      <div class="card-h" style="padding: 14px 14px 0">
        <h3>待处置留样（{{ total }}）</h3>
        <span class="muted" style="margin-left: auto; font-size: 12px">按超期时长降序 · 锁定留样不可销毁</span>
      </div>
      <table class="tbl">
        <thead>
          <tr><th>留样编号</th><th>食品</th><th>仓位</th><th class="r">重量</th><th>留样时间</th><th>到期时间</th><th class="r">超期</th><th>状态</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="r in rows" :key="r.id">
            <td class="id nowrap">{{ r.sampleNo }}</td>
            <td><div class="food-cell"><img class="thumb" :src="r.photo" alt="" /><div><b>{{ r.foodName }}</b><span>{{ r.locationCode }}</span></div></div></td>
            <td class="mono-num">{{ r.locationCode }}</td>
            <td class="r mono-num">{{ r.weight }} g</td>
            <td class="nowrap muted">{{ formatTime(r.storeTime) }}</td>
            <td class="nowrap muted">{{ formatTime(r.expireTime) }}</td>
            <td class="r"><span class="tag" :class="r.overdueDays > 3 ? 'bad' : 'warn'">{{ r.overdueDays }} 天</span></td>
            <td>
              <span class="tag" :class="toneOf(r.status)">{{ r.statusLabel }}</span>
              <span v-if="r.locked" class="tag bad" style="margin-left: 4px">锁定</span>
              <span v-else-if="r.disposalStatus" class="tag warn" style="margin-left: 4px">待审批</span>
            </td>
            <td>
              <div class="op">
                <button class="btn btn-text btn-sm" :disabled="!r.canDispose" @click="openDispose(r)">登记销毁</button>
              </div>
            </td>
          </tr>
          <tr v-if="!rows.length"><td colspan="9" class="empty">没有待处置留样</td></tr>
        </tbody>
      </table>
      <div class="pager">
        <span>共 {{ total }} 条 · 每页 {{ size }} 条</span>
        <div class="pgs">
          <span class="pg" @click="goto(Math.max(1, pageNo - 1))">‹</span>
          <span v-for="p in pages" :key="p" class="pg" :class="{ on: p === pageNo }" @click="goto(p)">{{ p }}</span>
          <span class="pg" @click="goto(Math.min(pages.length, pageNo + 1))">›</span>
        </div>
      </div>
    </div>

    <!-- 销毁登记 -->
    <el-drawer v-model="disposeVisible" :size="600" title="销毁登记（双人监督）">
      <div v-if="target">
        <div class="summary">
          <div class="s">留样编号<b>{{ target.sampleNo }}</b></div>
          <div class="s">食品<b>{{ target.foodName }}</b></div>
          <div class="s">超期<b>{{ target.overdueDays }} 天</b></div>
        </div>
        <div class="form-row">
          <label><span class="req">*</span>销毁方式</label>
          <div class="ctrl">
            <select v-model="form.method" class="sel">
              <option>餐厨垃圾统一处理</option><option>高温灭菌后处理</option><option>专业机构回收</option>
            </select>
          </div>
        </div>
        <div class="form-row">
          <label><span class="req">*</span>执行人</label>
          <div class="ctrl"><input v-model="form.executor" class="inp" placeholder="销毁执行人姓名" /></div>
        </div>
        <div class="form-row">
          <label><span class="req">*</span>监督人</label>
          <div class="ctrl">
            <input v-model="form.supervisor" class="inp" placeholder="与执行人必须不同" />
            <div class="hint" v-if="form.supervisor && form.supervisor === form.executor" style="color: var(--c-danger)">执行人与监督人不得为同一人</div>
          </div>
        </div>
        <div class="form-row">
          <label><span class="req">*</span>销毁重量(g)</label>
          <div class="ctrl"><el-input-number v-model="form.weight" :min="0" :precision="1" style="width: 160px" /></div>
        </div>
        <div class="form-row">
          <label>销毁时间</label>
          <div class="ctrl">
            <el-date-picker v-model="form.disposeTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 220px" />
          </div>
        </div>
        <div class="form-row">
          <label><span class="req">*</span>现场照片</label>
          <div class="ctrl">
            <div class="uploader" @click="fileInput.click()">
              <img v-if="form.photo" :src="form.photo" alt="" />
              <span v-else>点击上传销毁现场照片</span>
            </div>
            <input ref="fileInput" type="file" accept="image/*" style="display: none" @change="onFile" />
          </div>
        </div>
        <div class="form-row">
          <label>备注</label>
          <div class="ctrl"><el-input v-model="form.remark" type="textarea" :rows="2" placeholder="如销毁地点、监督情况" /></div>
        </div>
        <div class="note">提交后进入销毁审批，主管审批通过后留样状态变更为「已销毁」，仓位释放。</div>
      </div>
      <template #footer>
        <button class="btn btn-ghost" @click="disposeVisible = false">取消</button>
        <button class="btn btn-primary" style="margin-left: 8px" @click="submitDispose">提交销毁申请</button>
      </template>
    </el-drawer>

    <!-- 人工修正 -->
    <el-dialog v-model="adjustVisible" title="人工修正（记录不覆盖原始分数）" width="460">
      <div class="form-row">
        <label>修正等级</label>
        <div class="ctrl">
          <el-radio-group v-model="adjustGrade">
            <el-radio value="优">优</el-radio><el-radio value="良">良</el-radio>
            <el-radio value="中">中</el-radio><el-radio value="差">差</el-radio>
          </el-radio-group>
        </div>
      </div>
      <div class="form-row">
        <label>修正原因</label>
        <div class="ctrl"><el-input v-model="adjustReason" type="textarea" :rows="3" placeholder="如：已补 3 张销毁照片，现场条件改善" /></div>
      </div>
      <template #footer>
        <button class="btn btn-ghost" @click="adjustVisible = false">取消</button>
        <button class="btn btn-primary" style="margin-left: 8px" @click="submitAdjust">提交修正</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/utils/request'
import { formatTime, toneOf } from '@/utils/format'

const rows = ref([])
const total = ref(0)
const pageNo = ref(1)
const size = 10
const stats = ref({})
const evalData = ref({})
const latest = ref(null)
const rule = ref({})
const current = ref(null)
const target = ref(null)
const disposeVisible = ref(false)
const adjustVisible = ref(false)
const adjustGrade = ref('良')
const adjustReason = ref('')
const fileInput = ref(null)

const form = reactive({ sampleId: null, method: '餐厨垃圾统一处理', executor: '', supervisor: '', weight: 0, disposeTime: '', photo: '', remark: '' })

const factorLabel = { menuBatch: '菜单批次覆盖率', shouldCount: '应留样数量准确率', actualWeight: '实际重量达标率', storeTime: '入柜时间合规率', retainDuration: '保留时长完整率', evidence: '处置凭证完整性' }
const inputLabel = { menuBatch: '菜单批次', shouldCount: '应留样', actualCount: '实际留样', retainOk: '保留完整', avgWeight: '平均重量', lateStore: '延迟入柜', evidenceRate: '凭证完整率' }
const pages = computed(() => Array.from({ length: Math.max(1, Math.ceil(total.value / size)) }, (_, i) => i + 1))
const weightSum = computed(() => Object.values(evalData.value.detail || {}).reduce((a, b) => a + (b.weight || 0), 0))
const gradeStyle = computed(() => {
  const g = evalData.value.grade
  if (g === '优') return { background: '#E8F2ED', color: 'var(--c-success)' }
  if (g === '良') return { background: '#EAF1F0', color: 'var(--c-teal)' }
  if (g === '中') return { background: '#FBF0DF', color: 'var(--c-warn)' }
  return { background: '#F9E7E8', color: 'var(--c-danger)' }
})
const barPct = (f) => `${Math.min(100, Math.round((f.score / Math.max(f.weight, 1)) * 100))}%`

async function loadList() {
  const [res, st] = await Promise.all([
    http.get('/api/disposal/pending', { params: { page: pageNo.value, size } }),
    http.get('/api/disposal/stats')
  ])
  rows.value = res.records
  total.value = res.total
  stats.value = st
  current.value = res.records[0] || null
}

async function loadEval() {
  const [latestRes, ruleRes] = await Promise.all([http.get('/api/eval/latest'), http.get('/api/eval/rule')])
  evalData.value = latestRes
  latest.value = latestRes.score
  rule.value = { ...ruleRes, ruleVersion: latestRes.ruleVersion }
}

function goto(p) {
  pageNo.value = p
  loadList()
}

function openDispose(r) {
  if (!r) return
  target.value = r
  form.sampleId = r.id
  form.executor = ''
  form.supervisor = ''
  form.weight = r.weight
  form.disposeTime = ''
  form.photo = ''
  form.remark = ''
  disposeVisible.value = true
}

async function onFile(e) {
  const file = e.target.files?.[0]
  if (!file) return
  const fd = new FormData()
  fd.append('file', file)
  fd.append('bizType', 'DISPOSAL')
  const res = await http.post('/api/file/upload', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
  form.photo = res.path
  ElMessage.success('现场照片已上传')
  e.target.value = ''
}

async function submitDispose() {
  if (!form.executor || !form.supervisor) {
    ElMessage.warning('执行人与监督人必填')
    return
  }
  if (form.executor === form.supervisor) {
    ElMessage.error('执行人与监督人不得为同一人')
    return
  }
  if (!form.photo) {
    ElMessage.warning('请上传销毁现场照片')
    return
  }
  await http.post('/api/disposal', { ...form })
  ElMessage.success('销毁申请已提交，等待主管审批')
  disposeVisible.value = false
  loadList()
}

function openAdjust() {
  adjustGrade.value = evalData.value.grade || '良'
  adjustReason.value = ''
  adjustVisible.value = true
}

async function submitAdjust() {
  if (!adjustReason.value) {
    ElMessage.warning('请填写修正原因')
    return
  }
  await http.post(`/api/eval/scores/${latest.value.id}/adjust`, { grade: adjustGrade.value, reason: adjustReason.value })
  ElMessage.success('已记录人工修正，原始分数保留')
  adjustVisible.value = false
  loadEval()
}

async function adopt() {
  await http.post(`/api/eval/scores/${latest.value.id}/adopt`)
  ElMessage.success('已采纳本次校验结果')
  loadEval()
}

onMounted(() => {
  loadList()
  loadEval()
})
</script>

<style scoped>
.factor .w {
  width: 66px;
}
.uploader {
  height: 130px;
  border: 1px dashed var(--c-line);
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-text-3);
  font-size: 12.5px;
  cursor: pointer;
  background: #fcf9f7;
  overflow: hidden;
  max-width: 320px;
}
.uploader img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
