import { API_BASE_URL, tokenStorage, type Tokens } from './client'

// 백엔드 AuthController(/api/auth)와 같은 모양.
// shortcut: 손으로 맞춘 타입과 fetch 호출이다. schema.d.ts가 OpenAPI로 생성되면(npm run gen:api) api 클라이언트로 바꾼다.

export interface SignupRequest {
  email: string
  password: string
  nickname: string
}

async function post<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  const data = await res.json().catch(() => null)
  // 백엔드 ErrorResponse의 message를 그대로 보여준다
  if (!res.ok) throw new Error(data?.message ?? '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.')
  return data as T
}

export async function login(email: string, password: string) {
  tokenStorage.set(await post<Tokens>('/api/auth/login', { email, password }))
}

// 가입 API는 토큰을 주지 않으므로 가입 직후 로그인해 토큰을 받는다
export async function signup(request: SignupRequest) {
  await post('/api/auth/signup', request)
  await login(request.email, request.password)
}

// 로그인 후 돌아갈 경로. 외부 주소로 보내는 오픈 리다이렉트를 막기 위해 같은 사이트 경로만 허용한다.
export function safeRedirect(path: string | null) {
  return path && path.startsWith('/') && !path.startsWith('//') && !path.startsWith('/\\') ? path : '/'
}
