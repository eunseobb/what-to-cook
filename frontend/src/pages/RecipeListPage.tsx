import { useEffect, useState } from 'react'
import { Navigate, useSearchParams } from 'react-router'
import type { RecipeSummary } from '../api/recipe'
import type { CursorPage } from '../api/search'
import { RecipeGrid } from '../components/recipe/RecipeGrid'
import { isSectionKey, parseIds, SECTIONS, sectionErrorMessage } from './recipeSections'
// 검색 결과 화면과 같은 목록 시안(제목 + 다열 그리드 + 더 보기)이라 그 스타일을 같이 쓴다
import './SearchPage.css'
import './HomePage.css'

const PAGE_SIZE = 20

type Result = CursorPage<RecipeSummary> & { key: string; error?: unknown; moreError?: unknown }

/** 메인 화면 섹션의 "전체 보기" 목록. 조건은 쿼리스트링에 둔다(/recipes?section=ai&tag=1). */
export function RecipeListPage() {
  const [params] = useSearchParams()
  const sectionKey = params.get('section')
  const tagIds = parseIds(params.getAll('tag'))
  const queryKey = `${sectionKey}|${tagIds.join(',')}`
  const [result, setResult] = useState<Result | null>(null)
  const [loadingMore, setLoadingMore] = useState(false)

  useEffect(() => {
    const [key, tagPart] = queryKey.split('|')
    if (!isSectionKey(key)) return
    let ignore = false
    SECTIONS[key]
      .load(tagPart ? tagPart.split(',').map(Number) : [], undefined, PAGE_SIZE)
      .then((page) => ({ ...page, key: queryKey }))
      .catch((error: unknown) => ({ key: queryKey, items: [], nextCursor: null, hasNext: false, error }))
      .then((next) => {
        if (!ignore) setResult(next)
      })
    return () => {
      ignore = true
    }
  }, [queryKey])

  if (!isSectionKey(sectionKey)) return <Navigate to="/" replace />
  const section = SECTIONS[sectionKey]
  const current = result?.key === queryKey ? result : null

  const loadMore = () => {
    if (!current?.nextCursor) return
    // 그사이 조건이 바뀌었으면 이전 조건의 결과는 버린다
    const update = (fn: (prev: Result) => Result) =>
      setResult((prev) => (prev?.key === current.key ? fn(prev) : prev))
    setLoadingMore(true)
    update((prev) => ({ ...prev, moreError: undefined }))
    section
      .load(tagIds, current.nextCursor, PAGE_SIZE)
      .then((page) => update((prev) => ({ ...prev, ...page, items: [...prev.items, ...page.items] })))
      .catch((moreError: unknown) => update((prev) => ({ ...prev, moreError })))
      .finally(() => setLoadingMore(false))
  }

  return (
    <div className="search-page">
      <section className="search-page__results" aria-labelledby="recipe-list-title">
        <header className="search-page__section-header">
          <div>
            <h1 id="recipe-list-title" className="search-page__section-title">
              {section.title}
            </h1>
            <p className="search-page__section-desc">{section.desc}</p>
          </div>
        </header>
        {current?.error ? (
          <p className="home-section__notice">{sectionErrorMessage(current.error)}</p>
        ) : current?.items.length === 0 ? (
          <p className="home-section__notice">아직 레시피가 없어요.</p>
        ) : (
          <RecipeGrid loading={!current} recipes={current?.items ?? []} />
        )}
        {current?.moreError != null && (
          <p className="search-page__error" role="alert">
            {sectionErrorMessage(current.moreError)}
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
    </div>
  )
}
