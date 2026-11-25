import { RecipeCard, RecipeCardSkeleton, type RecipeCardData } from './RecipeCard'
import { RecipeEmptyState } from './RecipeEmptyState'
import './recipe.css'

const SKELETON_COUNT = 8 // 데스크톱 4열 × 2행

interface RecipeGridProps {
  recipes: RecipeCardData[]
  loading?: boolean
  /** 결과가 없을 때 "AI로 추천받기" 버튼을 누르면 호출된다 */
  onRequestAi?: () => void
}

/** 쇼핑몰형 다열 그리드. 로딩 중이면 스켈레톤, 결과가 없으면 빈 상태를 보여준다. */
export function RecipeGrid({ recipes, loading = false, onRequestAi }: RecipeGridProps) {
  if (loading) {
    return (
      <ul className="recipe-grid" aria-busy="true" aria-label="레시피 불러오는 중">
        {Array.from({ length: SKELETON_COUNT }, (_, i) => (
          <li key={i}>
            <RecipeCardSkeleton />
          </li>
        ))}
      </ul>
    )
  }
  if (recipes.length === 0) return <RecipeEmptyState onRequestAi={onRequestAi} />
  return (
    <ul className="recipe-grid">
      {recipes.map((recipe) => (
        <li key={recipe.id}>
          <RecipeCard recipe={recipe} />
        </li>
      ))}
    </ul>
  )
}
