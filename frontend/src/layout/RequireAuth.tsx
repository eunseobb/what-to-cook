import { Navigate, Outlet, useLocation } from 'react-router'
import { loginPath, tokenStorage } from '../api/client'

// 보호 화면: 토큰이 없으면 로그인 화면으로 보내고, 로그인 후 원래 화면으로 돌아온다.
// Access 토큰이 만료된 경우는 API 호출 시 authFetch가 Refresh로 재발급한다.
export default function RequireAuth() {
  const { pathname, search } = useLocation()
  if (!tokenStorage.get()) return <Navigate to={loginPath(pathname + search)} replace />
  return <Outlet />
}
