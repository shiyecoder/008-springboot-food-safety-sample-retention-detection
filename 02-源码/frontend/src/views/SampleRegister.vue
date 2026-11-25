<template>
  <div>
    <div class="page-h">
      <div>
        <h1>留样登记</h1>
        <p>按任务完成称重、分装、贴标与入柜，提交后进入待复核，由主管核对三项硬校验</p>
      </div>
      <div class="acts">
        <button class="btn btn-ghost" @click="$router.back()">返回</button>
      </div>
    </div>

    <div class="grid2" style="grid-template-columns: 1fr 340px; align-items: start">
      <div class="card">
        <div class="group-t">任务与食品</div>
        <div class="form-row">
          <label><span class="req">*</span>来源任务</label>
          <div class="ctrl">
            <el-select v-model="form.taskId" filterable placeholder="选择待执行任务（可留空手工登记）" style="width: 100%; max-width: 420px" @change="onTask">
              <el-option v-for="t in tasks" :key="t.id" :label="`${t.taskNo} · ${t.foodName}（≥ ${t.minWeight}g）`" :value="t.id" />
            </el-select>
            <div class="hint">选择任务后自动带出食品名称、分类、餐次与标准重量</div>
          </div>
        </div>
        <div class="form-row">
          <label><span class="req">*</span>食品名称</label>
          <div class="ctrl"><input v-model="form.foodName" class="inp" placeholder="如 红烧狮子头" /></div>
        </div>
        <div class="form-row">
          <label><span class="req">*</span>食品分类</label>
          <div class="ctrl">
            <select v-model.number="form.categoryId" class="sel" @change="onCategory">
              <option v-for="c in options.categories" :key="c.id" :value="c.id">{{ c.name }}（标准 ≥ {{ c.minWeight }}g / 保留 {{ c.retainHours }}h）</option>
            </select>
          </div>
        </div>
        <div class="form-row">
          <label><span class="req">*</span>餐次</label>
          <div class="ctrl">
            <select v-model="form.meal" class="sel" style="max-width: 160px">
              <option v-for="m in options.meals" :key="m">{{ m }}</option>
            </select>
          </div>
        </div>
        <div class="form-row">
          <label><span class="req">*</span>生产批次</label>
          <div class="ctrl">
            <input v-model="form.batchNo" class="inp" placeholder="如 PC-20261031-L01" />
          </div>
        </div>

        <div class="group-t" style="margin-top: 18px">称重与入柜</div>
        <div class="form-row">
          <label><span class="req">*</span>留样重量(g)</label>
          <div class="ctrl">
            <el-input-number v-model="form.weight" :min="0" :precision="1" :step="5" style="width: 180px" />
            <div class="hint" :style="{ color: weightOk ? 'var(--c-success)' : 'var(--c-danger)' }">
              {{ weightOk ? `达到标准（≥ ${minWeight} g）` : `低于标准 ${minWeight} g，提交后将被主管驳回` }}
            </div>
          </div>
        </div>
        <div class="form-row">
          <label>容器编号</label>
          <div class="ctrl"><input v-model="form.containerNo" class="inp" placeholder="如 BX-A-1021" /></div>
        </div>
        <div class="form-row">
          <label><span class="req">*</span>存放仓位</label>
          <div class="ctrl">
            <el-select v-model="form.locationId" filterable placeholder="选择空闲仓位" style="width: 100%; max-width: 420px">
              <el-option v-for="l in freeLocations" :key="l.id" :label="`${l.code} · ${l.locationDesc || ''}`" :value="l.id" />
            </el-select>
          </div>
        </div>
        <div class="form-row">
          <label>入柜温度(℃)</label>
          <div class="ctrl"><el-input-number v-model="form.temperature" :min="-5" :max="10" :precision="1" :step="0.1" style="width: 150px" /></div>
        </div>
        <div class="form-row">
          <label>留样时间</label>
          <div class="ctrl">
            <el-date-picker v-model="form.storeTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="默认当前时间" style="width: 220px" />
          </div>
        </div>

        <div class="group-t" style="margin-top: 18px">现场照片（本地文件）</div>
        <div class="grid2" style="gap: 12px">
          <div>
            <div class="hint" style="margin-bottom: 6px">留样容器照片</div>
            <div class="uploader" @click="pick('photo')">
              <img v-if="form.photo" :src="form.photo" alt="" />
              <span v-else>点击上传</span>
            </div>
          </div>
          <div>
            <div class="hint" style="margin-bottom: 6px">现场凭证照片</div>
            <div class="uploader" @click="pick('proofPhoto')">
              <img v-if="form.proofPhoto" :src="form.proofPhoto" alt="" />
              <span v-else>点击上传</span>
            </div>
          </div>
        </div>
        <input ref="fileInput" type="file" accept="image/*" style="display: none" @change="onFile" />

        <div class="form-row" style="margin-top: 16px">
          <label>备注</label>
          <div class="ctrl"><el-input v-model="form.remark" type="textarea" :rows="2" placeholder="如出餐温度、分装方式等" /></div>
        </div>

        <div style="display: flex; gap: 8px; justify-content: flex-end; margin-top: 8px">
          <button class="btn btn-ghost" @click="$router.push('/samples')">取消</button>
          <button class="btn btn-primary" :disabled="saving" @click="submit">{{ saving ? '提交中…' : '提交留样' }}</button>
        </div>
      </div>

      <div class="card">
        <div class="card-h"><h3>提交前校验</h3></div>
        <div class="factor"><span class="n">重量校验</span><div class="bar"><i :style="{ width: barWidth, background: weightOk ? 'var(--c-teal)' : 'var(--c-danger)' }"></i></div><span class="sc">{{ form.weight || 0 }}g</span></div>
        <div class="factor"><span class="n">标准下限</span><span class="w">≥</span><span class="sc">{{ minWeight }}g</span></div>
        <div class="factor"><span class="n">分类规则</span><div style="flex: 1; text-align: right" class="muted">{{ currentCategory?.storageCond }} · {{ currentCategory?.retainHours }}h</div></div>
        <div class="factor"><span class="n">仓位</span><div style="flex: 1; text-align: right" class="muted">{{ selectedLocation?.code || '未选择' }}</div></div>
        <div class="note" style="margin-top: 12px">
          提交后状态为「待复核」，主管需核对重量、密封与仓位三项硬校验才会入柜保留；任一不通过则驳回并计入完成率。
        </div>
        <div class="card-h" style="margin-top: 18px"><h3>可用仓位统计</h3></div>
        <div class="stat-strip" style="flex-wrap: wrap; gap: 12px">
          <div class="s"><b>{{ freeLocations.length }}</b>空闲仓位</div>
          <div class="s"><b>{{ options.locations?.length || 0 }}</b>仓位总数</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/utils/request'

const route = useRoute()
const router = useRouter()
const options = ref({ categories: [], locations: [], meals: ['早餐', '午餐', '晚餐'], batches: [] })
const tasks = ref([])
const saving = ref(false)
const fileInput = ref(null)
const uploadField = ref('photo')

const form = reactive({
  taskId: null, foodName: '', categoryId: null, meal: '午餐', batchNo: '', weight: 130,
  containerNo: '', locationId: null, temperature: 2.5, storeTime: '', photo: '', proofPhoto: '', remark: ''
})

const currentCategory = computed(() => options.value.categories.find((c) => c.id === form.categoryId))
const minWeight = computed(() => currentCategory.value?.minWeight || 125)
const weightOk = computed(() => Number(form.weight) >= Number(minWeight.value))
const barWidth = computed(() => `${Math.min(100, Math.round((Number(form.weight || 0) / Math.max(minWeight.value, 1)) * 100))}%`)
const selectedLocation = computed(() => options.value.locations.find((l) => l.id === form.locationId))
const freeLocations = computed(() => (options.value.locations || []).filter((l) => !l.occupied))

function onCategory() {
  if (!form.batchNo && options.value.batches?.length) form.batchNo = options.value.batches[0].batchNo
}

function onTask(id) {
  const t = tasks.value.find((x) => x.id === id)
  if (!t) return
  form.foodName = t.foodName
  form.categoryId = t.categoryId
  form.meal = t.meal
  form.batchNo = t.batchNo
  form.weight = Number(t.minWeight)
}

function pick(field) {
  uploadField.value = field
  fileInput.value.click()
}

async function onFile(e) {
  const file = e.target.files?.[0]
  if (!file) return
  const fd = new FormData()
  fd.append('file', file)
  fd.append('bizType', 'SAMPLE')
  const res = await http.post('/api/file/upload', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
  form[uploadField.value] = res.path
  ElMessage.success('图片已上传到本地 uploads 目录')
  e.target.value = ''
}

async function submit() {
  if (!form.foodName || !form.categoryId || !form.locationId) {
    ElMessage.warning('食品名称、分类与存放仓位为必填')
    return
  }
  if (!form.photo || !form.proofPhoto) {
    ElMessage.warning('请上传留样容器照片与现场凭证照片')
    return
  }
  saving.value = true
  try {
    await http.post('/api/food-sample', { ...form })
    ElMessage.success('留样已提交，等待主管复核')
    router.push('/samples')
  } catch (e) {
    /* handled */
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  const [opt, taskPage, cabinet] = await Promise.all([
    http.get('/api/base/options'),
    http.get('/api/task/page', { params: { page: 1, size: 50, status: 'PENDING' } }),
    http.get('/api/base/cabinets')
  ])
  options.value = opt
  tasks.value = taskPage.records || []
  const occupiedIds = new Set()
  Object.values(cabinet.cabinets || {}).forEach((list) => list.forEach((c) => c.occupied && occupiedIds.add(c.id)))
  options.value.locations = (opt.locations || []).map((l) => ({ ...l, occupied: occupiedIds.has(l.id) }))
  if (opt.categories?.length) form.categoryId = opt.categories[0].id
  if (route.query.taskId) {
    form.taskId = Number(route.query.taskId)
    onTask(form.taskId)
  } else if (route.query.food) {
    form.foodName = route.query.food
  }
})
</script>

<style scoped>
.uploader {
  height: 150px;
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
}
.uploader:hover {
  border-color: var(--c-accent);
  color: var(--c-accent);
}
.uploader img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
