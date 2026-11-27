import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError, errorMessage } from '../../api/client'
import { setBookmark, setLike } from '../../api/community'
import { CommentSection } from '../community/CommentSection'
import { ReportButton } from '../community/ReportButton'
import { fetchRecipeDetail, type Difficulty, type RecipeDetail as RecipeDetailData } from '../../api/recipe'
import './recipe.css'

const DIFFICULTY_LABEL: Record<Difficulty, string> = { EASY: '쉬움', NORMAL: '보통', HARD: '어려움' }

/** 레시피 상세 화면. 데스크톱은 대표 이미지 옆에 요약·재료, 모바일은 한 열로 쌓는다. */
export function RecipeDetail({ recipeId }: { recipeId: number }) {
  const navigate = useNavigate()
  const [recipe, setRecipe] = useState<RecipeDetailData | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    // recipeId가 바뀌면 라우트에서 key로 다시 마운트하므로 상태를 여기서 초기화하지 않는다.
    let ignore = false
    fetchRecipeDetail(recipeId)
      .then((data) => !ignore && setRecipe(data))
      .catch((e: unknown) => {
        if (ignore) return
        setError(e instanceof ApiError && e.status === 404 ? '레시피를 찾을 수 없어요.' : '레시피를 불러오지 못했어요.')
      })
    return () => {
      ignore = true
    }
  }, [recipeId])

  // shortcut: 상세 응답에 내 좋아요·저장 여부가 없어 꺼진 상태로 시작한다, 백엔드가 liked·bookmarked를 주면 초기값으로 쓴다
  const [liked, setLiked] = useState(false)
  const [bookmarked, setBookmarked] = useState(false)
  const [busy, setBusy] = useState(false)

  async function toggleLike() {
    const next = !liked
    setBusy(true)
    try {
      await setLike(recipeId, next)
      setLiked(next)
      setRecipe((r) => r && { ...r, likeCount: r.likeCount + (next ? 1 : -1) })
    } catch (e) {
      // 이미 좋아요(L001)했거나 좋아요하지 않은(L002) 상태면 서버 상태에 맞추기만 한다
      if (e instanceof ApiError && (e.code === 'L001' || e.code === 'L002')) setLiked(next)
      else window.alert(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  async function toggleBookmark() {
    const next = !bookmarked
    setBusy(true)
    try {
      await setBookmark(recipeId, next)
      setBookmarked(next)
    } catch (e) {
      window.alert(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  if (error) {
    return (
      <div className="recipe-detail__status" role="alert">
        <p>{error}</p>
        <Link to="/">홈으로</Link>
      </div>
    )
  }
  if (!recipe) {
    return (
      <p className="recipe-detail__status" aria-busy="true">
        레시피를 불러오는 중…
      </p>
    )
  }

  return (
    <article className="recipe-detail">
      <div className="recipe-detail__topbar">
        <button type="button" className="recipe-detail__back" onClick={() => navigate(-1)}>
          <span aria-hidden="true">‹</span> 뒤로
        </button>
        <ReportButton targetType="RECIPE" targetId={recipeId} className="recipe-detail__report" />
      </div>

      <div className="recipe-detail__top">
        <nav className="recipe-detail__breadcrumb" aria-label="현재 위치">
          <Link to="/">홈</Link> / <span>레시피</span> / <span aria-current="page">{recipe.title}</span>
        </nav>

        <div className="recipe-detail__hero">
          <div className="recipe-detail__thumb">
            {recipe.mainImageUrl && <img src={recipe.mainImageUrl} alt="" />}
            {recipe.source === 'AI_GENERATED' && <span className="recipe-card__ai-badge">AI 생성</span>}
          </div>

          <div className="recipe-detail__side">
            <section className="recipe-detail__summary">
              {recipe.tags.length > 0 && (
                <ul className="recipe-detail__tags">
                  {recipe.tags.map((tag) => (
                    <li key={tag.id} className="recipe-detail__tag">
                      {tag.name}
                    </li>
                  ))}
                </ul>
              )}
              <h1 className="recipe-detail__title">{recipe.title}</h1>
              <p className="recipe-detail__meta">
                {recipe.authorNickname ?? 'AI 추천'} · {recipe.createdAt.slice(0, 10).replaceAll('-', '.')}
              </p>
              <dl className="recipe-detail__stats">
                <div className="recipe-detail__stat">
                  <dt>난이도</dt>
                  <dd>{DIFFICULTY_LABEL[recipe.difficulty]}</dd>
                </div>
                <div className="recipe-detail__stat">
                  <dt>조리시간</dt>
                  <dd>{recipe.cookTimeMinutes}분</dd>
                </div>
                <div className="recipe-detail__stat">
                  <dt>분량</dt>
                  <dd>{recipe.servings}인분</dd>
                </div>
              </dl>
              {/* 모바일에서는 하단 고정 바가 된다. shortcut: 아이콘은 시안의 텍스트 글리프, 아이콘 세트를 정하면 교체 */}
              <div className="recipe-detail__actions">
                <button
                  type="button"
                  className="recipe-detail__action"
                  aria-pressed={liked}
                  disabled={busy}
                  onClick={toggleLike}
                >
                  <span aria-hidden="true">{liked ? '♥' : '♡'}</span> 좋아요 {recipe.likeCount}
                </button>
                <button
                  type="button"
                  className="recipe-detail__action"
                  aria-pressed={bookmarked}
                  disabled={busy}
                  onClick={toggleBookmark}
                >
                  <span aria-hidden="true">{bookmarked ? '★' : '☆'}</span> 저장
                </button>
                <ReportButton targetType="RECIPE" targetId={recipeId} className="recipe-detail__report" />
              </div>
            </section>

            <section className="recipe-detail__ingredients" aria-labelledby="recipe-ingredients-title">
              <div className="recipe-detail__ingredients-head">
                <h2 id="recipe-ingredients-title">재료</h2>
                <span>{recipe.servings}인분 기준</span>
              </div>
              <ul>
                {recipe.ingredients.map((item) => (
                  <li key={item.ingredientId} className="recipe-detail__ingredient">
                    <span>{item.name}</span>
                    <span className="recipe-detail__quantity">
                      {item.quantity ?? ''}
                      {item.unit ?? ''}
                    </span>
                  </li>
                ))}
              </ul>
            </section>
          </div>
        </div>
      </div>

      <div className="recipe-detail__lower">
        <section className="recipe-detail__steps" aria-labelledby="recipe-steps-title">
          <h2 id="recipe-steps-title" className="recipe-detail__section-title">
            조리 순서
          </h2>
          <ol>
            {recipe.steps.map((step) => (
              <li key={step.order} className="recipe-detail__step">
                <span className="recipe-detail__step-number" aria-hidden="true">
                  {step.order}
                </span>
                <div className="recipe-detail__step-body">
                  <p>{step.content}</p>
                  {step.imageUrl && <img src={step.imageUrl} alt={`${step.order}단계 사진`} loading="lazy" />}
                </div>
              </li>
            ))}
          </ol>
        </section>
        <CommentSection recipeId={recipeId} />
      </div>
    </article>
  )
}
