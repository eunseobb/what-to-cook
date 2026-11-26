import { Link } from 'react-router'
import type { TagResponse } from '../../api/recipe'
import './search.css'

interface TagFilterProps {
  tags: TagResponse[]
  selectedIds: number[]
  /** 선택이 바뀐 태그 ID 목록으로 이동할 주소. 필터 상태는 쿼리스트링에 둔다. */
  toHref: (nextSelectedIds: number[]) => string
}

/** 태그 필터 칩(다중 선택). 선택된 칩이 앞에 오고 x로 해제한다. 변경은 링크 이동이다. */
export function TagFilter({ tags, selectedIds, toHref }: TagFilterProps) {
  const selected = tags.filter((t) => selectedIds.includes(t.id))
  const others = tags.filter((t) => !selectedIds.includes(t.id))
  return (
    <ul className="tag-filter" aria-label="태그 필터">
      {selected.map((tag) => (
        <li key={tag.id}>
          <Link
            className="tag-filter__chip tag-filter__chip--selected"
            to={toHref(selectedIds.filter((id) => id !== tag.id))}
            aria-label={`${tag.name} 필터 해제`}
          >
            {tag.name}
            <span className="tag-filter__remove" aria-hidden="true">
              ×
            </span>
          </Link>
        </li>
      ))}
      {others.map((tag) => (
        <li key={tag.id}>
          <Link className="tag-filter__chip" to={toHref([...selectedIds, tag.id])}>
            {tag.name}
          </Link>
        </li>
      ))}
    </ul>
  )
}
