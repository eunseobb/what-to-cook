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

/** 화면에 보여줄 오류 문구. 401은 로그인 안내로 바꾼다. */
export function errorMessage(e: unknown) {
  if (e instanceof ApiError) return e.status === 401 ? '로그인이 필요해요.' : e.message
  return '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.'
}

/** openapi-fetch 결과가 실패면 ApiError를 던진다. */
export function throwIfFailed(response: Response, error: unknown, fallback: string) {
  if (response.ok) return
  const body = error as { code?: string; message?: string } | undefined
  throw new ApiError(response.status, body?.code, body?.message ?? fallback)
}
