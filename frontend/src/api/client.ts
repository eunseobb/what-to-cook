import createClient from 'openapi-fetch'
import type { paths } from './schema'

// 쿠키에 의존하지 않는 토큰 방식. 앱(Capacitor) 웹뷰에서도 같은 방식으로 동작한다.
const TOKEN_KEY = 'accessToken'

export const tokenStorage = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

// 앱으로 전환하면 웹뷰 origin이 API 서버와 달라지므로 API 주소는 환경변수로 받은 절대 주소를 쓴다.
export const api = createClient<paths>({
  baseUrl: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
})

api.use({
  onRequest({ request }) {
    const token = tokenStorage.get()
    if (token) request.headers.set('Authorization', `Bearer ${token}`)
    return request
  },
})
