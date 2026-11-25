import { BrowserRouter, Route, Routes } from 'react-router'
import AppLayout from './layout/AppLayout.tsx'
import { HomePage } from './pages/HomePage.tsx'
import { RecipeListPage } from './pages/RecipeListPage.tsx'
import { SearchPage } from './pages/SearchPage.tsx'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppLayout />}>
          <Route index element={<HomePage />} />
          {/* 각 화면 내용은 해당 기능에서 채운다 */}
          <Route path="browse" element={<h1>둘러보기</h1>} />
          <Route path="search" element={<SearchPage />} />
          <Route path="recipes" element={<RecipeListPage />} />
          <Route path="mypage" element={<h1>마이페이지</h1>} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
