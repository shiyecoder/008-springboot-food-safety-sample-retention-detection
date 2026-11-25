<template>
  <div>
    <div class="page-h">
      <div>
        <h1>系统管理</h1>
        <p>用户、角色与操作日志 · 系统内不提供菜单管理入口，菜单随角色固定下发</p>
      </div>
      <div class="acts">
        <button v-if="tab === 'user'" class="btn btn-primary" @click="openUser(null)">新增用户</button>
      </div>
    </div>

    <div class="tabs" style="margin-bottom: 14px">
      <a :class="{ on: tab === 'user' }" @click="tab = 'user'">用户管理<span class="cnt">{{ users.length }}</span></a>
      <a :class="{ on: tab === 'role' }" @click="tab = 'role'">角色与数据范围<span class="cnt">{{ roles.length }}</span></a>
      <a :class="{ on: tab === 'log' }" @click="tab = 'log'">操作日志<span class="cnt">{{ logTotal }}</span></a>
    </div>

    <div v-if="tab === 'user'">
      <div class="filters">
        <div class="fi"><label>关键字</label>
          <input v-model="keyword" class="inp wide" placeholder="用户名 / 姓名 / 手机号" @keyup.enter="loadUsers" />
        </div>
        <div class="sp">
          <button class="btn btn-ghost" @click="keyword = ''; loadUsers()">重置</button>
          <button class="btn btn-primary" @click="loadUsers">查询</button>
        </div>
      </div>
      <div class="tbl-wrap">
        <table class="tbl">
          <thead>
            <tr><th>用户</th><th>账号</th><th>角色</th><th>所属组织</th><th>数据范围</th><th>手机号</th><th>最近登录</th><th>状态</th><th>操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="u in users" :key="u.id">
              <td>
                <div class="food-cell">
                  <span class="av" style="width: 28px; height: 28px"><img v-if="u.avatar" :src="u.avatar" alt="" /></span>
                  <div><b>{{ u.realName }}</b><span>{{ u.email }}</span></div>
                </div>
              </td>
              <td class="mono-num">{{ u.username }}</td>
              <td>{{ u.roleName }}</td>
              <td class="muted">{{ u.orgName }}</td>
              <td class="muted">{{ scopeLabel(u.dataScope) }}</td>
              <td class="mono-num">{{ u.phone }}</td>
              <td class="muted nowrap">{{ formatTime(u.lastLoginTime) }}</td>
              <td>
                <span class="tag" :class="u.status === 1 ? 'ok' : 'plain'">{{ u.status === 1 ? '正常' : '停用' }}</span>
                <span v-if="u.locked" class="tag bad" style="margin-left: 4px">已锁定</span>
              </td>
              <td>
                <div class="op">
                  <button class="btn btn-text btn-sm" @click="openUser(u)">编辑</button>
                  <button v-if="u.locked" class="btn btn-text btn-sm" @click="unlock(u)">解锁</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="tab === 'role'" class="tbl-wrap">
      <table class="tbl">
        <thead><tr><th>角色编码</th><th>角色名称</th><th>数据范围</th><th>职责说明</th><th>用户数</th></tr></thead>
        <tbody>
          <tr v-for="r in roles" :key="r.id">
            <td class="mono-num">{{ r.code }}</td>
            <td><b>{{ r.name }}</b></td>
            <td><span class="tag" :class="r.dataScope === 'ALL' ? 'key' : r.dataScope === 'DEPT' ? 'doing' : 'plain'">{{ scopeLabel(r.dataScope) }}</span></td>
            <td class="muted">{{ r.remark }}</td>
            <td class="mono-num">{{ users.filter((u) => u.roleId === r.id).length }}</td>
          </tr>
        </tbody>
      </table>
      <div class="note" style="margin: 12px">
        数据范围决定列表可见性：全部数据（主管/管理员）、本部门数据（检测员）、仅本人数据（留样员）。
      </div>
    </div>

    <div v-if="tab === 'log'">
      <div class="tbl-wrap">
        <table class="tbl">
          <thead><tr><th>操作时间</th><th>操作人</th><th>动作</th><th>对象</th><th>详情</th><th>结果</th><th>IP</th></tr></thead>
          <tbody>
            <tr v-for="l in logs" :key="l.id">
              <td class="muted nowrap">{{ formatTime(l.createTime, true) }}</td>
              <td>{{ l.userName }}</td>
              <td>{{ l.action }}</td>
              <td class="mono-num">{{ l.target }}</td>
              <td class="muted" style="max-width: 380px">{{ l.detail }}</td>
              <td><span class="tag" :class="l.result === 'SUCCESS' ? 'ok' : 'bad'">{{ l.result === 'SUCCESS' ? '成功' : '失败' }}</span></td>
              <td class="muted mono-num">{{ l.ip }}</td>
            </tr>
            <tr v-if="!logs.length"><td colspan="7" class="empty">暂无日志</td></tr>
          </tbody>
        </table>
        <div class="pager">
          <span>共 {{ logTotal }} 条 · 每页 10 条</span>
          <div class="pgs">
            <span class="pg" @click="logPage(Math.max(1, logNo - 1))">‹</span>
            <span v-for="p in logPages" :key="p" class="pg" :class="{ on: p === logNo }" @click="logPage(p)">{{ p }}</span>
            <span class="pg" @click="logPage(Math.min(logPages.length, logNo + 1))">›</span>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="userVisible" :title="(form.id ? '编辑' : '新增') + '用户'" width="560">
      <div class="form-row"><label>账号</label><div class="ctrl"><el-input v-model="form.username" :disabled="!!form.id" /></div></div>
      <div class="form-row"><label>姓名</label><div class="ctrl"><el-input v-model="form.realName" /></div></div>
      <div class="form-row"><label>密码</label><div class="ctrl"><el-input v-model="form.password" type="password" :placeholder="form.id ? '留空表示不修改' : '默认 123456'" /></div></div>
      <div class="form-row"><label>角色</label>
        <div class="ctrl">
          <select v-model="form.roleId" class="sel">
            <option v-for="r in roles" :key="r.id" :value="r.id">{{ r.name }}</option>
          </select>
        </div>
      </div>
      <div class="form-row"><label>组织</label>
        <div class="ctrl">
          <select v-model="form.orgId" class="sel">
            <option v-for="o in orgs" :key="o.id" :value="o.id">{{ o.name }}</option>
          </select>
        </div>
      </div>
      <div class="form-row"><label>手机号</label><div class="ctrl"><el-input v-model="form.phone" /></div></div>
      <div class="form-row"><label>邮箱</label><div class="ctrl"><el-input v-model="form.email" /></div></div>
      <div class="form-row"><label>状态</label>
        <div class="ctrl">
          <select v-model="form.status" class="sel">
            <option :value="1">正常</option><option :value="0">停用</option>
          </select>
        </div>
      </div>
      <template #footer>
        <button class="btn btn-ghost" @click="userVisible = false">取消</button>
        <button class="btn btn-primary" style="margin-left: 8px" @click="saveUser">保存</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/utils/request'
import { formatTime } from '@/utils/format'

const tab = ref('user')
const users = ref([])
const roles = ref([])
const orgs = ref([])
const logs = ref([])
const logTotal = ref(0)
const logNo = ref(1)
const keyword = ref('')
const userVisible = ref(false)
const form = reactive({ id: null, username: '', realName: '', password: '', roleId: 1, orgId: 1, phone: '', email: '', status: 1, remark: '' })

const logPages = computed(() => Array.from({ length: Math.max(1, Math.ceil(logTotal.value / 10)) }, (_, i) => i + 1))
const scopeLabel = (s) => ({ ALL: '全部数据', DEPT: '本部门', SELF: '仅本人' }[s] || s)

async function loadUsers() {
  const res = await http.get('/api/system/users', { params: { page: 1, size: 50, keyword: keyword.value || undefined } })
  users.value = res.records
}
async function loadRoles() {
  roles.value = await http.get('/api/system/roles')
}
async function loadLogs() {
  const res = await http.get('/api/system/logs', { params: { page: logNo.value, size: 10 } })
  logs.value = res.records
  logTotal.value = res.total
}
function logPage(p) {
  logNo.value = p
  loadLogs()
}

function openUser(u) {
  Object.assign(form, u ? { ...u, password: '' } : { id: null, username: '', realName: '', password: '', roleId: roles.value[0]?.id || 1, orgId: orgs.value[0]?.id || 1, phone: '', email: '', status: 1, remark: '' })
  userVisible.value = true
}

async function saveUser() {
  const payload = { ...form }
  if (form.id) await http.put(`/api/system/users/${form.id}`, payload)
  else await http.post('/api/system/users', payload)
  ElMessage.success('已保存')
  userVisible.value = false
  loadUsers()
}

async function unlock(u) {
  await http.post(`/api/system/users/${u.id}/unlock`)
  ElMessage.success('已解锁')
  loadUsers()
}

watch(tab, (v) => {
  if (v === 'log') loadLogs()
  if (v === 'role') loadRoles()
})

onMounted(async () => {
  await Promise.all([loadUsers(), loadRoles()])
  const opt = await http.get('/api/base/options')
  orgs.value = opt.orgs || []
  loadLogs()
})
</script>
