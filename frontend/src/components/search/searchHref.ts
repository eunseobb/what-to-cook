/** 쿼리스트링 값 중 양의 정수 ID만 중복 없이 쓴다 */
export const parseIds = (values: string[]) => [
  ...new Set(values.map(Number).filter((n) => Number.isSafeInteger(n) && n > 0)),
]

function conditionQuery(ingredientIds: number[], tagIds: number[]) {
  const query = new URLSearchParams()
  ingredientIds.forEach((id) => query.append('ingredient', String(id)))
  tagIds.forEach((id) => query.append('tag', String(id)))
  return query
}

/** 검색 결과 화면 주소. 필터 상태는 URL에만 둔다(뒤로가기·공유·새로고침 시 같은 결과). */
export function searchHref(ingredientIds: number[], tagIds: number[]) {
  return `/search?${conditionQuery(ingredientIds, tagIds)}`
}

/** AI 추천 화면 주소. 태그도 넘겨 "검색 결과"로 돌아갈 때 같은 조건이 되게 한다. AI 생성에는 재료만 쓴다. */
export function aiHref(ingredientIds: number[], tagIds: number[]) {
  return `/recommendations/ai?${conditionQuery(ingredientIds, tagIds)}`
}
