import { NavLink, Outlet } from 'react-router'
import './AppLayout.css'

export default function AppLayout() {
  return (
    <>
      <header className="app-header">
        <nav className="app-header__inner" aria-label="주요 메뉴">
          <NavLink to="/" className="app-header__logo">
            냉장고 레시피
          </NavLink>
          <NavLink to="/" end className="app-header__link">
            홈
          </NavLink>
          <NavLink to="/browse" className="app-header__link">
            둘러보기
          </NavLink>
          <NavLink to="/search" className="app-header__link">
            검색
          </NavLink>
          <NavLink to="/mypage" className="app-header__link app-header__link--end">
            마이페이지
          </NavLink>
        </nav>
      </header>
      <main>
        <Outlet />
      </main>
    </>
  )
}
