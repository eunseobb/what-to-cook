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

/** 백엔드 ErrorResponse(code, message)를 담은 오류. status로 401·409 등을 구분한다. */
export class ApiError extends Error {
  readonly status: number
  readonly code?: string

  constructor(status: number, code: string | undefined, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

/** openapi-fetch 결과가 실패면 ApiError를 던진다. */
export function throwIfFailed(response: Response, error: unknown, fallback: string) {
  if (response.ok) return
  const body = error as { code?: string; message?: string } | undefined
  throw new ApiError(response.status, body?.code, body?.message ?? fallback)
}
