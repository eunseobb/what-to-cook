import { useEffect, useState, type KeyboardEvent } from 'react'
import { autocompleteIngredients, type Ingredient } from '../../api/onboarding'

interface IngredientPickerProps {
  id: string
  label: string
  hint: string
  variant: 'allergy' | 'dislike'
  selected: Ingredient[]
  onChange: (selected: Ingredient[]) => void
  /** 후보에서 뺄 재료 id. 알레르기와 비선호에 같은 재료가 들어가면 서버가 거부한다. */
  excludedIds: Set<number>
}

// 온보딩 전용 최소 자동완성. shortcut: 검색 화면의 재료 검색창이 생기면 공용 컴포넌트로 합친다.
export function IngredientPicker({ id, label, hint, variant, selected, onChange, excludedIds }: IngredientPickerProps) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<{ q: string; items: Ingredient[] }>({ q: '', items: [] })
  const [active, setActive] = useState(0)
  const [open, setOpen] = useState(false)

  useEffect(() => {
    const q = query.trim()
    if (!q) return
    // 입력이 멈춘 뒤에만 요청하고, 늦게 온 이전 응답은 버린다
    const controller = new AbortController()
    const timer = setTimeout(() => {
      autocompleteIngredients(q, controller.signal)
        .then((items) => {
          setResults({ q, items })
          setActive(0)
        })
        .catch(() => {}) // 자동완성 실패는 후보 없음으로 둔다
    }, 200)
    return () => {
      clearTimeout(timer)
      controller.abort()
    }
  }, [query])

  // 지금 입력에 대한 결과만 보여준다. 이전 입력의 후보를 Enter로 잘못 고르지 않게 한다.
  const q = query.trim()
  const options = q && results.q === q ? results.items.filter((item) => !excludedIds.has(item.id)) : []
  const expanded = open && options.length > 0
  const listId = `${id}-list`

  function pick(item: Ingredient) {
    onChange([...selected, item])
    setQuery('')
  }

  function handleKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (!expanded) return
    if (e.key === 'ArrowDown') setActive((i) => (i + 1) % options.length)
    else if (e.key === 'ArrowUp') setActive((i) => (i - 1 + options.length) % options.length)
    else if (e.key === 'Enter') pick(options[Math.min(active, options.length - 1)])
    else if (e.key === 'Escape') setOpen(false)
    else return
    e.preventDefault()
  }

  return (
    <div className="ingredient-picker">
      <div className="ingredient-picker__label">
        <label className="ingredient-picker__title" htmlFor={id}>
          {label}
        </label>
        <p id={`${id}-hint`} className="ingredient-picker__hint">
          {hint}
        </p>
      </div>
      <input
        id={id}
        className="ingredient-picker__input"
        role="combobox"
        aria-expanded={expanded}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={expanded ? `${listId}-${active}` : undefined}
        aria-describedby={`${id}-hint`}
        autoComplete="off"
        placeholder="재료 검색"
        value={query}
        onChange={(e) => {
          setQuery(e.target.value)
          setOpen(true)
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        onKeyDown={handleKeyDown}
      />
      {expanded && (
        <ul id={listId} className="ingredient-picker__list" role="listbox" aria-label={`${label} 재료 후보`}>
          {options.map((item, i) => (
            <li
              key={item.id}
              id={`${listId}-${i}`}
              role="option"
              aria-selected={i === active}
              className="ingredient-picker__option"
              // 클릭 전에 입력창 blur로 목록이 닫히지 않게 한다
              onMouseDown={(e) => e.preventDefault()}
              onMouseEnter={() => setActive(i)}
              onClick={() => pick(item)}
            >
              {item.name}
            </li>
          ))}
        </ul>
      )}
      {selected.length > 0 && (
        <ul className="ingredient-picker__chips" aria-label={`선택한 ${label} 재료`}>
          {selected.map((item) => (
            <li key={item.id} className={`ingredient-chip ingredient-chip--${variant}`}>
              {item.name}
              <button
                type="button"
                className="ingredient-chip__remove"
                aria-label={`${item.name} 삭제`}
                onClick={() => onChange(selected.filter((s) => s.id !== item.id))}
              >
                {/* shortcut: 아이콘은 시안의 텍스트 글리프를 그대로 쓴다, 아이콘 세트를 정하면 교체 */}
                <span aria-hidden="true">×</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
