import axios from 'axios'

const client = axios.create({ baseURL: '/api' })

// accessToken을 헤더에 주입 — AuthContext에서 setter를 등록해 사용
let _getToken = () => null

export function setTokenGetter(fn) {
  _getToken = fn
}

client.interceptors.request.use((config) => {
  const token = _getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

export default client
