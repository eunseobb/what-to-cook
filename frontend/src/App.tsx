import { BrowserRouter, Route, Routes } from 'react-router'
import { LoginPage } from './components/auth/LoginPage.tsx'
import { SignupPage } from './components/auth/SignupPage.tsx'
import { MyPage } from './components/mypage/MyPage.tsx'
import AppLayout from './layout/AppLayout.tsx'
import RequireAuth from './layout/RequireAuth.tsx'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppLayout />}>
          <Route
            index
            element={
              <>
                <h1>냉장고 레시피</h1>
                <p>가진 재료로 만들 수 있는 레시피를 찾아보세요.</p>
              </>
            }
          />
          {/* 각 화면 내용은 해당 기능에서 채운다 */}
          <Route path="browse" element={<h1>둘러보기</h1>} />
          <Route path="search" element={<h1>검색</h1>} />
          <Route element={<RequireAuth />}>
            <Route path="mypage" element={<MyPage />} />
          </Route>
        </Route>
        {/* 모바일에서 하단 탭바 없는 전체 화면 */}
        <Route element={<AppLayout hideTabBar />}>
          <Route path="login" element={<LoginPage />} />
          <Route path="signup" element={<SignupPage />} />
          <Route element={<RequireAuth />}>
            {/* 온보딩 화면은 F9-5에서 구현한다 */}
            <Route path="onboarding" element={<h1>온보딩</h1>} />
          </Route>
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
