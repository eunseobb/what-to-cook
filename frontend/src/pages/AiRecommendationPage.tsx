import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { recommendWithAi, type AiRecipe } from '../api/ai'
import { ApiError, getIngredients, type IngredientOption } from '../api/search'
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

type Failure = { kind: 'rate-limit' } | { kind: 'unavailable'; message: string; retryable: boolean }

const UNAVAILABLE_MESSAGE = '잠시 후 다시 시도해 주세요. 요청이 많거나 AI 서비스에 문제가 있을 때 생겨요.'

// 사용자별 한도 초과(AI006)만 따로 안내하고, 서킷 오픈·타임아웃·생성 실패·대기열 초과는 다시 시도를 권한다.
// 로그인 필요(401)와 재료를 못 알아본 경우(400)는 다시 시도해도 같아서 버튼 없이 이유만 보여준다.
function toFailure(error: unknown): Failure {
  if (!(error instanceof ApiError)) return { kind: 'unavailable', message: UNAVAILABLE_MESSAGE, retryable: true }
  if (error.code === 'AI006') return { kind: 'rate-limit' }
  if (error.status === 401) {
    return { kind: 'unavailable', message: '로그인하면 AI 추천을 받을 수 있어요.', retryable: false }
  }
  if (error.status === 400) return { kind: 'unavailable', message: error.message, retryable: false }
  return { kind: 'unavailable', message: UNAVAILABLE_MESSAGE, retryable: true }
}

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
  // 다시 시도할 때마다 올려 같은 재료로 새로 요청한다
  const [attempt, setAttempt] = useState(0)
  const requestKey = `${ingredientKey}#${attempt}`

  useEffect(() => {
    if (!ingredientKey) return
    const key = `${ingredientKey}#${attempt}`
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
      .then((recipes) =>
        done(
          recipes.length > 0
            ? { key, status: 'done', recipes }
            : { key, status: 'error', error: new ApiError(200, '', 'AI005') },
        ),
      )
      .catch((error: unknown) => done({ key, status: 'error', error }))
    // 화면을 떠나면 폴링을 멈춘다
    return () => controller.abort()
  }, [ingredientKey, attempt])

  const current = result?.key === requestKey ? result : null
  const failure = current?.status === 'error' ? toFailure(current.error) : null
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

      {failure?.kind === 'rate-limit' && (
        <section className="ai-page__error" role="alert">
          <h2 className="ai-page__error-title">AI 추천을 잠시 쉬어 갈게요</h2>
          {/* 백엔드 app.ai.rate-limit(10회/1시간)과 같은 값. 설정을 바꾸면 함께 고친다 */}
          <p className="ai-page__error-desc">
            AI 추천은 1시간에 10번까지 받을 수 있어요. 조금 뒤에 다시 시도하거나, 커뮤니티 레시피를 둘러보세요.
          </p>
          <Link className="ai-page__error-button" to="/browse">
            둘러보기로 가기
          </Link>
        </section>
      )}

      {failure?.kind === 'unavailable' && (
        <section className="ai-page__error" role="alert">
          <h2 className="ai-page__error-title">지금은 AI 추천을 쓸 수 없어요</h2>
          <p className="ai-page__error-desc">{failure.message}</p>
          {failure.retryable && (
            <button
              type="button"
              className="ai-page__error-button ai-page__error-button--primary"
              onClick={() => setAttempt((n) => n + 1)}
            >
              다시 시도
            </button>
          )}
        </section>
      )}
    </div>
  )
}
