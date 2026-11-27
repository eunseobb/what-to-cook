import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router'
import type { Difficulty, TagResponse } from '../../api/recipe'
import {
  createRecipe,
  getRecipe,
  listTags,
  updateRecipe,
  uploadImage,
  type RecipeDetail,
  type RecipeWriteRequest,
} from '../../api/recipeEditor'
import { ImageFileButton } from './ImageFileButton'
import { IngredientRow, type IngredientDraft } from './IngredientRow'
import { StepEditor, type StepDraft } from './StepEditor'
import './recipe-editor.css'

// 대표 사진과 단계 사진을 합친 한 레시피의 최대 사진 수
const MAX_IMAGES = 10
const MAX_TAGS = 10
const DIFFICULTIES: { value: Difficulty; label: string }[] = [
  { value: 'EASY', label: '쉬움' },
  { value: 'NORMAL', label: '보통' },
  { value: 'HARD', label: '어려움' },
]

interface Form {
  title: string
  mainImageUrl: string | null
  tagIds: number[]
  difficulty: Difficulty | null
  cookTime: string
  servings: string
  ingredients: IngredientDraft[]
  steps: StepDraft[]
}

let nextKey = 0
const newIngredient = (): IngredientDraft => ({ key: nextKey++, ingredientId: null, name: '', quantity: '', unit: '' })
const newStep = (): StepDraft => ({ key: nextKey++, content: '', imageUrl: null })

const emptyForm = (): Form => ({
  title: '',
  mainImageUrl: null,
  tagIds: [],
  difficulty: null,
  cookTime: '',
  servings: '',
  ingredients: [newIngredient()],
  steps: [newStep()],
})

const fromDetail = (r: RecipeDetail): Form => ({
  title: r.title,
  mainImageUrl: r.mainImageUrl,
  tagIds: r.tags.map((t) => t.id),
  difficulty: r.difficulty,
  cookTime: String(r.cookTimeMinutes),
  servings: String(r.servings),
  ingredients: r.ingredients.map((i) => ({
    key: nextKey++,
    ingredientId: i.ingredientId,
    name: i.name,
    quantity: i.quantity === null ? '' : String(i.quantity),
    unit: i.unit ?? '',
  })),
  steps: r.steps.map((s) => ({ key: nextKey++, content: s.content, imageUrl: s.imageUrl })),
})

function intInRange(text: string, min: number, max: number) {
  const n = Number(text)
  return Number.isInteger(n) && n >= min && n <= max ? n : null
}

// 폼을 API 요청으로 바꾼다. 내용이 하나도 없는 재료·단계 줄은 빼고, 문제가 있으면 첫 오류 문구를 돌려준다.
function toRequest(form: Form): RecipeWriteRequest | string {
  const title = form.title.trim()
  if (!title) return '제목을 입력해 주세요.'
  if (!form.difficulty) return '난이도를 골라 주세요.'
  const cookTimeMinutes = intInRange(form.cookTime, 1, 1440)
  if (cookTimeMinutes === null) return '조리시간은 1~1440분 사이 정수로 입력해 주세요.'
  const servings = intInRange(form.servings, 1, 100)
  if (servings === null) return '분량은 1~100인분 사이 정수로 입력해 주세요.'

  const ingredients = form.ingredients.filter((i) => i.name.trim() || i.quantity.trim())
  if (ingredients.length === 0) return '재료를 하나 이상 입력해 주세요.'
  if (ingredients.some((i) => i.ingredientId === null)) return '목록에 없는 재료가 있어요. 자동완성에서 골라 주세요.'
  // 수량은 비워도 된다("약간"처럼 정량이 없는 경우). 적었다면 소수 둘째 자리까지의 양수여야 한다.
  if (ingredients.some((i) => i.quantity.trim() && !/^\d{1,8}(\.\d{1,2})?$/.test(i.quantity.trim())))
    return '수량은 소수 둘째 자리까지의 숫자로 입력해 주세요.'
  if (ingredients.some((i) => i.quantity.trim() && Number(i.quantity) <= 0)) return '수량은 0보다 커야 해요.'

  const steps = form.steps.filter((s) => s.content.trim() || s.imageUrl)
  if (steps.length === 0) return '조리 순서를 하나 이상 입력해 주세요.'
  if (steps.some((s) => !s.content.trim())) return '사진만 있는 단계가 있어요. 설명을 적어 주세요.'

  return {
    title,
    mainImageUrl: form.mainImageUrl,
    difficulty: form.difficulty,
    cookTimeMinutes,
    servings,
    ingredients: ingredients.map((i) => ({
      ingredientId: i.ingredientId!,
      quantity: i.quantity.trim() ? Number(i.quantity) : null,
      unit: i.unit || null,
    })),
    steps: steps.map((s) => ({ content: s.content.trim(), imageUrl: s.imageUrl })),
    tagIds: form.tagIds,
  }
}

function move<T>(list: T[], from: number, to: number) {
  const next = [...list]
  next.splice(to, 0, next.splice(from, 1)[0])
  return next
}

// 레시피 작성(/recipes/new), AI 레시피 불러와 작성(/recipes/new?from=<AI 레시피 id>), 수정(/recipes/:recipeId/edit)
export function RecipeEditorPage() {
  const { recipeId } = useParams()
  const [searchParams] = useSearchParams()
  const aiRecipeId = recipeId ? null : searchParams.get('from')
  const sourceId = recipeId ?? aiRecipeId
  const navigate = useNavigate()
  const location = useLocation()

  const [form, setForm] = useState<Form | null>(sourceId ? null : emptyForm)
  const [tags, setTags] = useState<TagResponse[]>([])
  const [loadError, setLoadError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [uploading, setUploading] = useState(0)
  const [submitting, setSubmitting] = useState(false)
  const errorRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    listTags()
      .then(setTags)
      .catch(() => setTags([]))
  }, [])

  useEffect(() => {
    if (!sourceId) return
    let ignore = false
    getRecipe(Number(sourceId))
      .then((recipe) => {
        if (ignore) return
        if (aiRecipeId && recipe.source !== 'AI_GENERATED') setLoadError('AI 레시피만 불러올 수 있어요.')
        else setForm(fromDetail(recipe))
      })
      .catch((e: Error) => !ignore && setLoadError(e.message))
    return () => {
      ignore = true
    }
  }, [sourceId, aiRecipeId])

  useEffect(() => {
    if (error) errorRef.current?.focus()
  }, [error])

  const title = recipeId ? '레시피 수정' : '레시피 올리기'
  const submitLabel = recipeId ? '저장' : '올리기'

  function cancel() {
    // 바로 이 화면으로 들어왔으면 돌아갈 곳이 없으므로 홈으로 보낸다
    if (location.key === 'default') navigate('/')
    else navigate(-1)
  }

  if (!form) {
    return (
      <div className="recipe-editor">
        {loadError ? (
          <p className="editor-error" role="alert">
            {loadError}
          </p>
        ) : (
          <p className="recipe-editor__loading" aria-busy="true">
            레시피를 불러오는 중이에요.
          </p>
        )}
      </div>
    )
  }

  const update = (patch: Partial<Form>) => setForm({ ...form, ...patch })
  const imageCount = (form.mainImageUrl ? 1 : 0) + form.steps.filter((s) => s.imageUrl).length
  // 올리는 중인 사진도 세어 동시에 여러 장을 골라 한도를 넘지 않게 한다
  const canAddImage = imageCount + uploading < MAX_IMAGES

  async function upload(file: File) {
    setError(null)
    setUploading((n) => n + 1)
    try {
      return await uploadImage(file)
    } catch (e) {
      setError((e as Error).message)
      return null
    } finally {
      setUploading((n) => n - 1)
    }
  }

  // 업로드가 끝날 때 폼이 바뀌어 있을 수 있으므로 최신 상태에 반영한다
  async function uploadMain(file: File) {
    const imageUrl = await upload(file)
    if (imageUrl) setForm((f) => f && { ...f, mainImageUrl: imageUrl })
  }

  // 단계 사진 업로드가 끝날 때 순서가 바뀌어 있을 수 있으므로 위치가 아니라 key로 찾는다
  function setStep(key: number, patch: Partial<StepDraft>) {
    setForm((f) => f && { ...f, steps: f.steps.map((s) => (s.key === key ? { ...s, ...patch } : s)) })
  }

  function toggleTag(id: number) {
    const selected = form!.tagIds.includes(id)
    if (!selected && form!.tagIds.length >= MAX_TAGS) return setError(`태그는 ${MAX_TAGS}개까지 고를 수 있어요.`)
    update({ tagIds: selected ? form!.tagIds.filter((t) => t !== id) : [...form!.tagIds, id] })
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const request = toRequest(form!)
    if (typeof request === 'string') return setError(request)
    setError(null)
    setSubmitting(true)
    try {
      const id = recipeId ? (await updateRecipe(Number(recipeId), request), Number(recipeId)) : await createRecipe(request)
      navigate(`/recipes/${id}`, { replace: true })
    } catch (err) {
      setError((err as Error).message)
      setSubmitting(false)
    }
  }

  return (
    <div className="recipe-editor">
      <div className="recipe-editor__top-bar">
        {/* shortcut: 아이콘은 시안의 텍스트 글리프를 그대로 쓴다, 아이콘 세트를 정하면 교체 */}
        <button type="button" className="recipe-editor__back" onClick={cancel}>
          <span aria-hidden="true">‹</span> 취소
        </button>
        <span className="recipe-editor__top-title">{title}</span>
      </div>

      <form className="recipe-editor__form" onSubmit={handleSubmit} noValidate>
        <div className="recipe-editor__heading">
          <h1 className="recipe-editor__title">{title}</h1>
          <p className="recipe-editor__desc">내가 만든 요리를 다른 사람과 나눠 보세요.</p>
        </div>

        {aiRecipeId && (
          <div className="recipe-editor__ai-banner">
            <span className="recipe-editor__ai-badge">AI 생성</span>
            <p>AI가 만든 레시피를 불러왔어요. 직접 만들어 본 대로 고쳐서 올려 주세요.</p>
          </div>
        )}

        <div className="recipe-editor__basic">
          <div className="editor-field">
            <div className="editor-field__label">
              <span className="editor-field__name">사진</span>
              <span className="editor-field__hint">
                {imageCount} / {MAX_IMAGES}장 · 대표 사진과 단계 사진 합계
              </span>
            </div>
            <div className="recipe-editor__photos">
              {form.mainImageUrl ? (
                <div className="recipe-editor__photo">
                  <img src={form.mainImageUrl} alt="대표 사진" />
                  <span className="recipe-editor__photo-badge">대표</span>
                  <div className="recipe-editor__photo-actions">
                    <ImageFileButton className="editor-small-button" onFile={uploadMain}>
                      사진 바꾸기
                    </ImageFileButton>
                    <button type="button" className="editor-small-button" onClick={() => update({ mainImageUrl: null })}>
                      삭제
                    </button>
                  </div>
                </div>
              ) : (
                <ImageFileButton className="recipe-editor__photo-add" disabled={!canAddImage} onFile={uploadMain}>
                  <span className="recipe-editor__photo-plus" aria-hidden="true">
                    +
                  </span>
                  사진 추가
                </ImageFileButton>
              )}
            </div>
          </div>

          <div className="editor-field">
            <label className="editor-field__label" htmlFor="recipe-title">
              <span className="editor-field__name">제목</span>
            </label>
            <input
              id="recipe-title"
              className="editor-input"
              placeholder="예: 김치 참치 볶음밥"
              maxLength={100}
              value={form.title}
              onChange={(e) => update({ title: e.target.value })}
            />
          </div>

          <fieldset className="editor-field">
            <legend className="editor-field__label">
              <span className="editor-field__name">태그</span>
              <span className="editor-field__hint">여러 개 선택</span>
            </legend>
            <div className="editor-chips">
              {tags.map((tag) => (
                <button
                  key={tag.id}
                  type="button"
                  className="editor-chip"
                  aria-pressed={form.tagIds.includes(tag.id)}
                  onClick={() => toggleTag(tag.id)}
                >
                  {tag.name}
                </button>
              ))}
            </div>
          </fieldset>

          <div className="recipe-editor__meta">
            <fieldset className="editor-field">
              <legend className="editor-field__label">
                <span className="editor-field__name">난이도</span>
              </legend>
              <div className="editor-chips recipe-editor__difficulty">
                {DIFFICULTIES.map(({ value, label }) => (
                  <button
                    key={value}
                    type="button"
                    className="editor-chip"
                    aria-pressed={form.difficulty === value}
                    onClick={() => update({ difficulty: value })}
                  >
                    {label}
                  </button>
                ))}
              </div>
            </fieldset>
            <div className="editor-field">
              <label className="editor-field__label" htmlFor="recipe-cook-time">
                <span className="editor-field__name">조리시간</span>
              </label>
              <div className="editor-input editor-input--suffix">
                <input
                  id="recipe-cook-time"
                  inputMode="numeric"
                  value={form.cookTime}
                  onChange={(e) => update({ cookTime: e.target.value })}
                />
                <span aria-hidden="true">분</span>
              </div>
            </div>
            <div className="editor-field">
              <label className="editor-field__label" htmlFor="recipe-servings">
                <span className="editor-field__name">분량</span>
              </label>
              <div className="editor-input editor-input--suffix">
                <input
                  id="recipe-servings"
                  inputMode="numeric"
                  value={form.servings}
                  onChange={(e) => update({ servings: e.target.value })}
                />
                <span aria-hidden="true">인분</span>
              </div>
            </div>
          </div>
        </div>

        <section className="recipe-editor__section" aria-labelledby="recipe-ingredients">
          <div className="recipe-editor__section-heading">
            <h2 id="recipe-ingredients">재료</h2>
            <span className="editor-field__hint">목록에 있는 재료를 골라 주세요</span>
          </div>
          <div className="recipe-editor__rows">
            {form.ingredients.map((ingredient, i) => (
              <IngredientRow
                key={ingredient.key}
                value={ingredient}
                onChange={(value) => update({ ingredients: form.ingredients.map((x, j) => (j === i ? value : x)) })}
                onRemove={() =>
                  update({
                    ingredients:
                      form.ingredients.length === 1 ? [newIngredient()] : form.ingredients.filter((_, j) => j !== i),
                  })
                }
              />
            ))}
          </div>
          <button
            type="button"
            className="editor-add-button"
            disabled={form.ingredients.length >= 50}
            onClick={() => update({ ingredients: [...form.ingredients, newIngredient()] })}
          >
            + 재료 추가
          </button>
        </section>

        <section className="recipe-editor__section" aria-labelledby="recipe-steps">
          <div className="recipe-editor__section-heading">
            <h2 id="recipe-steps">조리 순서</h2>
          </div>
          <ol className="recipe-editor__steps">
            {form.steps.map((step, i) => (
              <StepEditor
                key={step.key}
                index={i}
                count={form.steps.length}
                value={step}
                canAddImage={canAddImage}
                onChange={(patch) => setStep(step.key, patch)}
                onUpload={upload}
                onMove={(to) => update({ steps: move(form.steps, i, to) })}
                onRemove={() => update({ steps: form.steps.filter((_, j) => j !== i) })}
              />
            ))}
          </ol>
          <button
            type="button"
            className="editor-add-button"
            disabled={form.steps.length >= 50}
            onClick={() => update({ steps: [...form.steps, newStep()] })}
          >
            + 단계 추가
          </button>
        </section>

        {error && (
          <p className="editor-error" role="alert" tabIndex={-1} ref={errorRef}>
            {error}
          </p>
        )}

        <div className="recipe-editor__actions">
          <button type="button" className="recipe-editor__cancel" onClick={cancel}>
            취소
          </button>
          <button type="submit" className="recipe-editor__submit" disabled={submitting || uploading > 0}>
            {uploading > 0 ? '사진 올리는 중' : submitLabel}
          </button>
        </div>
      </form>
    </div>
  )
}
