<template>
  <div>
    <div class="page-h">
      <div>
        <h1>留样柜位图</h1>
        <p>冷藏柜 A/B/C 共 {{ totalSlots }} 个仓位 · 已占用 {{ usedCount }} 个 · 待销毁 {{ dueCount }} 个 · 锁定 {{ lockCount }} 个</p>
      </div>
      <div class="acts">
        <select v-model="active" class="sel" style="min-width: 150px">
          <option value="ALL">全部冷藏柜</option>
          <option v-for="k in cabinets" :key="k" :value="k">冷藏柜 {{ k }}</option>
        </select>
        <button class="btn btn-ghost" @click="load">刷新柜位</button>
      </div>
    </div>

    <div class="cabinet" v-for="(list, key) in shown" :key="key" style="margin-bottom: 14px">
      <div class="card-h">
        <h3>冷藏柜 {{ key }}（0–4 ℃）</h3>
        <span class="more">{{ list.filter((c) => c.occupied).length }} / {{ list.length }} 已占用</span>
      </div>
      <div class="cab-grid">
        <div
          v-for="c in list"
          :key="c.id"
          class="cab"
          :class="c.locked ? 'lock' : c.occupied ? (c.due ? 'due' : 'used') : 'free'"
          :title="c.occupied ? `${c.code} · ${c.foodName} · 到期 ${formatTime(c.expireTime)}` : `${c.code} 空闲`"
          @click="c.occupied && open(c)"
        >
          <b>{{ c.code }}</b>
          <span v-if="c.occupied">{{ c.foodName }}</span>
          <span v-else>空闲</span>
          <span v-if="c.occupied">{{ formatTime(c.expireTime, false) }}</span>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="legend">
        <span><i style="background: #eaf1f0; border-color: #cfe2df"></i>保留中</span>
        <span><i style="background: #fbf0df; border-color: #eed9b4"></i>即将到期 / 待销毁</span>
        <span><i style="background: #f9e7e8; border-color: #efc9cd"></i>事件锁定，禁止销毁</span>
        <span><i style="background: #f5f0ed"></i>空闲仓位</span>
      </div>
      <div class="note" style="margin-top: 12px">
        柜位图与「留样台账」共用同一份仓位数据；点击已占用柜位可查看留样详情，锁定柜位需先关闭不合格事件才能处置。
      </div>
    </div>

    <el-dialog v-model="visible" title="柜位留样" width="520">
      <div v-if="current">
        <div class="summary">
          <div class="s">仓位<b>{{ current.code }}</b></div>
          <div class="s">食品<b>{{ current.foodName }}</b></div>
          <div class="s">状态<b>{{ current.statusLabel }}</b></div>
        </div>
        <table class="tbl">
          <tbody>
            <tr><td class="muted" style="width: 100px">留样编号</td><td>{{ current.sampleNo || '—' }}</td></tr>
            <tr><td class="muted">入柜时间</td><td>{{ formatTime(current.storeTime) }}</td></tr>
            <tr><td class="muted">到期时间</td><td>{{ formatTime(current.expireTime) }}</td></tr>
          </tbody>
        </table>
      </div>
      <template #footer>
        <button class="btn btn-ghost" @click="visible = false">关闭</button>
        <button class="btn btn-primary" style="margin-left: 8px" @click="$router.push('/samples')">查看台账</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import http from '@/utils/request'
import { formatTime } from '@/utils/format'

const cabinets = ref({})
const active = ref('ALL')
const visible = ref(false)
const current = ref(null)

const shown = computed(() => {
  if (active.value === 'ALL') return cabinets.value
  return cabinets.value[active.value] ? { [active.value]: cabinets.value[active.value] } : {}
})
const all = computed(() => Object.values(cabinets.value).flat())
const totalSlots = computed(() => all.value.length)
const usedCount = computed(() => all.value.filter((c) => c.occupied).length)
const dueCount = computed(() => all.value.filter((c) => c.due).length)
const lockCount = computed(() => all.value.filter((c) => c.locked).length)

async function load() {
  const res = await http.get('/api/base/cabinets')
  cabinets.value = res.cabinets || {}
}

function open(c) {
  current.value = c
  visible.value = true
}

onMounted(load)
</script>
