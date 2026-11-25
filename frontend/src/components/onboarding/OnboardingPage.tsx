import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { getTags, saveOnboarding, type Ingredient } from '../../api/onboarding'
import type { TagResponse, TagType } from '../../api/recipe'
import { IngredientPicker } from './IngredientPicker'
import './onboarding.css'

const MIN_TAGS = 3

const GROUPS: { type: TagType; label: string }[] = [
  { type: 'CUISINE', label: '음식 종류' },
  { type: 'TASTE', label: '맛' },
  { type: 'PURPOSE', label: '목적' },
  { type: 'DIFFICULTY', label: '난이도' },
]

// 2단계: 1) 선호 태그 3개 이상(건너뛸 수 없음) 2) 알레르기·비선호 재료(건너뛰기 가능)
export function OnboardingPage() {
  const navigate = useNavigate()
  const [step, setStep] = useState<1 | 2>(1)
  const [tags, setTags] = useState<TagResponse[] | null>(null)
  const [tagIds, setTagIds] = useState<number[]>([])
  const [allergies, setAllergies] = useState<Ingredient[]>([])
  const [dislikes, setDislikes] = useState<Ingredient[]>([])
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getTags()
      .then(setTags)
      .catch((e: Error) => setError(e.message))
  }, [])

  function toggleTag(id: number) {
    setTagIds((ids) => (ids.includes(id) ? ids.filter((t) => t !== id) : [...ids, id]))
  }

  async function submit(skip: boolean) {
    setSubmitting(true)
    setError(null)
    try {
      await saveOnboarding({
        tagIds,
        allergyIngredientIds: skip ? [] : allergies.map((i) => i.id),
        dislikeIngredientIds: skip ? [] : dislikes.map((i) => i.id),
      })
      navigate('/', { replace: true })
    } catch (e) {
      setError((e as Error).message)
      setSubmitting(false)
    }
  }

  // 한 재료는 알레르기·비선호 중 한 곳에만 들어간다(서버 DUPLICATE_EXCLUDED_INGREDIENT)
  const pickedIds = new Set([...allergies, ...dislikes].map((i) => i.id))
  const remaining = MIN_TAGS - tagIds.length

  return (
    <main className="onboarding">
      <div className="onboarding__top-bar">
        <div className="onboarding__top-row">
          <span className="onboarding__logo">냉장고 레시피</span>
          <span className="onboarding__step">{step} / 2 단계</span>
        </div>
        <div
          className="onboarding__progress"
          role="progressbar"
          aria-label="온보딩 진행"
          aria-valuemin={1}
          aria-valuemax={2}
          aria-valuenow={step}
        >
          <div className={`onboarding__progress-fill onboarding__progress-fill--step${step}`} />
        </div>
      </div>

      <div className="onboarding__content">
        {step === 1 ? (
          <>
            <div className="onboarding__heading">
              <h1 className="onboarding__title">어떤 요리를 좋아하세요?</h1>
              <p className="onboarding__desc">3개 이상 골라주세요. 내 취향 추천에 반영돼요.</p>
            </div>
            {tags === null && !error && <p className="onboarding__desc" aria-busy="true">불러오는 중…</p>}
            {tags &&
              GROUPS.map(({ type, label }) => {
                const group = tags.filter((t) => t.type === type)
                if (group.length === 0) return null
                return (
                  <fieldset key={type} className="onboarding__group">
                    <legend className="onboarding__group-title">{label}</legend>
                    <div className="onboarding__chips">
                      {group.map((tag) => {
                        const on = tagIds.includes(tag.id)
                        return (
                          <button
                            key={tag.id}
                            type="button"
                            className={`tag-chip${on ? ' tag-chip--selected' : ''}`}
                            aria-pressed={on}
                            onClick={() => toggleTag(tag.id)}
                          >
                            {on && <span aria-hidden="true">✓</span>}
                            {tag.name}
                          </button>
                        )
                      })}
                    </div>
                  </fieldset>
                )
              })}
          </>
        ) : (
          <>
            <div className="onboarding__heading">
              <h1 className="onboarding__title">피해야 할 재료가 있나요?</h1>
              <p className="onboarding__desc">알레르기 재료가 들어간 레시피는 추천에서 빼요.</p>
            </div>
            <IngredientPicker
              id="onboarding-allergy"
              label="알레르기"
              hint="이 재료가 들어간 레시피는 보이지 않아요."
              variant="allergy"
              selected={allergies}
              onChange={setAllergies}
              excludedIds={pickedIds}
            />
            <IngredientPicker
              id="onboarding-dislike"
              label="비선호"
              hint="추천 순서에서만 뒤로 밀려요."
              variant="dislike"
              selected={dislikes}
              onChange={setDislikes}
              excludedIds={pickedIds}
            />
          </>
        )}

        {error && (
          <p className="onboarding__error" role="alert">
            {error}
          </p>
        )}

        <div className="onboarding__footer">
          {step === 1 ? (
            <>
              <p className="onboarding__count" aria-live="polite">
                {tagIds.length}개 선택됨{remaining > 0 && ` · ${remaining}개 더 골라주세요`}
              </p>
              <button
                type="button"
                className="onboarding__primary"
                disabled={remaining > 0}
                onClick={() => {
                  setError(null)
                  setStep(2)
                }}
              >
                다음
              </button>
            </>
          ) : (
            <>
              <button type="button" className="onboarding__skip" disabled={submitting} onClick={() => submit(true)}>
                건너뛰기
              </button>
              <button type="button" className="onboarding__secondary" onClick={() => setStep(1)}>
                이전
              </button>
              <button
                type="button"
                className="onboarding__primary"
                disabled={submitting}
                onClick={() => submit(false)}
              >
                완료
              </button>
            </>
          )}
        </div>
      </div>
    </main>
  )
}
