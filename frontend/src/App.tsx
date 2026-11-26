import { BrowserRouter, Route, Routes } from 'react-router'
import { LoginPage } from './components/auth/LoginPage.tsx'
import { SignupPage } from './components/auth/SignupPage.tsx'
import { OnboardingPage } from './components/onboarding/OnboardingPage.tsx'
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
            <Route path="mypage" element={<h1>마이페이지</h1>} />
          </Route>
        </Route>
        {/* 모바일에서 하단 탭바 없는 전체 화면 */}
        <Route element={<AppLayout hideTabBar />}>
          <Route path="login" element={<LoginPage />} />
          <Route path="signup" element={<SignupPage />} />
        </Route>
        {/* 온보딩은 앱 헤더·탭바 없이 자체 상단 바(로고·단계·진행 막대)만 쓴다 */}
        <Route element={<RequireAuth />}>
          <Route path="onboarding" element={<OnboardingPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
