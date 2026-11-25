/** 검색 결과 화면 주소. 필터 상태는 URL에만 둔다(뒤로가기·공유·새로고침 시 같은 결과). */
export function searchHref(ingredientIds: number[], tagIds: number[]) {
  const query = new URLSearchParams()
  ingredientIds.forEach((id) => query.append('ingredient', String(id)))
  tagIds.forEach((id) => query.append('tag', String(id)))
  return `/search?${query}`
}
