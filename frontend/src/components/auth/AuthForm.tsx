import type { FormEvent, InputHTMLAttributes, ReactNode } from 'react'
import { Link } from 'react-router'
import './auth.css'

interface AuthFormProps {
  title: string
  description: string
  submitLabel: string
  submitting: boolean
  error: string | null
  onSubmit: () => void
  switchText: string
  switchLink: { to: string; label: string }
  children: ReactNode
}

// 로그인·회원가입 공용 틀. 모바일에서는 하단 탭바 없는 전체 화면이라 닫기 버튼을 둔다.
export function AuthForm({
  title,
  description,
  submitLabel,
  submitting,
  error,
  onSubmit,
  switchText,
  switchLink,
  children,
}: AuthFormProps) {
  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    onSubmit()
  }

  return (
    <div className="auth">
      <div className="auth__top-bar">
        {/* shortcut: 아이콘은 시안의 텍스트 글리프를 그대로 쓴다, 아이콘 세트를 정하면 교체 */}
        <Link to="/" className="auth__close">
          <span aria-hidden="true">‹</span> 닫기
        </Link>
      </div>
      <form className="auth__form" onSubmit={handleSubmit}>
        <div className="auth__heading">
          <h1 className="auth__title">{title}</h1>
          <p className="auth__desc">{description}</p>
        </div>
        <div className="auth__fields">{children}</div>
        {error && (
          <p className="auth__error" role="alert">
            {error}
          </p>
        )}
        <button type="submit" className="auth__submit" disabled={submitting}>
          {submitLabel}
        </button>
        <p className="auth__switch">
          {switchText}
          <Link to={switchLink.to} className="auth__switch-link">
            {switchLink.label}
          </Link>
        </p>
      </form>
    </div>
  )
}

interface AuthFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  hint?: string
  error?: string
}

export function AuthField({ label, hint, error, id, ...input }: AuthFieldProps) {
  const message = error ?? hint
  return (
    <div className="auth-field">
      <label className="auth-field__label" htmlFor={id}>
        {label}
      </label>
      <input
        id={id}
        className="auth-field__input"
        aria-invalid={error ? true : undefined}
        aria-describedby={message ? `${id}-message` : undefined}
        {...input}
      />
      {message && (
        <p id={`${id}-message`} className={error ? 'auth-field__error' : 'auth-field__hint'}>
          {message}
        </p>
      )}
    </div>
  )
}
