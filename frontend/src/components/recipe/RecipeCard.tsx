import type { RecipeSummary } from '../../api/recipe'
import './recipe.css'

export type RecipeCardData = RecipeSummary & {
  /** 재료 검색 결과에서만 준다. 0~1 */
  matchRate?: number
  /** 예상 금액(F4). 값이 있을 때만 "약 ○○원"을 보여준다. */
  estimatedPrice?: number
}

// colorKey는 서버 값이므로 토큰 이름 모양일 때만 CSS 변수로 쓴다.
const placeholderColor = (key?: string) =>
  `var(--color-${key && /^[a-z-]+$/.test(key) ? key : 'accent-sage'})`

export function RecipeCard({ recipe }: { recipe: RecipeCardData }) {
  const tag = recipe.representativeTag
  return (
    <a className="recipe-card" href={`/recipes/${recipe.id}`}>
      <div className="recipe-card__thumb" style={{ background: placeholderColor(tag?.colorKey) }}>
        {recipe.mainImageUrl && <img src={recipe.mainImageUrl} alt="" loading="lazy" />}
        {recipe.source === 'AI_GENERATED' && <span className="recipe-card__ai-badge">AI 생성</span>}
      </div>
      <div className="recipe-card__meta">
        {tag && <span className="recipe-card__tag">{tag.name}</span>}
        <span>· {recipe.cookTimeMinutes}분</span>
        {recipe.matchRate !== undefined && (
          <span className="recipe-card__match">일치 {Math.round(recipe.matchRate * 100)}%</span>
        )}
      </div>
      <h3 className="recipe-card__title">{recipe.title}</h3>
      {recipe.estimatedPrice !== undefined && (
        <p className="recipe-card__price">약 {recipe.estimatedPrice.toLocaleString('ko-KR')}원</p>
      )}
    </a>
  )
}

export function RecipeCardSkeleton() {
  return (
    <div className="recipe-card recipe-card--skeleton" aria-hidden="true">
      <div className="recipe-card__thumb" />
      <div className="recipe-card__bar" style={{ width: '40%' }} />
      <div className="recipe-card__bar" style={{ width: '80%' }} />
      <div className="recipe-card__bar" style={{ width: '30%' }} />
    </div>
  )
}
