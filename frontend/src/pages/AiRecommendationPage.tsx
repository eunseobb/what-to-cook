import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { recommendWithAi, type AiRecipe } from '../api/ai'
import { getIngredients, type IngredientOption } from '../api/search'
import { RecipeCard, RecipeCardSkeleton, type RecipeCardData } from '../components/recipe/RecipeCard'
import { parseIds, searchHref } from '../components/search/searchHref'
import './AiRecommendationPage.css'

const SKELETON_COUNT = 3 // 한 번에 1~3개를 만든다
// 시안(Spinner)의 호 그대로. 색은 CSS에서 토큰으로 준다.
const SPINNER_PATH =
  'M18 8.99931C18 10.7746 17.475 12.5102 16.491 13.9878C15.507 15.4655 14.1079 16.6191 12.4699 17.3035C10.8319 17.988 9.02798 18.1727 7.28522 17.8344C5.54246 17.4962 3.93865 16.6501 2.67559 15.4026C1.41252 14.1551 0.546609 12.5619 0.186796 10.8234C-0.173016 9.08498 -0.0106581 7.27896 0.653444 5.63257C1.31755 3.98618 2.45373 2.57296 3.91904 1.57073C5.38436 0.568487 7.11336 0.0219937 8.8885 0L8.91638 2.24983C7.58502 2.26632 6.28827 2.67619 5.18928 3.42787C4.0903 4.17955 3.23816 5.23946 2.74008 6.47425C2.24201 7.70905 2.12024 9.06356 2.3901 10.3674C2.65996 11.6712 3.30939 12.8661 4.25669 13.8018C5.20399 14.7374 6.40684 15.372 7.71392 15.6257C9.02099 15.8793 10.3739 15.7408 11.6024 15.2275C12.8309 14.7141 13.8802 13.8489 14.6182 12.7407C15.3562 11.6325 15.75 10.3308 15.75 8.99931H18Z'

type Result =
  | { key: string; status: 'done'; recipes: AiRecipe[] }
  | { key: string; status: 'error'; error: unknown }

// 상세 응답의 태그에는 색 키가 없어 첫 태그(음식 종류 우선 정렬)를 기본 색으로 카드에 보인다.
const toCard = (recipe: AiRecipe): RecipeCardData => ({
  ...recipe,
  representativeTag: recipe.tags[0] ? { ...recipe.tags[0], colorKey: 'accent-sage' } : null,
})

export function AiRecommendationPage() {
  const [params] = useSearchParams()
  const ingredientIds = parseIds(params.getAll('ingredient'))
  const tagIds = parseIds(params.getAll('tag'))
  const ingredientKey = ingredientIds.join(',')

  const [names, setNames] = useState<{ key: string; items: IngredientOption[] }>({ key: '', items: [] })
  const [result, setResult] = useState<Result | null>(null)

  useEffect(() => {
    if (!ingredientKey) return
    const controller = new AbortController()
    const done = (next: Result) => {
      if (!controller.signal.aborted) setResult(next)
    }
    getIngredients(ingredientKey.split(',').map(Number))
      .then((items) => {
        if (controller.signal.aborted) return []
        setNames({ key: ingredientKey, items })
        return recommendWithAi(
          items.map((i) => i.name),
          controller.signal,
        )
      })
      .then((recipes) => done({ key: ingredientKey, status: 'done', recipes }))
      .catch((error: unknown) => done({ key: ingredientKey, status: 'error', error }))
    // 화면을 떠나면 폴링을 멈춘다
    return () => controller.abort()
  }, [ingredientKey])

  const current = result?.key === ingredientKey ? result : null
  const loading = ingredientKey !== '' && !current
  const chips = names.key === ingredientKey ? names.items : []

  return (
    <div className="ai-page">
      <Link className="ai-page__back" to={searchHref(ingredientIds, tagIds)}>
        <span aria-hidden="true">‹</span> 검색 결과
      </Link>
      <section className="ai-page__hero">
        <h1 className="ai-page__title">AI 추천 레시피</h1>
        <p className="ai-page__desc">
          커뮤니티에 맞는 레시피가 없어서 AI가 가진 재료로 만들었어요. 직접 확인하고 조리해 주세요.
        </p>
        {chips.length > 0 && (
          <ul className="ai-page__chips" aria-label="AI에게 알려준 재료">
            {chips.map((item) => (
              <li key={item.id} className="ai-page__chip">
                {item.name}
              </li>
            ))}
          </ul>
        )}
      </section>

      {!ingredientKey && (
        <p className="ai-page__empty">
          재료를 먼저 골라 주세요. <Link to="/search">재료 검색으로 가기</Link>
        </p>
      )}

      {loading && (
        <>
          <div className="ai-page__status" role="status">
            <svg className="ai-page__spinner" width="18" height="18" viewBox="0 0 18 17.9993" aria-hidden="true">
              <path fill="currentColor" d={SPINNER_PATH} />
            </svg>
            <div className="ai-page__status-text">
              <p className="ai-page__status-title">AI가 레시피를 만들고 있어요</p>
              <p className="ai-page__status-desc">보통 10~20초 걸려요.</p>
            </div>
          </div>
          <ul className="recipe-grid" aria-busy="true" aria-label="AI 레시피 만드는 중">
            {Array.from({ length: SKELETON_COUNT }, (_, i) => (
              <li key={i}>
                <RecipeCardSkeleton />
              </li>
            ))}
          </ul>
        </>
      )}

      {current?.status === 'done' && (
        <>
          <ul className="recipe-grid">
            {current.recipes.map((recipe) => (
              <li key={recipe.id} className="ai-page__card">
                <RecipeCard recipe={toCard(recipe)} />
                {/* 편집 화면(F9-10)이 AI 레시피를 가져와(POST /api/recipes/{id}/fork) 편집한다 */}
                <Link className="ai-page__edit" to={`/recipes/new?fromAi=${recipe.id}`}>
                  편집해서 올리기
                </Link>
              </li>
            ))}
          </ul>
          <p className="ai-page__notice">
            AI가 만든 레시피는 분량이나 조리법이 정확하지 않을 수 있어요. 직접 만들어 보고 고쳐서 올려 주시면
            다른 사람에게 큰 도움이 돼요.
          </p>
        </>
      )}

      {current?.status === 'error' && (
        <p className="ai-page__empty" role="alert">
          AI 레시피를 만들지 못했어요.
        </p>
      )}
    </div>
  )
}
