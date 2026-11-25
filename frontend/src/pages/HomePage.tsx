import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import type { RecipeSummary, TagResponse } from '../api/recipe'
import { getTags } from '../api/search'
import { RecipeGrid } from '../components/recipe/RecipeGrid'
import { IngredientSearchBar } from '../components/search/IngredientSearchBar'
import { TagFilter } from '../components/search/TagFilter'
import { searchHref } from '../components/search/searchHref'
import { parseIds, SECTION_KEYS, SECTIONS, sectionErrorMessage, sectionHref, type SectionKey } from './recipeSections'
// 히어로·섹션 제목은 검색 결과 화면과 같은 시안이라 그 스타일을 같이 쓴다
import './SearchPage.css'
import './HomePage.css'

const SECTION_SIZE = 8 // 데스크톱 인기 4열 × 2행. 나머지 개수는 CSS로 숨긴다.

type Loaded = { key: string; items: RecipeSummary[]; error?: unknown }

function HomeSection({ sectionKey, tagIds }: { sectionKey: SectionKey; tagIds: number[] }) {
  const section = SECTIONS[sectionKey]
  const tagKey = tagIds.join(',')
  const [loaded, setLoaded] = useState<Loaded | null>(null)

  useEffect(() => {
    let ignore = false
    section
      .load(tagKey ? tagKey.split(',').map(Number) : [], undefined, SECTION_SIZE)
      .then((page) => ({ key: tagKey, items: page.items }))
      .catch((error: unknown) => ({ key: tagKey, items: [], error }))
      .then((next) => {
        if (!ignore) setLoaded(next)
      })
    return () => {
      ignore = true
    }
  }, [section, tagKey])

  const current = loaded?.key === tagKey ? loaded : null
  const titleId = `home-section-${sectionKey}`

  return (
    <section
      className={`search-page__results home-section${sectionKey === 'popular' ? '' : ' home-section--compact'}`}
      aria-labelledby={titleId}
    >
      <header className="search-page__section-header">
        <div>
          <h2 id={titleId} className="search-page__section-title">
            {section.title}
          </h2>
          <p className="search-page__section-desc">{section.desc}</p>
        </div>
        <Link className="home-section__all" to={sectionHref(sectionKey, tagIds)}>
          전체 보기 →
        </Link>
      </header>
      {current?.error ? (
        <p className="home-section__notice">{sectionErrorMessage(current.error)}</p>
      ) : current?.items.length === 0 ? (
        <p className="home-section__notice">아직 레시피가 없어요.</p>
      ) : (
        <RecipeGrid loading={!current} recipes={current?.items ?? []} />
      )}
    </section>
  )
}

export function HomePage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const tagIds = parseIds(params.getAll('tag'))
  const [tags, setTags] = useState<TagResponse[]>([])

  useEffect(() => {
    // 태그 목록을 못 받으면 필터 칩 없이 보여준다
    getTags().then(setTags).catch(() => setTags([]))
  }, [])

  const tagHref = (next: number[]) => {
    const query = new URLSearchParams()
    next.forEach((id) => query.append('tag', String(id)))
    return next.length ? `/?${query}` : '/'
  }

  return (
    <div className="search-page">
      <section className="search-page__hero">
        <p className="search-page__brand">냉장고 레시피</p>
        <h1 className="search-page__title">레시피 라이브러리</h1>
        <p className="search-page__desc">냉장고에 있는 재료로 오늘 뭐 먹을지 찾아보세요.</p>
        <IngredientSearchBar onSearch={(items) => navigate(searchHref(items.map((i) => i.id), tagIds))} />
        <TagFilter tags={tags} selectedIds={tagIds} toHref={tagHref} />
      </section>

      {SECTION_KEYS.map((key) => (
        <HomeSection key={key} sectionKey={key} tagIds={tagIds} />
      ))}

      <section className="home-cta">
        <h2 className="home-cta__title">원하는 레시피가 없나요?</h2>
        <p className="home-cta__desc">가진 재료만 알려주면 AI가 바로 만들어 드려요.</p>
        {/* F9-8에서 AI 추천 화면이 생기면 그쪽으로 연결한다. 지금은 재료를 고르는 검색 화면으로 보낸다. */}
        <Link className="home-cta__button" to="/search">
          AI로 추천받기
        </Link>
      </section>
    </div>
  )
}
