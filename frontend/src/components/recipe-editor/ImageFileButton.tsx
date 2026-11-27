import type { ReactNode } from 'react'

interface ImageFileButtonProps {
  className: string
  disabled?: boolean
  onFile: (file: File) => void
  children: ReactNode
}

// 파일 선택 버튼. 백엔드가 허용하는 이미지 형식만 고르게 한다(크기 등은 업로드 URL 발급에서 검증).
export function ImageFileButton({ className, disabled, onFile, children }: ImageFileButtonProps) {
  return (
    <label className={className} aria-disabled={disabled || undefined}>
      <input
        type="file"
        className="editor-file-input"
        accept="image/jpeg,image/png,image/webp"
        disabled={disabled}
        onChange={(e) => {
          const file = e.target.files?.[0]
          // 같은 파일을 다시 고를 수 있게 비운다
          e.target.value = ''
          if (file) onFile(file)
        }}
      />
      {children}
    </label>
  )
}
