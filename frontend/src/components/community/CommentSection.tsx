import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/client'
import { createComment, fetchComments, type CommentItem } from '../../api/community'
import type { CursorPage } from '../../api/recipe'
import { ReportButton } from './ReportButton'
import './community.css'

const relativeTime = new Intl.RelativeTimeFormat('ko', { numeric: 'always' })
const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 31_536_000],
  ['month', 2_592_000],
  ['day', 86_400],
  ['hour', 3_600],
  ['minute', 60],
]

/** "2일 전", "5시간 전"처럼 보여준다. 1분 미만은 "방금 전". */
function timeAgo(iso: string) {
  const seconds = (Date.parse(iso) - Date.now()) / 1000
  for (const [unit, size] of UNITS) {
    if (Math.abs(seconds) >= size) return relativeTime.format(Math.trunc(seconds / size), unit)
  }
  return '방금 전'
}

/** 댓글 입력창. 등록에 성공하면 비운다. */
function CommentForm({ placeholder, onSubmit }: { placeholder: string; onSubmit: (content: string) => Promise<boolean> }) {
  const [content, setContent] = useState('')
  const [sending, setSending] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const trimmed = content.trim()
    if (!trimmed) return
    setSending(true)
    if (await onSubmit(trimmed)) setContent('')
    setSending(false)
  }

  return (
    <form className="comment-form" onSubmit={handleSubmit}>
      <input
        className="comment-form__field"
        value={content}
        onChange={(e) => setContent(e.target.value)}
        placeholder={placeholder}
        aria-label={placeholder}
        maxLength={500}
      />
      <button type="submit" className="comment-form__submit" disabled={sending || !content.trim()}>
        등록
      </button>
    </form>
  )
}

interface CommentRowProps {
  comment: CommentItem
  reply?: boolean
  onReply?: () => void
}

function CommentRow({ comment, reply = false, onReply }: CommentRowProps) {
  const className = `comment${reply ? ' comment--reply' : ''}`
  if (comment.deleted || comment.hidden) {
    return (
      <li className={className}>
        <p className="comment__removed">{comment.deleted ? '삭제된 댓글입니다.' : '숨겨진 댓글입니다.'}</p>
      </li>
    )
  }
  return (
    <li className={className}>
      <p className="comment__head">
        {reply && (
          <span className="comment__arrow" aria-hidden="true">
            ↳
          </span>
        )}
        <strong className="comment__author">{comment.authorNickname}</strong>
        <time className="comment__time" dateTime={comment.createdAt}>
          {timeAgo(comment.createdAt)}
        </time>
      </p>
      <p className="comment__content">{comment.content}</p>
      <p className="comment__actions">
        {onReply && (
          <>
            <button type="button" onClick={onReply}>
              답글 달기
            </button>
            {' · '}
          </>
        )}
        <ReportButton targetType="COMMENT" targetId={comment.id} />
      </p>
    </li>
  )
}

/** 댓글·대댓글(1단계) 목록과 입력. 최상위 댓글을 오래된 순으로 커서 페이지네이션한다. */
export function CommentSection({ recipeId }: { recipeId: number }) {
  const [comments, setComments] = useState<CommentItem[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [replyTo, setReplyTo] = useState<number | null>(null)

  const applyPage = useCallback((page: CursorPage<CommentItem>, append: boolean) => {
    setComments((prev) => (append ? [...prev, ...page.items] : page.items))
    setNextCursor(page.hasNext ? page.nextCursor : null)
    setError(null)
  }, [])

  /** cursor가 없으면 처음부터 다시 불러온다(등록 직후 새 댓글을 보이게 할 때). */
  async function load(cursor?: string) {
    setLoading(true)
    try {
      applyPage(await fetchComments(recipeId, cursor), cursor !== undefined)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let ignore = false
    fetchComments(recipeId)
      .then((page) => !ignore && applyPage(page, false))
      .catch((e: unknown) => !ignore && setError(errorMessage(e)))
      .finally(() => !ignore && setLoading(false))
    return () => {
      ignore = true
    }
  }, [recipeId, applyPage])

  async function submit(content: string, parentId?: number) {
    try {
      await createComment(recipeId, content, parentId)
      setReplyTo(null)
      // shortcut: 등록 후 첫 페이지부터 다시 불러온다, 댓글이 많아지면 새 댓글만 목록에 붙인다
      await load()
      return true
    } catch (e) {
      window.alert(errorMessage(e))
      return false
    }
  }

  // shortcut: 목록 API에 전체 댓글 수가 없어 지금까지 불러온 댓글·대댓글 수를 보여준다
  const count = comments.reduce((sum, c) => sum + 1 + c.replies.length, 0)

  return (
    <section className="comments" aria-labelledby="comments-title">
      <h2 id="comments-title" className="comments__title">
        댓글 {count}
      </h2>
      <CommentForm placeholder="댓글을 남겨 주세요" onSubmit={(content) => submit(content)} />
      {error && (
        <p className="comments__error" role="alert">
          {error}
        </p>
      )}
      <ul className="comments__list" aria-busy={loading}>
        {comments.map((comment) => (
          <li key={comment.id} className="comments__thread">
            <ul>
              <CommentRow
                comment={comment}
                onReply={
                  comment.deleted || comment.hidden
                    ? undefined
                    : () => setReplyTo(replyTo === comment.id ? null : comment.id)
                }
              />
              {comment.replies.map((r) => (
                <CommentRow key={r.id} comment={r} reply />
              ))}
            </ul>
            {replyTo === comment.id && (
              <div className="comments__reply-form">
                <CommentForm placeholder="답글을 남겨 주세요" onSubmit={(content) => submit(content, comment.id)} />
              </div>
            )}
          </li>
        ))}
      </ul>
      {nextCursor && (
        <button type="button" className="comments__more" disabled={loading} onClick={() => load(nextCursor)}>
          댓글 더 보기
        </button>
      )}
    </section>
  )
}
