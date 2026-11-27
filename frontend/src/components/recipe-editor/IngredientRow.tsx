import { useEffect, useId, useState, type KeyboardEvent } from 'react'
import { autocompleteIngredients, type IngredientOption } from '../../api/recipeEditor'

export interface IngredientDraft {
  key: number
  ingredientId: number | null
  name: string
  quantity: string
  unit: string
}

// shortcut: 자주 쓰는 단위를 고정 목록으로 둔다, 재료별 단위 환산표(F4)를 화면에 내리면 그 목록으로 바꾼다
const UNITS = ['g', 'kg', 'ml', 'L', '개', '대', '줌', '쪽', '장', '모', '컵', '공기', '캔', '큰술', '작은술', '약간']

interface IngredientRowProps {
  value: IngredientDraft
  onChange: (value: IngredientDraft) => void
  onRemove: () => void
}

// 재료 한 줄: 표준 재료 자동완성(이름), 수량, 단위. 자동완성에서 고르지 않은 이름은 저장할 수 없다.
export function IngredientRow({ value, onChange, onRemove }: IngredientRowProps) {
  const listId = useId()
  const [focused, setFocused] = useState(false)
  const [options, setOptions] = useState<IngredientOption[]>([])
  const [active, setActive] = useState(0)
  const query = value.ingredientId ? '' : value.name.trim()

  useEffect(() => {
    if (!query) return
    const controller = new AbortController()
    const timer = setTimeout(() => {
      autocompleteIngredients(query, controller.signal)
        .then((result) => {
          setOptions(result)
          setActive(0)
        })
        .catch(() => setOptions([]))
    }, 200)
    return () => {
      clearTimeout(timer)
      controller.abort()
    }
  }, [query])

  const open = focused && query !== '' && options.length > 0
  const unmatched = !focused && value.name.trim() !== '' && value.ingredientId === null

  function select(option: IngredientOption) {
    onChange({ ...value, ingredientId: option.id, name: option.name })
    setOptions([])
  }

  function handleKeyDown(e: KeyboardEvent) {
    if (!open) return
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault()
      setActive((i) => (i + (e.key === 'ArrowDown' ? 1 : options.length - 1)) % options.length)
    } else if (e.key === 'Enter') {
      e.preventDefault()
      select(options[active])
    } else if (e.key === 'Escape') {
      setOptions([])
    }
  }

  function handleBlur() {
    setFocused(false)
    // 목록의 이름을 그대로 쳤으면 고른 것으로 본다
    const exact = options.find((o) => o.name === value.name.trim())
    if (!value.ingredientId && exact) select(exact)
  }

  const units = value.unit && !UNITS.includes(value.unit) ? [value.unit, ...UNITS] : UNITS

  return (
    <div className="ingredient-row">
      <div className="ingredient-row__fields">
        <div className="ingredient-row__name">
          <input
            className="editor-input"
            role="combobox"
            aria-label="재료"
            aria-expanded={open}
            aria-controls={listId}
            aria-activedescendant={open ? `${listId}-${active}` : undefined}
            aria-invalid={unmatched || undefined}
            aria-describedby={unmatched ? `${listId}-error` : undefined}
            autoComplete="off"
            placeholder="재료 이름"
            value={value.name}
            onChange={(e) => onChange({ ...value, ingredientId: null, name: e.target.value })}
            onFocus={() => setFocused(true)}
            onBlur={handleBlur}
            onKeyDown={handleKeyDown}
          />
          {open && (
            <ul id={listId} className="ingredient-row__options" role="listbox" aria-label="재료 후보">
              {options.map((option, i) => (
                <li
                  key={option.id}
                  id={`${listId}-${i}`}
                  role="option"
                  aria-selected={i === active}
                  className="ingredient-row__option"
                  // 입력창 blur보다 먼저 고르기 위해 mousedown을 쓴다
                  onMouseDown={(e) => {
                    e.preventDefault()
                    select(option)
                  }}
                >
                  {option.name}
                </li>
              ))}
            </ul>
          )}
        </div>
        <input
          className="editor-input ingredient-row__quantity"
          aria-label="수량"
          inputMode="decimal"
          placeholder="수량"
          value={value.quantity}
          onChange={(e) => onChange({ ...value, quantity: e.target.value })}
        />
        <select
          className="editor-input ingredient-row__unit"
          aria-label="단위"
          value={value.unit}
          data-empty={value.unit === '' || undefined}
          onChange={(e) => onChange({ ...value, unit: e.target.value })}
        >
          <option value="">단위</option>
          {units.map((unit) => (
            <option key={unit} value={unit}>
              {unit}
            </option>
          ))}
        </select>
        {/* shortcut: 아이콘은 시안의 텍스트 글리프를 그대로 쓴다, 아이콘 세트를 정하면 교체 */}
        <button type="button" className="editor-icon-button" aria-label="재료 삭제" onClick={onRemove}>
          <span aria-hidden="true">✕</span>
        </button>
      </div>
      {unmatched && (
        <p id={`${listId}-error`} className="editor-error-text">
          목록에 없는 재료예요. 비슷한 재료를 골라 주세요.
        </p>
      )}
    </div>
  )
}
