import './recipe.css'

export function RecipeEmptyState({ onRequestAi }: { onRequestAi?: () => void }) {
  return (
    <div className="recipe-empty">
      <h3 className="recipe-empty__title">조건에 맞는 레시피가 없어요</h3>
      <p className="recipe-empty__desc">가진 재료만 알려주면 AI가 바로 만들어 드려요.</p>
      <button type="button" className="recipe-empty__button" onClick={onRequestAi}>
        AI로 추천받기
      </button>
    </div>
  )
}
