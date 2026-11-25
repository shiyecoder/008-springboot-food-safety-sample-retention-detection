<template>
  <div>
    <div class="page-h">
      <div>
        <h1>个人中心</h1>
        <p>维护个人资料与登录密码 · 头像与顶栏实时同步</p>
      </div>
    </div>

    <div class="grid2" style="grid-template-columns: 340px 1fr; align-items: start">
      <div class="card" style="text-align: center">
        <div class="av" style="width: 96px; height: 96px; margin: 8px auto 12px; font-size: 26px">
          <img v-if="info?.avatar" :src="info.avatar" alt="" />
        </div>
        <h3 style="font-size: 16px; font-weight: 600">{{ info?.realName }}</h3>
        <div class="muted" style="font-size: 12.5px; margin-top: 2px">{{ info?.roleName }} · {{ info?.orgName }}</div>
        <div style="margin-top: 14px">
          <el-upload :show-file-list="false" :http-request="uploadAvatar" accept="image/*">
            <button class="btn btn-ghost btn-sm">更换头像</button>
          </el-upload>
        </div>
        <table class="tbl" style="margin-top: 18px; text-align: left">
          <tbody>
            <tr><td class="muted" style="width: 90px">账号</td><td class="mono-num">{{ info?.username }}</td></tr>
            <tr><td class="muted">数据范围</td><td>{{ scopeLabel }}</td></tr>
            <tr><td class="muted">手机号</td><td class="mono-num">{{ info?.phone }}</td></tr>
            <tr><td class="muted">邮箱</td><td>{{ info?.email }}</td></tr>
            <tr><td class="muted">最近登录</td><td class="muted">{{ formatTime(info?.lastLoginTime) }}</td></tr>
          </tbody>
        </table>
      </div>

      <div class="card">
        <div class="group-t">基本资料</div>
        <div class="form-row"><label>姓名</label><div class="ctrl"><el-input v-model="profile.realName" style="max-width: 320px" /></div></div>
        <div class="form-row"><label>手机号</label><div class="ctrl"><el-input v-model="profile.phone" style="max-width: 320px" /></div></div>
        <div class="form-row"><label>邮箱</label><div class="ctrl"><el-input v-model="profile.email" style="max-width: 320px" /></div></div>
        <div style="display: flex; justify-content: flex-end">
          <button class="btn btn-primary" @click="saveProfile">保存资料</button>
        </div>

        <div class="group-t" style="margin-top: 24px">修改密码</div>
        <div class="form-row"><label>当前密码</label><div class="ctrl"><el-input v-model="pwd.oldPassword" type="password" style="max-width: 320px" /></div></div>
        <div class="form-row"><label>新密码</label><div class="ctrl"><el-input v-model="pwd.newPassword" type="password" style="max-width: 320px" placeholder="不少于 6 位" /></div></div>
        <div class="form-row"><label>确认新密码</label><div class="ctrl"><el-input v-model="pwd.confirm" type="password" style="max-width: 320px" /></div></div>
        <div style="display: flex; justify-content: flex-end">
          <button class="btn btn-primary" @click="savePassword">更新密码</button>
        </div>

        <div class="note" style="margin-top: 20px">
          演示账号统一初始密码为 123456；连续登录失败 5 次将锁定账号 10 分钟，可由系统管理员在「系统管理 - 用户管理」中解锁。
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/utils/request'
import { formatTime } from '@/utils/format'
import { loadProfile, userStore } from '@/store/user'

const info = computed(() => userStore.info)
const scopeLabel = computed(() => ({ ALL: '全部数据', DEPT: '本部门数据', SELF: '仅本人数据' }[info.value?.dataScope] || '—'))
const profile = reactive({ realName: '', phone: '', email: '', avatar: '' })
const pwd = reactive({ oldPassword: '', newPassword: '', confirm: '' })

async function saveProfile() {
  await http.put('/api/profile', { ...profile })
  await loadProfile()
  ElMessage.success('资料已保存')
}

async function savePassword() {
  if (!pwd.oldPassword || !pwd.newPassword) {
    ElMessage.warning('请填写当前密码与新密码')
    return
  }
  if (pwd.newPassword !== pwd.confirm) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  await http.put('/api/profile/password', { oldPassword: pwd.oldPassword, newPassword: pwd.newPassword })
  ElMessage.success('密码已更新')
  pwd.oldPassword = ''
  pwd.newPassword = ''
  pwd.confirm = ''
}

async function uploadAvatar(option) {
  const fd = new FormData()
  fd.append('file', option.file)
  fd.append('bizType', 'AVATAR')
  const res = await http.post('/api/file/upload', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
  profile.avatar = res.path
  await http.put('/api/profile', { ...profile })
  await loadProfile()
  ElMessage.success('头像已更新')
}

onMounted(async () => {
  if (!userStore.info) await loadProfile()
  Object.assign(profile, {
    realName: userStore.info?.realName || '',
    phone: userStore.info?.phone || '',
    email: userStore.info?.email || '',
    avatar: userStore.info?.avatar || ''
  })
})
</script>
