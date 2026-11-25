import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { fetchRecipes, type RecipeSummary } from '../../api/recipe'
import { RecipeEmptyState } from './RecipeEmptyState'
import { RecipeGrid } from './RecipeGrid'
import './recipe.css'

// 한 번에 데스크톱 4열×2행, 모바일 2열×3행
const pageSize = () => (window.matchMedia('(max-aspect-ratio: 3/4)').matches ? 6 : 8)

/** 둘러보기: 최근 7일 레시피를 좋아요 순으로, 커서 기반 "더 보기" */
export function RecipeBrowse() {
  const [recipes, setRecipes] = useState<RecipeSummary[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)

  const request = (after?: string) =>
    fetchRecipes({ sort: 'POPULAR', days: 7, size: pageSize(), cursor: after })
      .then((page) => {
        // 첫 페이지는 덮어써서 StrictMode 이중 호출에도 중복되지 않게 한다
        setRecipes((prev) => (after ? [...prev, ...page.items] : page.items))
        setCursor(page.hasNext ? page.nextCursor : null)
      })
      .catch(() => setError(true))
      .finally(() => setLoading(false))

  const load = (after?: string) => {
    setLoading(true)
    setError(false)
    request(after)
  }

  // 첫 페이지: loading 초기값이 true라 여기서는 요청만 보낸다
  useEffect(() => {
    request()
  }, [])

  const firstLoading = loading && recipes.length === 0

  return (
    <div className="browse">
      <header className="browse__hero">
        <p className="browse__brand">냉장고 레시피</p>
        <h1 className="browse__title">둘러보기</h1>
        <p className="browse__desc">최근 7일 동안 올라온 레시피를 좋아요 많은 순으로 모았어요.</p>
      </header>

      <section className="browse__list" aria-label="둘러보기 목록">
        {!(error && recipes.length === 0) && (
          <RecipeGrid
            recipes={recipes}
            loading={firstLoading}
            empty={
              <RecipeEmptyState
                title="이번 주에 올라온 레시피가 아직 없어요"
                description="내 레시피를 처음으로 올려 보세요."
                action={
                  <Link className="recipe-empty__button" to="/recipes/new">
                    레시피 올리기
                  </Link>
                }
              />
            }
          />
        )}
        {error && (
          <p className="browse__error" role="alert">
            레시피를 불러오지 못했어요.
          </p>
        )}
        {(error || (cursor && !firstLoading)) && (
          <div className="browse__more">
            <button
              type="button"
              className="browse__more-button"
              disabled={loading}
              onClick={() => load(cursor ?? undefined)}
            >
              {loading ? '불러오는 중…' : error ? '다시 시도' : '더 보기'}
            </button>
          </div>
        )}
      </section>

      <aside className="browse__cta">
        <h2 className="browse__cta-title">원하는 레시피가 없나요?</h2>
        <p className="browse__cta-desc">가진 재료만 알려주면 AI가 바로 만들어 드려요.</p>
        <Link className="recipe-empty__button" to="/search">
          AI로 추천받기
        </Link>
      </aside>
    </div>
  )
}
