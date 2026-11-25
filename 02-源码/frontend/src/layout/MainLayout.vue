<template>
  <div class="shell">
    <aside class="side">
      <div class="logo"><i></i><span>食品安全留样</span></div>
      <nav>
        <template v-for="g in userStore.menus" :key="g.id">
          <div v-if="g.children && g.children.length" class="grp">{{ g.name }}</div>
          <router-link
            v-for="m in g.children || []"
            :key="m.id"
            :to="m.path"
            :class="{ on: activePath === m.path }"
          >
            <svg width="15" height="15" style="flex: 0 0 15px"><use :href="`/icons.svg#${m.icon || 'i-dot'}`" /></svg>
            <span>{{ m.name }}</span>
          </router-link>
        </template>
        <div class="grp">账户</div>
        <router-link to="/profile" :class="{ on: activePath === '/profile' }">
          <svg width="15" height="15" style="flex: 0 0 15px"><use href="/icons.svg#i-user" /></svg>
          <span>个人中心</span>
        </router-link>
      </nav>
      <div class="collapse" @click="collapsed = !collapsed">{{ collapsed ? '» 展开菜单' : '« 收起菜单' }}</div>
    </aside>

    <div class="main">
      <header class="top">
        <span class="muted">{{ userStore.info?.roleName }}工作台</span>
        <span class="weak">/</span>
        <span style="color: var(--c-text)">{{ route.meta.title }}</span>
        <div class="right">
          <el-popover placement="bottom-end" :width="320" trigger="click" @show="loadMessages">
            <template #reference>
              <span class="bell">
                <svg width="17" height="17"><use href="/icons.svg#i-bell" /></svg>
                <em v-if="userStore.unread">{{ userStore.unread }}</em>
              </span>
            </template>
            <div class="msg-box">
              <div class="msg-h">
                <b>待办提醒</b>
                <span class="btn-text" style="cursor: pointer" @click="readAll">全部已读</span>
              </div>
              <div v-if="!messages.length" class="empty">暂无提醒</div>
              <div v-for="m in messages" :key="m.id" class="msg-it">
                <div class="t">{{ m.title }}<span class="weak"> · {{ formatTime(m.createTime) }}</span></div>
                <div class="c">{{ m.content }}</div>
              </div>
            </div>
          </el-popover>
          <span class="muted">{{ userStore.info?.orgName }}</span>
          <el-dropdown @command="onCommand">
            <span class="user">
              <span class="av"><img v-if="userStore.info?.avatar" :src="userStore.info.avatar" alt="" /></span>
              {{ userStore.info?.realName }} ▾
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      <div class="content">
        <router-view v-slot="{ Component }">
          <component :is="Component" />
        </router-view>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import http from '@/utils/request'
import { formatTime } from '@/utils/format'
import { logout, userStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const messages = ref([])

const activePath = computed(() => route.meta.active || route.path)

async function loadMessages() {
  try {
    messages.value = await http.get('/api/messages')
  } catch (e) {
    messages.value = []
  }
}

async function readAll() {
  await http.post('/api/messages/read-all')
  userStore.unread = 0
  messages.value = messages.value.map((m) => ({ ...m, isRead: 1 }))
}

function onCommand(cmd) {
  if (cmd === 'logout') {
    logout()
    router.push('/login')
  } else if (cmd === 'profile') {
    router.push('/profile')
  }
}
</script>

<style scoped>
.side.collapsed {
  width: 64px;
  flex: 0 0 64px;
}
.msg-h {
  display: flex;
  align-items: center;
  padding: 4px 2px 10px;
  border-bottom: 1px solid var(--c-line);
}
.msg-h .btn-text {
  margin-left: auto;
}
.msg-it {
  padding: 10px 2px;
  border-bottom: 1px dashed #f0e7e3;
}
.msg-it:last-child {
  border-bottom: 0;
}
.msg-it .t {
  font-size: 12.5px;
  font-weight: 600;
}
.msg-it .c {
  font-size: 12px;
  color: var(--c-text-2);
  line-height: 18px;
}
</style>
