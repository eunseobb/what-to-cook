import { errorMessage } from '../../api/client'
import { report, type ReportTargetType } from '../../api/community'

interface ReportButtonProps {
  targetType: ReportTargetType
  targetId: number
  className?: string
}

const TARGET_LABEL: Record<ReportTargetType, string> = { RECIPE: '레시피', COMMENT: '댓글' }

/** 확인을 받은 뒤 레시피·댓글을 신고한다. 중복 신고·내 글 신고는 서버 메시지를 그대로 보여준다. */
export function ReportButton({ targetType, targetId, className }: ReportButtonProps) {
  async function handleClick() {
    if (!window.confirm(`이 ${TARGET_LABEL[targetType]}을(를) 신고할까요?`)) return
    try {
      await report(targetType, targetId)
      window.alert('신고가 접수됐어요.')
    } catch (e) {
      window.alert(errorMessage(e))
    }
  }

  return (
    <button type="button" className={className} onClick={handleClick}>
      신고
    </button>
  )
}
