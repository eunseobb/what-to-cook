import { ImageFileButton } from './ImageFileButton'

export interface StepDraft {
  key: number
  content: string
  imageUrl: string | null
}

interface StepEditorProps {
  index: number
  count: number
  value: StepDraft
  canAddImage: boolean
  /** 바뀐 필드만 넘긴다. 사진 업로드가 끝나기 전에 고친 설명을 덮어쓰지 않기 위해서다. */
  onChange: (patch: Partial<StepDraft>) => void
  onUpload: (file: File) => Promise<string | null>
  onMove: (to: number) => void
  onRemove: () => void
}

// 조리 단계 한 개: 설명, 단계 사진, 순서 바꾸기와 삭제
export function StepEditor({ index, count, value, canAddImage, onChange, onUpload, onMove, onRemove }: StepEditorProps) {
  async function handleFile(file: File) {
    const imageUrl = await onUpload(file)
    if (imageUrl) onChange({ imageUrl })
  }

  return (
    <li className="step-editor">
      <span className="step-editor__number" aria-hidden="true">
        {index + 1}
      </span>
      <div className="step-editor__body">
        <textarea
          className="editor-input step-editor__content"
          aria-label={`${index + 1}단계 설명`}
          placeholder="이 단계에서 할 일을 적어 주세요"
          maxLength={1000}
          rows={2}
          value={value.content}
          onChange={(e) => onChange({ content: e.target.value })}
        />
        <div className="step-editor__tools">
          {value.imageUrl ? (
            <>
              <img className="step-editor__photo" src={value.imageUrl} alt={`${index + 1}단계 사진`} />
              {/* 바꾸기는 장수가 늘지 않으므로 한도와 상관없이 허용한다 */}
              <ImageFileButton className="editor-small-button" onFile={handleFile}>
                사진 바꾸기
              </ImageFileButton>
              <button type="button" className="editor-small-button" onClick={() => onChange({ imageUrl: null })}>
                사진 삭제
              </button>
            </>
          ) : (
            <ImageFileButton className="editor-small-button" disabled={!canAddImage} onFile={handleFile}>
              + 사진
            </ImageFileButton>
          )}
          <span className="step-editor__actions">
            <button type="button" className="editor-text-button" disabled={index === 0} onClick={() => onMove(index - 1)}>
              위로
            </button>
            <span aria-hidden="true">·</span>
            <button
              type="button"
              className="editor-text-button"
              disabled={index === count - 1}
              onClick={() => onMove(index + 1)}
            >
              아래로
            </button>
            <span aria-hidden="true">·</span>
            <button type="button" className="editor-text-button" disabled={count === 1} onClick={onRemove}>
              삭제
            </button>
          </span>
        </div>
      </div>
    </li>
  )
}
