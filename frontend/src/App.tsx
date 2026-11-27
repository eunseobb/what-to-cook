import { BrowserRouter, Route, Routes } from 'react-router'
import { RecipeDetailRoute } from './components/recipe/RecipeDetail.tsx'
import AppLayout from './layout/AppLayout.tsx'

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
          <Route path="mypage" element={<h1>마이페이지</h1>} />
          <Route path="recipes/:recipeId" element={<RecipeDetailRoute />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
