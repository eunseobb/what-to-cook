import { useState } from 'react'
import { useNavigate } from 'react-router'
import { signup } from '../../api/auth'
import { AuthField, AuthForm } from './AuthForm'

export function SignupPage() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [nickname, setNickname] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // 비밀번호 확인은 화면에서만 검사한다. API는 이 값을 받지 않는다.
  const mismatch = passwordConfirm !== '' && passwordConfirm !== password

  async function handleSubmit() {
    if (mismatch) return
    setSubmitting(true)
    setError(null)
    try {
      await signup({ email, password, nickname })
      navigate('/onboarding', { replace: true })
    } catch (e) {
      setError((e as Error).message)
      setSubmitting(false)
    }
  }

  return (
    <AuthForm
      title="회원가입"
      description="가입하고 내 취향에 맞는 레시피를 추천받으세요."
      submitLabel="가입하기"
      submitting={submitting}
      error={error}
      onSubmit={handleSubmit}
      switchText="이미 계정이 있으신가요?"
      switchLink={{ to: '/login', label: '로그인' }}
    >
      {/* 길이 제한은 백엔드 SignupRequest 검증과 같다 */}
      <AuthField
        id="signup-email"
        label="이메일"
        type="email"
        autoComplete="email"
        placeholder="example@email.com"
        required
        maxLength={255}
        value={email}
        onChange={(e) => setEmail(e.target.value)}
      />
      <AuthField
        id="signup-nickname"
        label="닉네임"
        autoComplete="nickname"
        placeholder="30자 이내"
        required
        maxLength={30}
        value={nickname}
        onChange={(e) => setNickname(e.target.value)}
      />
      <AuthField
        id="signup-password"
        label="비밀번호"
        type="password"
        autoComplete="new-password"
        placeholder="8~64자"
        hint="8자 이상 64자 이하로 입력하세요."
        required
        minLength={8}
        maxLength={64}
        value={password}
        onChange={(e) => setPassword(e.target.value)}
      />
      <AuthField
        id="signup-password-confirm"
        label="비밀번호 확인"
        type="password"
        autoComplete="new-password"
        placeholder="비밀번호 다시 입력"
        error={mismatch ? '비밀번호가 일치하지 않습니다.' : undefined}
        required
        value={passwordConfirm}
        onChange={(e) => setPasswordConfirm(e.target.value)}
      />
    </AuthForm>
  )
}
