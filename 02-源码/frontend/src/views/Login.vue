<template>
  <div class="login-wrap">
    <div class="login-left">
      <img class="bg" src="/login-bg.jpg" alt="" />
      <div class="inner">
        <h1>食品安全留样<br />与检测管理系统</h1>
        <div class="rule"></div>
        <p class="sub">
          覆盖食堂菜品留样、入柜复核、抽检登记、不合格追溯与到期销毁全流程，
          把「留样覆盖率与到期处置校验」做成可解释、可修正的量化指标。
        </p>
        <ul>
          <li>按批次与分类规则自动生成当日留样任务</li>
          <li>三项硬校验：重量、密封、仓位，缺一不可入柜</li>
          <li>检测不合格自动生成事件并锁定留样，禁止销毁</li>
          <li>双人监督销毁，全链路留痕可回溯</li>
        </ul>
      </div>
    </div>

    <div class="login-right">
      <div class="login-form">
        <h2>账号登录</h2>
        <p class="tip">请使用后勤保障处统一账号登录，登录失败 5 次将锁定 10 分钟</p>
        <div class="field">
          <label>用户名</label>
          <input v-model="form.username" class="inp" placeholder="请输入用户名" @keyup.enter="submit" />
        </div>
        <div class="field">
          <label>密码</label>
          <input v-model="form.password" type="password" class="inp" placeholder="请输入密码" @keyup.enter="submit" />
        </div>
        <div class="row">
          <el-checkbox v-model="remember">记住账号</el-checkbox>
          <a @click="tip">忘记密码？</a>
        </div>
        <button class="btn btn-primary" :disabled="loading" @click="submit">
          {{ loading ? '登录中…' : '登 录' }}
        </button>

        <div class="roles">
          <div v-for="r in accounts" :key="r.username" class="role-chip" @click="fill(r)">
            <b>{{ r.role }}</b>{{ r.username }} / {{ r.password }}
          </div>
        </div>
        <div class="login-foot">© 2026 后勤保障处 · 食品安全科　技术支持：信息岗</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/store/user'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const remember = ref(true)
const form = reactive({ username: 'zhuguan01', password: '123456' })

const accounts = [
  { role: '留样员', username: 'liuyang01', password: '123456' },
  { role: '检测员', username: 'jiance01', password: '123456' },
  { role: '食品安全主管', username: 'zhuguan01', password: '123456' },
  { role: '系统管理员', username: 'admin', password: 'admin123' }
]

function fill(r) {
  form.username = r.username
  form.password = r.password
}

function tip() {
  ElMessage.info('请联系系统管理员重置密码：admin@canteen.edu.cn')
}

async function submit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名与密码')
    return
  }
  loading.value = true
  try {
    await login(form.username.trim(), form.password)
    ElMessage.success('登录成功')
    const target = route.query.redirect || '/dashboard'
    router.replace(target)
  } catch (e) {
    /* 错误提示已由拦截器统一处理 */
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-left .bg {
  width: 100%;
  height: 100%;
}
</style>
