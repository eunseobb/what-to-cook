import { useEffect, useId, useState, type KeyboardEvent } from 'react'
import { autocompleteIngredients, type IngredientOption } from '../../api/search'
import './search.css'

const MAX_INGREDIENTS = 30 // 백엔드 추천 요청 한도
const DEBOUNCE_MS = 150
// 후보는 요청 당시 검색어와 함께 둔다. 입력이 바뀐 뒤 이전 후보가 선택되지 않게 하기 위해서다.
const NO_SUGGESTIONS = { q: '', items: [] as IngredientOption[] }

interface IngredientSearchBarProps {
  /** 처음 칩으로 보여줄 재료. 바깥 값이 바뀌면 key를 바꿔 다시 마운트한다. */
  initialIngredients?: IngredientOption[]
  /** "검색" 버튼이나 빈 입력에서 Enter를 누르면 칩으로 쌓인 재료를 넘긴다. */
  onSearch: (ingredients: IngredientOption[]) => void
}

/** 재료 검색창: 자동완성(F1)으로 고른 표준 재료를 칩으로 쌓는다. */
export function IngredientSearchBar({ initialIngredients = [], onSearch }: IngredientSearchBarProps) {
  const [chips, setChips] = useState(initialIngredients)
  const [text, setText] = useState('')
  const [suggestions, setSuggestions] = useState(NO_SUGGESTIONS)
  const [active, setActive] = useState(-1)
  const listId = useId()

  useEffect(() => {
    const q = text.trim()
    if (!q) return
    const controller = new AbortController()
    const timer = setTimeout(() => {
      autocompleteIngredients(q, controller.signal)
        .then((result) => {
          setSuggestions({ q, items: result })
          setActive(-1)
        })
        // 자동완성 실패(취소 포함)는 후보 없이 입력만 계속 받는다
        .catch(() => setSuggestions(NO_SUGGESTIONS))
    }, DEBOUNCE_MS)
    return () => {
      clearTimeout(timer)
      controller.abort()
    }
  }, [text])

  const current = suggestions.q && suggestions.q === text.trim()
  const options = current ? suggestions.items.filter((s) => !chips.some((c) => c.id === s.id)) : []
  const open = options.length > 0
  const full = chips.length >= MAX_INGREDIENTS

  const add = (option: IngredientOption) => {
    if (!full) setChips([...chips, option])
    setText('')
    setSuggestions(NO_SUGGESTIONS)
  }

  const remove = (id: number) => setChips(chips.filter((c) => c.id !== id))

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    // 한글 조합 중 Enter는 조합 확정용이라 무시한다
    if (e.nativeEvent.isComposing) return
    if (e.key === 'ArrowDown' && open) {
      e.preventDefault()
      setActive((active + 1) % options.length)
    } else if (e.key === 'ArrowUp' && open) {
      e.preventDefault()
      setActive((active - 1 + options.length) % options.length)
    } else if (e.key === 'Enter' && open) {
      e.preventDefault()
      add(options[Math.max(active, 0)])
    } else if (e.key === 'Escape') {
      setSuggestions(NO_SUGGESTIONS)
    } else if (e.key === 'Backspace' && !text && chips.length > 0) {
      remove(chips[chips.length - 1].id)
    }
  }

  return (
    <form
      className="ingredient-search"
      role="search"
      onSubmit={(e) => {
        e.preventDefault()
        if (chips.length > 0) onSearch(chips)
      }}
    >
      {/* shortcut: 아이콘은 시안의 텍스트 글리프를 그대로 쓴다, 아이콘 세트를 정하면 교체 */}
      <span className="ingredient-search__icon" aria-hidden="true">
        🔍
      </span>
      {chips.map((chip) => (
        <span key={chip.id} className="ingredient-search__chip">
          {chip.name}
          <button type="button" aria-label={`${chip.name} 빼기`} onClick={() => remove(chip.id)}>
            ×
          </button>
        </span>
      ))}
      <input
        className="ingredient-search__input"
        role="combobox"
        aria-label="재료 입력"
        aria-autocomplete="list"
        aria-expanded={open}
        aria-controls={listId}
        aria-activedescendant={open && active >= 0 ? `${listId}-${active}` : undefined}
        placeholder={full ? `재료는 ${MAX_INGREDIENTS}개까지 넣을 수 있어요` : '가진 재료를 입력하세요'}
        disabled={full}
        value={text}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={onKeyDown}
        onBlur={() => setSuggestions(NO_SUGGESTIONS)}
      />
      <button type="submit" className="ingredient-search__submit" disabled={chips.length === 0}>
        검색
      </button>
      <ul id={listId} role="listbox" className="ingredient-search__options" hidden={!open}>
        {options.map((option, i) => (
          <li
            key={option.id}
            id={`${listId}-${i}`}
            role="option"
            aria-selected={i === active}
            className="ingredient-search__option"
            // blur보다 먼저 고르도록 mousedown 기본 동작(포커스 이동)을 막는다
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => add(option)}
          >
            {option.name}
          </li>
        ))}
      </ul>
    </form>
  )
}
