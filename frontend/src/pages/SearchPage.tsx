import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import type { TagResponse } from '../api/recipe'
import {
  ApiError,
  getIngredients,
  getTags,
  recommendByIngredients,
  type CursorPage,
  type IngredientOption,
  type RecommendedRecipe,
} from '../api/search'
import { RecipeGrid } from '../components/recipe/RecipeGrid'
import { IngredientSearchBar } from '../components/search/IngredientSearchBar'
import { TagFilter } from '../components/search/TagFilter'
import { aiHref, parseIds, searchHref } from '../components/search/searchHref'
import './SearchPage.css'

const splitIds = (key: string) => (key ? key.split(',').map(Number) : [])

type Result = CursorPage<RecommendedRecipe> & { key: string; error?: unknown; moreError?: unknown }

const errorMessage = (error: unknown) =>
  error instanceof ApiError && error.status === 401
    ? '로그인하면 가진 재료로 레시피를 찾아 드려요.'
    : error instanceof Error
      ? error.message
      : '레시피를 불러오지 못했어요.'

export function SearchPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const ingredientIds = parseIds(params.getAll('ingredient'))
  const tagIds = parseIds(params.getAll('tag'))
  const ingredientKey = ingredientIds.join(',')
  const queryKey = `${ingredientKey}|${tagIds.join(',')}`

  const [tags, setTags] = useState<TagResponse[]>([])
  const [names, setNames] = useState<{ key: string; items: IngredientOption[] }>({ key: '', items: [] })
  const [result, setResult] = useState<Result | null>(null)
  const [loadingMore, setLoadingMore] = useState(false)

  useEffect(() => {
    // 태그 목록을 못 받으면 필터 칩 없이 검색만 쓴다
    getTags().then(setTags).catch(() => setTags([]))
  }, [])

  // URL의 재료 ID를 칩 이름으로 바꾼다
  useEffect(() => {
    if (!ingredientKey) return
    let ignore = false
    getIngredients(splitIds(ingredientKey))
      .catch(() => [])
      .then((items) => {
        if (!ignore) setNames({ key: ingredientKey, items })
      })
    return () => {
      ignore = true
    }
  }, [ingredientKey])

  useEffect(() => {
    const [ingredientPart, tagPart] = queryKey.split('|')
    if (!ingredientPart) return
    let ignore = false
    recommendByIngredients(splitIds(ingredientPart), splitIds(tagPart))
      .then((page) => ({ ...page, key: queryKey }))
      .catch((error: unknown) => ({ key: queryKey, items: [], nextCursor: null, hasNext: false, error }))
      .then((next) => {
        if (!ignore) setResult(next)
      })
    return () => {
      ignore = true
    }
  }, [queryKey])

  const current = result?.key === queryKey ? result : null

  const loadMore = () => {
    if (!current?.nextCursor) return
    // 그사이 조건이 바뀌었으면 이전 조건의 결과는 버린다
    const update = (fn: (prev: Result) => Result) =>
      setResult((prev) => (prev?.key === current.key ? fn(prev) : prev))
    setLoadingMore(true)
    update((prev) => ({ ...prev, moreError: undefined }))
    recommendByIngredients(ingredientIds, tagIds, current.nextCursor)
      .then((page) => update((prev) => ({ ...prev, ...page, items: [...prev.items, ...page.items] })))
      .catch((moreError: unknown) => update((prev) => ({ ...prev, moreError })))
      .finally(() => setLoadingMore(false))
  }

  const requestAi = () => navigate(aiHref(ingredientIds, tagIds))

  const namesReady = !ingredientKey || names.key === ingredientKey

  return (
    <div className="search-page">
      <section className="search-page__hero">
        <p className="search-page__brand">냉장고 레시피</p>
        <h1 className="search-page__title">재료로 찾은 레시피</h1>
        <p className="search-page__desc">
          가진 재료와 태그로 찾은 결과예요. 조건을 바꾸면 주소(URL)도 함께 바뀌어요.
        </p>
        <IngredientSearchBar
          // URL의 재료가 바뀌면 칩을 다시 채운다
          key={namesReady ? ingredientKey : 'loading'}
          initialIngredients={ingredientKey && namesReady ? names.items : []}
          onSearch={(items) => navigate(searchHref(items.map((i) => i.id), tagIds))}
        />
        <TagFilter tags={tags} selectedIds={tagIds} toHref={(next) => searchHref(ingredientIds, next)} />
      </section>

      {ingredientKey && (
        <section className="search-page__results" aria-labelledby="search-results-title">
          <header className="search-page__section-header">
            <div>
              <h2 id="search-results-title" className="search-page__section-title">
                일치율 높은 순
              </h2>
              <p className="search-page__section-desc">기본 양념은 일치율 계산에서 빼요</p>
            </div>
          </header>
          {current?.error ? (
            <p className="search-page__error" role="alert">
              {errorMessage(current.error)}
            </p>
          ) : (
            <RecipeGrid
              loading={!current}
              recipes={(current?.items ?? []).map((item) => ({ ...item.recipe, matchRate: item.matchRate }))}
              onRequestAi={requestAi}
            />
          )}
          {current?.moreError != null && (
            <p className="search-page__error" role="alert">
              {errorMessage(current.moreError)}
            </p>
          )}
          {current?.hasNext && (
            <div className="search-page__more">
              <button type="button" className="search-page__more-button" onClick={loadMore} disabled={loadingMore}>
                {loadingMore ? '불러오는 중…' : '더 보기'}
              </button>
            </div>
          )}
        </section>
      )}

      {current && current.items.length > 0 && (
        <section className="search-page__cta" aria-labelledby="search-cta-title">
          <h2 id="search-cta-title" className="search-page__cta-title">
            원하는 레시피가 없나요?
          </h2>
          <p className="search-page__cta-desc">가진 재료만 알려주면 AI가 바로 만들어 드려요.</p>
          <button type="button" className="recipe-empty__button" onClick={requestAi}>
            AI로 추천받기
          </button>
        </section>
      )}
    </div>
  )
}
