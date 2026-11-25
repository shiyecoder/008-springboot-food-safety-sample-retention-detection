import axios from 'axios'
import { ElMessage } from 'element-plus'

const TOKEN_KEY = 'food_token'

export const getToken = () => localStorage.getItem(TOKEN_KEY) || ''
export const setToken = (t) => localStorage.setItem(TOKEN_KEY, t)
export const clearToken = () => localStorage.removeItem(TOKEN_KEY)

const http = axios.create({ baseURL: '', timeout: 20000 })

http.interceptors.request.use((cfg) => {
  const t = getToken()
  if (t) cfg.headers.Authorization = `Bearer ${t}`
  return cfg
})

http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 200) return body.data
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (err) => {
    const status = err.response?.status
    const msg = err.response?.data?.message || err.message
    if (status === 401) {
      clearToken()
      if (!location.hash.includes('/login')) location.hash = '#/login'
      ElMessage.error('登录已过期，请重新登录')
    } else {
      ElMessage.error(msg || '网络异常')
    }
    return Promise.reject(err)
  }
)

export default http
