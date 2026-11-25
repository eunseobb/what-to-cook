import createClient from 'openapi-fetch'
import type { paths } from './schema'

// 쿠키에 의존하지 않는 토큰 방식. 앱(Capacitor) 웹뷰에서도 같은 방식으로 동작한다.
const ACCESS_KEY = 'accessToken'
const REFRESH_KEY = 'refreshToken'

export interface Tokens {
  accessToken: string
  refreshToken: string
}

export const tokenStorage = {
  get: () => localStorage.getItem(ACCESS_KEY),
  getRefresh: () => localStorage.getItem(REFRESH_KEY),
  set: ({ accessToken, refreshToken }: Tokens) => {
    localStorage.setItem(ACCESS_KEY, accessToken)
    localStorage.setItem(REFRESH_KEY, refreshToken)
  },
  clear: () => {
    localStorage.removeItem(ACCESS_KEY)
    localStorage.removeItem(REFRESH_KEY)
  },
}

// 앱으로 전환하면 웹뷰 origin이 API 서버와 달라지므로 API 주소는 환경변수로 받은 절대 주소를 쓴다.
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

// Refresh 토큰은 한 번 쓰면 무효가 되므로, 동시에 401이 여러 개 와도 재발급은 한 번만 한다.
let refreshing: Promise<boolean> | null = null

function refreshTokens(): Promise<boolean> {
  refreshing ??= (async () => {
    const refreshToken = tokenStorage.getRefresh()
    if (!refreshToken) return false
    // 네트워크 오류는 그대로 던진다. 일시적 장애로 로그아웃시키지 않기 위해서다.
    const res = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
    if (!res.ok) return false
    tokenStorage.set(await res.json())
    return true
  })().finally(() => {
    refreshing = null
  })
  return refreshing
}

function withToken(request: Request) {
  const token = tokenStorage.get()
  if (token) request.headers.set('Authorization', `Bearer ${token}`)
  return request
}

export function loginPath(redirect = window.location.pathname + window.location.search) {
  return `/login?redirect=${encodeURIComponent(redirect)}`
}

// Access 토큰이 만료돼 401이 오면 Refresh로 재발급하고 한 번 다시 보낸다. 재발급에 실패하면 로그인 화면으로 보낸다.
export async function authFetch(request: Request): Promise<Response> {
  const retry = request.clone()
  const res = await fetch(withToken(request))
  if (res.status !== 401 || !tokenStorage.getRefresh()) return res
  if (await refreshTokens()) return fetch(withToken(retry))
  tokenStorage.clear()
  window.location.assign(loginPath())
  return res
}

export const api = createClient<paths>({ baseUrl: API_BASE_URL, fetch: authFetch })
