import type { ReactNode } from 'react'
import './recipe.css'

interface RecipeEmptyStateProps {
  title?: string
  description?: string
  onRequestAi?: () => void
  /** 기본 "AI로 추천받기" 버튼 대신 보여줄 동작 (예: 레시피 올리기 링크) */
  action?: ReactNode
}

export function RecipeEmptyState({
  title = '조건에 맞는 레시피가 없어요',
  description = '가진 재료만 알려주면 AI가 바로 만들어 드려요.',
  onRequestAi,
  action,
}: RecipeEmptyStateProps) {
  return (
    <div className="recipe-empty">
      <h3 className="recipe-empty__title">{title}</h3>
      <p className="recipe-empty__desc">{description}</p>
      {action ?? (
        <button type="button" className="recipe-empty__button" onClick={onRequestAi}>
          AI로 추천받기
        </button>
      )}
    </div>
  )
}
