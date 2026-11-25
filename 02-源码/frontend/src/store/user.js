import { reactive } from 'vue'
import http, { clearToken, getToken, setToken } from '@/utils/request'

export const userStore = reactive({
  token: getToken(),
  info: null,
  menus: [],
  unread: 0,
  loaded: false
})

export async function login(username, password) {
  const data = await http.post('/api/auth/login', { username, password })
  userStore.token = data.token
  setToken(data.token)
  if (data.user) userStore.info = data.user
  await loadProfile()
  return data
}

export async function loadProfile() {
  const [info, menus] = await Promise.all([http.get('/api/auth/info'), http.get('/api/auth/menus')])
  userStore.info = info
  userStore.menus = menus || []
  userStore.loaded = true
  try {
    userStore.unread = await http.get('/api/messages/unread')
  } catch (e) {
    userStore.unread = 0
  }
  return info
}

export function logout() {
  clearToken()
  userStore.token = ''
  userStore.info = null
  userStore.menus = []
  userStore.loaded = false
}

export const roleName = () => userStore.info?.roleName || ''
export const hasRole = (r) => userStore.info?.roleCode === r || userStore.info?.roleName === r
