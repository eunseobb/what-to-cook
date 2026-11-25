import { NavLink, Outlet } from 'react-router'
import './AppLayout.css'

// shortcut: 아이콘은 시안의 텍스트 글리프를 그대로 쓴다, 아이콘 세트를 정하면 교체
const TABS = [
  { to: '/', label: '홈', icon: '⌂' },
  { to: '/browse', label: '둘러보기', icon: '▦' },
  { to: '/search', label: '검색', icon: '⌕' },
  { to: '/mypage', label: '마이페이지', icon: '◯' },
]

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
      <nav className="tab-bar" aria-label="하단 탭">
        {TABS.map(({ to, label, icon }) => (
          <NavLink key={to} to={to} end={to === '/'} className="tab-bar__item">
            <span className="tab-bar__icon" aria-hidden="true">
              {icon}
            </span>
            {label}
          </NavLink>
        ))}
      </nav>
    </>
  )
}
