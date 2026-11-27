import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { fetchMyPreferenceTags, fetchMyRecipes, type MyRecipeList } from '../../api/mypage'
import type { RecipeSummary, TagResponse } from '../../api/recipe'
import { RecipeGrid } from '../recipe/RecipeGrid'
import './mypage.css'

const TABS: { list: MyRecipeList; label: string; empty: string }[] = [
  { list: 'recipes', label: '내 레시피', empty: '아직 올린 레시피가 없어요.' },
  { list: 'bookmarks', label: '저장한 레시피', empty: '아직 저장한 레시피가 없어요.' },
]

// 데스크톱은 4열 × 3행, 모바일은 2열 × 3행씩 불러온다
const pageSize = () => (window.matchMedia('(max-aspect-ratio: 3/4)').matches ? 6 : 12)

export function MyPage() {
  const [params] = useSearchParams()
  // 탭 상태는 쿼리스트링에 둔다(뒤로가기·새로고침에도 같은 탭)
  const tab = TABS.find((t) => t.list === params.get('tab')) ?? TABS[0]

  const [tags, setTags] = useState<TagResponse[] | null>(null)

  useEffect(() => {
    // 취향 요약은 부가 정보라 실패하면 빈 목록으로 둔다
    fetchMyPreferenceTags().then(setTags, () => setTags([]))
  }, [])

  return (
    <div className="mypage">
      <div className="mypage__title-row">
        <h1 className="mypage__title">마이페이지</h1>
      </div>

      <section className="mypage__profile" aria-label="내 취향">
        <div className="mypage__profile-text">
          <h2 className="mypage__profile-name">내 취향</h2>
          {tags && tags.length === 0 && <p className="mypage__profile-sub">아직 고른 취향이 없어요.</p>}
        </div>
        {tags && tags.length > 0 && (
          <ul className="mypage__tags">
            {tags.map((tag) => (
              <li key={tag.id} className="mypage__tag">
                {tag.name}
              </li>
            ))}
          </ul>
        )}
        <Link to="/onboarding" className="mypage__button">
          취향 수정
        </Link>
      </section>

      <nav className="mypage__tabs" aria-label="내 레시피 목록">
        {TABS.map(({ list, label }) => (
          <Link
            key={list}
            to={`?tab=${list}`}
            className={`mypage__tab${list === tab.list ? ' mypage__tab--active' : ''}`}
            aria-current={list === tab.list ? 'page' : undefined}
          >
            {label}
          </Link>
        ))}
      </nav>

      {/* 탭이 바뀌면 key로 목록 상태를 새로 만든다 */}
      <MyRecipeGrid key={tab.list} tab={tab} />
    </div>
  )
}

function MyRecipeGrid({ tab }: { tab: (typeof TABS)[number] }) {
  const [recipes, setRecipes] = useState<RecipeSummary[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  // 늦게 도착한 이전 요청의 응답은 버린다
  const requestId = useRef(0)

  // 상태 변경은 응답이 온 뒤에만 한다. 첫 페이지는 loading=true로 시작한다.
  function fetchPage(list: MyRecipeList, cursor: string | null) {
    const id = ++requestId.current
    fetchMyRecipes(list, cursor, pageSize())
      .then((page) => {
        if (id !== requestId.current) return
        setRecipes((prev) => (cursor ? [...prev, ...page.items] : page.items))
        setNextCursor(page.hasNext ? page.nextCursor : null)
      })
      .catch((e: Error) => id === requestId.current && setError(e.message))
      .finally(() => id === requestId.current && setLoading(false))
  }

  useEffect(() => {
    fetchPage(tab.list, null)
  }, [tab.list])

  function loadMore() {
    setLoading(true)
    setError(null)
    fetchPage(tab.list, nextCursor)
  }

  const firstLoad = loading && recipes.length === 0

  return (
    <>
      {error && (
        <p className="mypage__error" role="alert">
          {error}
        </p>
      )}
      {firstLoad || recipes.length > 0 ? (
        <RecipeGrid recipes={recipes} loading={firstLoad} />
      ) : (
        !error && <p className="mypage__empty">{tab.empty}</p>
      )}

      {/* 실패하면 같은 커서로 다시 시도한다(nextCursor는 성공할 때만 바뀐다) */}
      {!firstLoad && (nextCursor || (error && recipes.length === 0)) && (
        <div className="mypage__more">
          <button
            type="button"
            className="mypage__button mypage__button--more"
            disabled={loading}
            onClick={loadMore}
          >
            {loading ? '불러오는 중…' : error ? '다시 시도' : '더 보기'}
          </button>
        </div>
      )}
    </>
  )
}
