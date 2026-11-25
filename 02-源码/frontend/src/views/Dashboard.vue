<template>
  <div>
    <div class="page-h">
      <div>
        <h1>食品安全看板</h1>
        <p>统计区间 近 30 天 · 数据更新于 {{ data.updated || '—' }}</p>
      </div>
      <div class="acts">
        <select v-model="range" class="sel" style="min-width: 140px" @change="load">
          <option value="30d">近 30 天</option>
          <option value="month">本月</option>
          <option value="quarter">本季度</option>
        </select>
        <button class="btn btn-ghost" @click="exportBoard">导出看板</button>
        <button class="btn btn-primary" @click="$router.push('/samples/create')">新建留样</button>
      </div>
    </div>

    <div class="kpis">
      <div v-for="k in data.kpis" :key="k.label" class="kpi" :class="k.tone">
        <div class="k">{{ k.label }}</div>
        <div class="v">{{ k.value }}<small>{{ k.unit }}</small></div>
        <div class="d">{{ k.desc || '与昨日基本持平' }}</div>
      </div>
    </div>

    <div class="grid3">
      <div class="card">
        <div class="chart-h"><b>近 7 日留样量趋势</b><span>应留 / 实留</span></div>
        <div ref="c1" class="chart"></div>
      </div>
      <div class="card">
        <div class="chart-h"><b>留样状态分布</b><span>共 {{ statusTotal }} 份在管</span></div>
        <div ref="c2" class="chart"></div>
      </div>
      <div class="card">
        <div class="chart-h"><b>档口留样完成率</b><span>按供餐档口</span></div>
        <div ref="c3" class="chart"></div>
      </div>
    </div>

    <div class="grid3" style="margin-top: 14px">
      <div class="card">
        <div class="chart-h"><b>重量不合格率趋势</b><span>单位 %</span></div>
        <div ref="c4" class="chart"></div>
      </div>
      <div class="card">
        <div class="chart-h"><b>检测结果分布</b><span>全部检测记录</span></div>
        <div ref="c5" class="chart"></div>
      </div>
      <div class="card">
        <div class="chart-h"><b>不合格事件趋势</b><span>起数 / 月</span></div>
        <div ref="c6" class="chart"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import http from '@/utils/request'

const data = ref({ kpis: [], trend: { days: [], should: [], actual: [] }, statusDist: [], workshopRate: [], weightFail: {}, inspectDist: [], incidentTrend: {} })
const range = ref('30d')
const c1 = ref(); const c2 = ref(); const c3 = ref(); const c4 = ref(); const c5 = ref(); const c6 = ref()
const charts = shallowRef([])

const BRAND = '#5B2533'; const TEAL = '#2F6F6A'; const WARN = '#B0762A'; const DANGER = '#A61B29'; const SUCCESS = '#2D765C'; const LINE = '#E8DFDA'; const TEXT2 = '#7A6E72'

const statusTotal = computed(() => (data.value.statusDist || []).reduce((s, i) => s + i.value, 0))

const baseOpt = {
  grid: { left: 8, right: 8, top: 18, bottom: 4, containLabel: true },
  tooltip: { trigger: 'axis', textStyle: { fontSize: 12 } },
  textStyle: { fontFamily: 'PingFang SC, Microsoft YaHei, sans-serif' }
}

function render() {
  const d = data.value
  const t = d.trend || { days: [], should: [], actual: [] }
  const inst = charts.value
  inst[0]?.setOption({
    ...baseOpt,
    legend: { right: 0, top: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11, color: TEXT2 } },
    xAxis: { type: 'category', data: t.days, axisLine: { lineStyle: { color: LINE } }, axisTick: { show: false }, axisLabel: { color: TEXT2, fontSize: 10 } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#EFE6E1' } }, axisLabel: { color: TEXT2, fontSize: 10 } },
    series: [
      { name: '应留', type: 'line', data: t.should, smooth: true, symbolSize: 5, lineStyle: { width: 2, color: BRAND }, itemStyle: { color: BRAND }, areaStyle: { color: 'rgba(91,37,51,.06)' } },
      { name: '实留', type: 'line', data: t.actual, smooth: true, symbolSize: 5, lineStyle: { width: 2, color: TEAL, type: 'dashed' }, itemStyle: { color: TEAL } }
    ]
  })
  inst[1]?.setOption({
    tooltip: { trigger: 'item', textStyle: { fontSize: 12 } },
    legend: { bottom: 0, itemWidth: 9, itemHeight: 9, textStyle: { fontSize: 10.5, color: TEXT2 } },
    color: [WARN, TEAL, DANGER, BRAND, '#C9B6AE'],
    series: [{
      type: 'pie', radius: ['52%', '72%'], center: ['50%', '44%'], avoidLabelOverlap: true,
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
      label: { show: true, position: 'center', formatter: `${statusTotal.value}\n在管留样`, fontSize: 15, fontWeight: 600, color: '#2A2226', lineHeight: 20 },
      emphasis: { label: { show: true } },
      data: (d.statusDist || []).map((i) => ({ name: i.name, value: i.value }))
    }]
  })
  const wr = (d.workshopRate || []).filter((i) => i.total > 0).slice(0, 6).reverse()
  inst[2]?.setOption({
    grid: { left: 8, right: 40, top: 10, bottom: 4, containLabel: true },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, textStyle: { fontSize: 12 } },
    xAxis: { type: 'value', max: 100, splitLine: { lineStyle: { color: '#EFE6E1' } }, axisLabel: { color: TEXT2, fontSize: 10 } },
    yAxis: { type: 'category', data: wr.map((i) => i.name), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: '#2A2226', fontSize: 11 } },
    series: [{
      type: 'bar', data: wr.map((i) => i.rate), barWidth: 10, itemStyle: { color: BRAND, borderRadius: [2, 2, 2, 2] },
      label: { show: true, position: 'right', formatter: '{c}%', fontSize: 10.5, color: TEXT2 }
    }]
  })
  const wf = d.weightFail || { values: [], months: [] }
  inst[3]?.setOption({
    ...baseOpt,
    xAxis: { type: 'category', data: wf.months, axisLine: { lineStyle: { color: LINE } }, axisTick: { show: false }, axisLabel: { color: TEXT2, fontSize: 10 } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#EFE6E1' } }, axisLabel: { color: TEXT2, fontSize: 10 } },
    series: [{ type: 'line', data: wf.values, smooth: true, symbolSize: 5, lineStyle: { color: DANGER, width: 2 }, itemStyle: { color: DANGER }, areaStyle: { color: 'rgba(166,27,41,.08)' } }]
  })
  const idist = d.inspectDist || []
  inst[4]?.setOption({
    grid: { left: 8, right: 12, top: 18, bottom: 4, containLabel: true },
    tooltip: { trigger: 'axis', textStyle: { fontSize: 12 } },
    xAxis: { type: 'category', data: idist.map((i) => i.name), axisLine: { lineStyle: { color: LINE } }, axisTick: { show: false }, axisLabel: { color: TEXT2, fontSize: 10.5 } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#EFE6E1' } }, axisLabel: { color: TEXT2, fontSize: 10 } },
    series: [{
      type: 'bar', barWidth: 26,
      data: idist.map((i, idx) => ({ value: i.value, itemStyle: { color: [SUCCESS, WARN, DANGER, BRAND][idx % 4], borderRadius: [2, 2, 0, 0] } })),
      label: { show: true, position: 'top', fontSize: 10.5, color: '#2A2226' }
    }]
  })
  const it = d.incidentTrend || { values: [], months: [] }
  inst[5]?.setOption({
    ...baseOpt,
    xAxis: { type: 'category', boundaryGap: false, data: it.months, axisLine: { lineStyle: { color: LINE } }, axisTick: { show: false }, axisLabel: { color: TEXT2, fontSize: 10 } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#EFE6E1' } }, axisLabel: { color: TEXT2, fontSize: 10 } },
    series: [{ type: 'line', data: it.values, smooth: true, symbolSize: 5, lineStyle: { color: BRAND, width: 2 }, itemStyle: { color: BRAND }, areaStyle: { color: 'rgba(91,37,51,.07)' } }]
  })
}

async function load() {
  const res = await http.get('/api/dashboard', { params: { range: range.value } })
  data.value = res
  await nextTick()
  render()
}

function exportBoard() {
  ElMessage.success('看板数据已导出为 CSV（演示环境仅提示）')
}

let ro = null
onMounted(async () => {
  await load()
  charts.value = [c1.value, c2.value, c3.value, c4.value, c5.value, c6.value].map((el) => echarts.init(el))
  render()
  ro = new ResizeObserver(() => charts.value.forEach((c) => c && c.resize()))
  ro.observe(document.body)
})

onBeforeUnmount(() => {
  ro && ro.disconnect()
  charts.value.forEach((c) => c && c.dispose())
})
</script>
