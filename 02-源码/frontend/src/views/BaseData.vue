<template>
  <div>
    <div class="page-h">
      <div>
        <h1>基础数据</h1>
        <p>食品分类、供应商、供餐档口、仓位与生产批次，是留样任务自动生成的依据</p>
      </div>
      <div class="acts">
        <button class="btn btn-primary" @click="openEdit(null)">新增{{ current.label }}</button>
      </div>
    </div>

    <div class="tabs" style="margin-bottom: 14px">
      <a v-for="t in types" :key="t.key" :class="{ on: type === t.key }" @click="switchType(t.key)">
        {{ t.label }}<span class="cnt">{{ t.count }}</span>
      </a>
    </div>

    <div class="filters">
      <div class="fi"><label>关键字</label>
        <input v-model="keyword" class="inp wide" placeholder="按名称 / 编号搜索" @keyup.enter="search" />
      </div>
      <div class="sp">
        <button class="btn btn-ghost" @click="reset">重置</button>
        <button class="btn btn-primary" @click="search">查询</button>
      </div>
    </div>

    <div class="tbl-wrap">
      <table class="tbl">
        <thead>
          <tr><th v-for="c in current.cols" :key="c.key" :class="c.right ? 'r' : ''">{{ c.label }}</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="r in rows" :key="r.id">
            <td v-for="c in current.cols" :key="c.key" :class="[c.right ? 'r' : '', c.mono ? 'mono-num' : '', c.muted ? 'muted' : '']">
              <span v-if="c.key === 'status'" class="tag" :class="r.status === 1 ? 'ok' : 'plain'">{{ r.status === 1 ? '启用' : '停用' }}</span>
              <span v-else-if="c.key === 'occupied'" class="tag" :class="r.occupied ? 'doing' : 'plain'">{{ r.occupied ? '占用' : '空闲' }}</span>
              <span v-else>{{ r[c.key] ?? '—' }}</span>
            </td>
            <td>
              <div class="op">
                <button class="btn btn-text btn-sm" @click="openEdit(r)">编辑</button>
                <button class="btn btn-text btn-sm" @click="remove(r)">删除</button>
              </div>
            </td>
          </tr>
          <tr v-if="!rows.length"><td :colspan="current.cols.length + 1" class="empty">暂无数据</td></tr>
        </tbody>
      </table>
      <div class="pager">
        <span>共 {{ total }} 条 · 每页 {{ size }} 条</span>
        <div class="pgs">
          <span class="pg" @click="goto(Math.max(1, page - 1))">‹</span>
          <span v-for="p in pages" :key="p" class="pg" :class="{ on: p === page }" @click="goto(p)">{{ p }}</span>
          <span class="pg" @click="goto(Math.min(pages.length, page + 1))">›</span>
        </div>
      </div>
    </div>

    <el-dialog v-model="editVisible" :title="(form.id ? '编辑' : '新增') + current.label" width="560">
      <div class="form-row" v-for="f in current.fields" :key="f.key">
        <label>{{ f.label }}</label>
        <div class="ctrl">
          <el-input-number v-if="f.type === 'number'" v-model="form[f.key]" :min="0" style="width: 160px" />
          <el-input v-else-if="f.type === 'text'" v-model="form[f.key]" />
          <select v-else-if="f.type === 'status'" v-model="form[f.key]" class="sel">
            <option :value="1">启用</option><option :value="0">停用</option>
          </select>
          <el-input v-else v-model="form[f.key]" />
        </div>
      </div>
      <template #footer>
        <button class="btn btn-ghost" @click="editVisible = false">取消</button>
        <button class="btn btn-primary" style="margin-left: 8px" @click="save">保存</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/utils/request'

const TYPES = [
  {
    key: 'category', label: '食品分类',
    cols: [
      { key: 'code', label: '分类编码', mono: true }, { key: 'name', label: '分类名称' },
      { key: 'minWeight', label: '最小留样量(g)', right: true, mono: true }, { key: 'storageCond', label: '存放条件', muted: true },
      { key: 'retainHours', label: '保留(h)', right: true, mono: true }, { key: 'meals', label: '餐次', muted: true },
      { key: 'owner', label: '负责人', muted: true }, { key: 'status', label: '状态' }
    ],
    fields: [
      { key: 'code', label: '分类编码' }, { key: 'name', label: '分类名称' },
      { key: 'minWeight', label: '最小留样量(g)', type: 'number' }, { key: 'storageCond', label: '存放条件' },
      { key: 'retainHours', label: '保留时长(h)', type: 'number' }, { key: 'meals', label: '适用餐次' },
      { key: 'owner', label: '负责人' }, { key: 'status', label: '状态', type: 'status' }
    ]
  },
  {
    key: 'supplier', label: '供应商',
    cols: [
      { key: 'code', label: '供应商编码', mono: true }, { key: 'name', label: '供应商名称' },
      { key: 'contact', label: '联系人' }, { key: 'phone', label: '联系电话', muted: true },
      { key: 'licenseNo', label: '许可证号', muted: true }, { key: 'status', label: '状态' }
    ],
    fields: [
      { key: 'code', label: '供应商编码' }, { key: 'name', label: '供应商名称' },
      { key: 'contact', label: '联系人' }, { key: 'phone', label: '联系电话' },
      { key: 'licenseNo', label: '许可证号' }, { key: 'status', label: '状态', type: 'status' }
    ]
  },
  {
    key: 'workshop', label: '供餐档口',
    cols: [
      { key: 'code', label: '档口编码', mono: true }, { key: 'name', label: '档口名称' },
      { key: 'orgName', label: '所属食堂', muted: true }, { key: 'manager', label: '负责人' },
      { key: 'phone', label: '联系电话', muted: true }, { key: 'status', label: '状态' }
    ],
    fields: [
      { key: 'code', label: '档口编码' }, { key: 'name', label: '档口名称' },
      { key: 'orgId', label: '所属食堂 ID', type: 'number' }, { key: 'manager', label: '负责人' },
      { key: 'phone', label: '联系电话' }, { key: 'status', label: '状态', type: 'status' }
    ]
  },
  {
    key: 'location', label: '留样仓位',
    cols: [
      { key: 'code', label: '仓位编码', mono: true }, { key: 'cabinetNo', label: '冷藏柜' },
      { key: 'layerNo', label: '层', right: true, mono: true }, { key: 'slotNo', label: '位', right: true, mono: true },
      { key: 'locationDesc', label: '位置说明', muted: true }, { key: 'occupied', label: '占用' }
    ],
    fields: [
      { key: 'code', label: '仓位编码' }, { key: 'cabinetNo', label: '冷藏柜编号' },
      { key: 'layerNo', label: '层号', type: 'number' }, { key: 'slotNo', label: '位号', type: 'number' },
      { key: 'locationDesc', label: '位置说明' }, { key: 'status', label: '状态', type: 'status' }
    ]
  },
  {
    key: 'batch', label: '生产批次',
    cols: [
      { key: 'batchNo', label: '批次编号', mono: true }, { key: 'foodName', label: '食品名称' },
      { key: 'workshopName', label: '档口', muted: true }, { key: 'produceTime', label: '生产时间', muted: true },
      { key: 'quantity', label: '数量', right: true, mono: true }, { key: 'status', label: '状态' }
    ],
    fields: [
      { key: 'batchNo', label: '批次编号' }, { key: 'foodName', label: '食品名称' },
      { key: 'quantity', label: '数量', type: 'number' }, { key: 'produceTime', label: '生产时间' },
      { key: 'status', label: '状态', type: 'status' }
    ]
  }
]

const types = ref(TYPES.map((t) => ({ key: t.key, label: t.label, count: 0 })))
const type = ref('category')
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const keyword = ref('')
const editVisible = ref(false)
const form = reactive({})

const current = computed(() => TYPES.find((t) => t.key === type.value))
const pages = computed(() => Array.from({ length: Math.max(1, Math.ceil(total.value / size)) }, (_, i) => i + 1))

async function loadCounts() {
  const res = await Promise.all(TYPES.map((t) => http.get(`/api/base/${t.key}/page`, { params: { page: 1, size: 1 } })))
  types.value = TYPES.map((t, i) => ({ key: t.key, label: t.label, count: res[i].total }))
}

async function load() {
  const res = await http.get(`/api/base/${type.value}/page`, {
    params: { page: page.value, size, keyword: keyword.value || undefined }
  })
  rows.value = res.records
  total.value = res.total
}

function switchType(k) {
  type.value = k
  page.value = 1
  keyword.value = ''
  load()
}
function search() {
  page.value = 1
  load()
}
function reset() {
  keyword.value = ''
  search()
}
function goto(p) {
  page.value = p
  load()
}

function openEdit(r) {
  Object.keys(form).forEach((k) => delete form[k])
  if (r) Object.assign(form, r)
  else {
    Object.assign(form, { status: 1 })
    current.value.fields.forEach((f) => {
      if (f.type === 'number') form[f.key] = 0
    })
  }
  editVisible.value = true
}

async function save() {
  if (form.id) await http.put(`/api/base/${type.value}/${form.id}`, { ...form })
  else await http.post(`/api/base/${type.value}`, { ...form })
  ElMessage.success('已保存')
  editVisible.value = false
  load()
  loadCounts()
}

async function remove(r) {
  try {
    await ElMessageBox.confirm(`确认删除「${r.name || r.code || r.batchNo}」？逻辑删除后不再参与任务生成。`, '删除确认', { type: 'warning' })
    await http.delete(`/api/base/${type.value}/${r.id}`)
    ElMessage.success('已删除')
    load()
    loadCounts()
  } catch (e) {
    /* 取消 */
  }
}

onMounted(() => {
  load()
  loadCounts()
})
</script>
