<template>
  <div>
    <div class="page-h">
      <div>
        <h1>留样与校验规则</h1>
        <p>分类留样标准 + 创新点权重配置 · 当前校验规则 {{ rule.rule?.version }}（生效 {{ formatTime(rule.rule?.effectiveTime, false) }}）</p>
      </div>
      <div class="acts">
        <button class="btn btn-ghost" @click="load">重新加载</button>
      </div>
    </div>

    <div class="tabs" style="margin-bottom: 16px">
      <a :class="{ on: tab === 'weight' }" @click="tab = 'weight'">校验权重与阈值</a>
      <a :class="{ on: tab === 'sample' }" @click="tab = 'sample'">分类留样标准</a>
      <a :class="{ on: tab === 'version' }" @click="tab = 'version'">规则版本</a>
      <a :class="{ on: tab === 'effect' }" @click="tab = 'effect'">效果统计</a>
    </div>

    <div v-if="tab === 'weight'" class="grid2" style="grid-template-columns: 1fr 360px; align-items: start">
      <div class="card">
        <div class="card-h"><h3>六因素权重（合计必须为 100）</h3><span class="more">当前合计 {{ sum }}</span></div>
        <table class="tbl">
          <thead><tr><th>因素</th><th>说明</th><th class="r" style="width: 200px">权重</th></tr></thead>
          <tbody>
            <tr v-for="(label, key) in rule.factors" :key="key">
              <td><b>{{ label }}</b></td>
              <td class="muted">{{ desc[key] }}</td>
              <td class="r">
                <el-input-number v-model="weights[key]" :min="0" :max="100" :step="5" size="small" style="width: 120px" />
              </td>
            </tr>
          </tbody>
        </table>
        <div class="note" style="margin-top: 12px" :style="{ borderLeftColor: sum === 100 ? 'var(--c-success)' : 'var(--c-danger)' }">
          权重合计 {{ sum }} / 100 {{ sum === 100 ? '，可保存' : '，请调整后再保存' }}
        </div>
        <div class="group-t" style="margin-top: 16px">等级阈值</div>
        <div class="form-row">
          <label>优</label><div class="ctrl"><el-input-number v-model="thresholds.excellent" :min="0" :max="100" size="small" /> 分及以上</div>
        </div>
        <div class="form-row">
          <label>良</label><div class="ctrl"><el-input-number v-model="thresholds.good" :min="0" :max="100" size="small" /> 分及以上</div>
        </div>
        <div class="form-row">
          <label>中</label><div class="ctrl"><el-input-number v-model="thresholds.medium" :min="0" :max="100" size="small" /> 分及以上，其余为「差」</div>
        </div>
        <div class="form-row" style="margin-top: 8px">
          <label>变更说明</label>
          <div class="ctrl"><el-input v-model="remark" type="textarea" :rows="2" placeholder="如：提高处置凭证权重，新增入柜时间因素" /></div>
        </div>
        <div style="display: flex; justify-content: flex-end; gap: 8px">
          <button class="btn btn-ghost" @click="load">放弃修改</button>
          <button class="btn btn-primary" :disabled="sum !== 100" @click="saveRule">保存为新版本</button>
        </div>
      </div>

      <div class="card">
        <div class="card-h"><h3>规则说明</h3></div>
        <div class="note" style="margin-bottom: 12px">
          权重决定各因素对总分的影响；每次保存生成新版本号，历史评分仍保留其计算时的权重快照，保证可追溯。
        </div>
        <div class="factor" v-for="(label, key) in rule.factors" :key="key">
          <span class="n">{{ label }}</span>
          <div class="bar"><i :style="{ width: (weights[key] || 0) + '%', background: 'var(--c-teal)' }"></i></div>
          <span class="w">{{ weights[key] }}</span>
        </div>
        <div class="stat-strip" style="margin-top: 12px; flex-wrap: wrap">
          <div class="s"><b>{{ rule.rule?.version }}</b>当前版本</div>
          <div class="s"><b>{{ versions.length }}</b>历史版本</div>
        </div>
      </div>
    </div>

    <div v-if="tab === 'sample'" class="tbl-wrap">
      <table class="tbl">
        <thead><tr><th>分类编号</th><th>分类</th><th>餐次</th><th class="r">最小留样量</th><th>存放条件</th><th class="r">保留时长</th><th>版本</th><th>负责人</th></tr></thead>
        <tbody>
          <tr v-for="r in sampleRules" :key="r.id">
            <td class="id">{{ r.categoryId }}</td>
            <td><b>{{ categoryName(r.categoryId) }}</b></td>
            <td>{{ r.meal }}</td>
            <td class="r mono-num">≥ {{ r.minWeight }} g</td>
            <td class="muted">{{ r.storageCond }}</td>
            <td class="r mono-num">{{ r.retainHours }} h</td>
            <td>{{ r.version }}</td>
            <td class="muted">{{ r.owner || '王建国' }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="tab === 'version'" class="tbl-wrap">
      <table class="tbl">
        <thead><tr><th>版本</th><th>规则名称</th><th>权重</th><th>阈值</th><th>生效时间</th><th>说明</th></tr></thead>
        <tbody>
          <tr v-for="v in versions" :key="v.id">
            <td class="id">{{ v.version }}</td>
            <td>{{ v.name }}</td>
            <td class="muted" style="max-width: 320px">{{ v.weights }}</td>
            <td class="muted">{{ v.thresholds }}</td>
            <td class="muted">{{ formatTime(v.effectiveTime) }}</td>
            <td class="muted">{{ v.remark }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="tab === 'effect'" class="grid2">
      <div class="card">
        <div class="card-h"><h3>校验效果统计</h3></div>
        <div class="stat-strip" style="flex-wrap: wrap; gap: 24px">
          <div class="s"><b>{{ effect.total }}</b>累计校验次数</div>
          <div class="s"><b>{{ effect.adoptRate }}%</b>结果采纳率</div>
          <div class="s"><b>{{ effect.adjustRate }}%</b>人工修正率</div>
        </div>
        <div class="group-t" style="margin-top: 16px">等级分布</div>
        <div class="factor" v-for="(n, g) in effect.gradeDist || {}" :key="g">
          <span class="n">{{ g }}</span>
          <div class="bar"><i :style="{ width: gradeWidth(n), background: g === '优' ? 'var(--c-success)' : g === '良' ? 'var(--c-teal)' : g === '中' ? 'var(--c-warn)' : 'var(--c-danger)' }"></i></div>
          <span class="sc">{{ n }}</span>
        </div>
      </div>
      <div class="card">
        <div class="card-h"><h3>最近校验记录</h3></div>
        <table class="tbl">
          <thead><tr><th>编号</th><th>业务日期</th><th class="r">总分</th><th>等级</th><th>采纳</th></tr></thead>
          <tbody>
            <tr v-for="h in history" :key="h.id">
              <td class="id">{{ h.evalNo }}</td>
              <td class="muted">{{ h.bizDate }}</td>
              <td class="r mono-num">{{ h.totalScore }}</td>
              <td><span class="tag" :class="h.grade === '优' ? 'ok' : h.grade === '良' ? 'doing' : h.grade === '中' ? 'warn' : 'bad'">{{ h.grade }}</span></td>
              <td class="muted">{{ h.adopted ? '已采纳' : '未采纳' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/utils/request'
import { formatTime } from '@/utils/format'

const tab = ref('weight')
const rule = ref({ rule: {}, factors: {}, weights: {}, thresholds: {} })
const weights = reactive({})
const thresholds = reactive({ excellent: 85, good: 70, medium: 55 })
const remark = ref('')
const versions = ref([])
const sampleRules = ref([])
const categories = ref([])
const effect = ref({})
const history = ref([])

const desc = {
  menuBatch: '当日菜单批次是否全覆盖，漏项即扣分',
  shouldCount: '应留样数量与菜单项数量的一致性',
  actualWeight: '实际留样重量是否达到分类标准下限',
  storeTime: '入柜时间是否在出餐后 2 小时内',
  retainDuration: '保留时长是否满足 48/24 小时要求',
  evidence: '销毁照片与监督人签字等处置凭证完整度'
}
const sum = computed(() => Object.values(weights).reduce((a, b) => a + (Number(b) || 0), 0))

const categoryName = (id) => categories.value.find((c) => c.id === id)?.name || `分类 ${id}`
const gradeWidth = (n) => {
  const max = Math.max(...Object.values(effect.value.gradeDist || { a: 1 }).map(Number), 1)
  return `${Math.round((Number(n) / max) * 100)}%`
}

async function load() {
  const [r, v, s, opt, st, his] = await Promise.all([
    http.get('/api/eval/rule'),
    http.get('/api/eval/rule/versions'),
    http.get('/api/base/rule/page', { params: { page: 1, size: 50 } }),
    http.get('/api/base/options'),
    http.get('/api/eval/stats'),
    http.get('/api/eval/history')
  ])
  rule.value = r
  Object.assign(weights, r.weights || {})
  Object.assign(thresholds, r.thresholds || {})
  versions.value = v || []
  sampleRules.value = s.records || []
  categories.value = opt.categories || []
  effect.value = st
  history.value = (his || []).slice(0, 8)
}

async function saveRule() {
  await http.put('/api/eval/rule', {
    weights: { ...weights },
    thresholds: { ...thresholds },
    effectiveTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
    remark: remark.value || '调整校验权重'
  })
  ElMessage.success('已保存为新规则版本')
  remark.value = ''
  load()
}

onMounted(load)
</script>
