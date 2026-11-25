import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { login, safeRedirect } from '../../api/auth'
import { AuthField, AuthForm } from './AuthForm'

export function LoginPage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit() {
    setSubmitting(true)
    setError(null)
    try {
      await login(email, password)
      // 보호 화면에서 넘어왔으면 그 화면으로 돌려보낸다
      navigate(safeRedirect(params.get('redirect')), { replace: true })
    } catch (e) {
      setError((e as Error).message)
      setSubmitting(false)
    }
  }

  return (
    <AuthForm
      title="로그인"
      description="냉장고 속 재료로 오늘 뭐 해 먹을지 찾아보세요."
      submitLabel="로그인"
      submitting={submitting}
      error={error}
      onSubmit={handleSubmit}
      switchText="계정이 없으신가요?"
      switchLink={{ to: '/signup', label: '회원가입' }}
    >
      <AuthField
        id="login-email"
        label="이메일"
        type="email"
        autoComplete="email"
        placeholder="example@email.com"
        required
        value={email}
        onChange={(e) => setEmail(e.target.value)}
      />
      <AuthField
        id="login-password"
        label="비밀번호"
        type="password"
        autoComplete="current-password"
        placeholder="비밀번호 입력"
        required
        value={password}
        onChange={(e) => setPassword(e.target.value)}
      />
    </AuthForm>
  )
}
