// 좋아요·저장·댓글·신고 API.
// shortcut: 손으로 맞춘 타입이다. schema.d.ts가 OpenAPI로 생성되면(npm run gen:api) components['schemas']로 바꾼다.

import type { Client } from 'openapi-fetch'
import { api, throwIfFailed } from './client'
import type { CursorPage } from './recipe'

/** 백엔드 CommentResponse. 삭제·숨김된 댓글은 content·작성자가 가려져 내려온다. */
export interface CommentItem {
  id: number
  content: string
  authorId: number | null
  authorNickname: string | null
  deleted: boolean
  hidden: boolean
  createdAt: string
  /** 최상위 댓글만 대댓글을 담는다 */
  replies: CommentItem[]
}

export type ReportTargetType = 'RECIPE' | 'COMMENT'

type RecipePath = { path: { recipeId: number } }
type NoContent = { 204: { content?: never } }

type CommunityPaths = {
  '/api/recipes/{recipeId}/likes': {
    post: { parameters: RecipePath; responses: NoContent }
    delete: { parameters: RecipePath; responses: NoContent }
  }
  '/api/recipes/{recipeId}/bookmarks': {
    post: { parameters: RecipePath; responses: NoContent }
    delete: { parameters: RecipePath; responses: NoContent }
  }
  '/api/recipes/{recipeId}/comments': {
    get: {
      parameters: RecipePath & { query?: { cursor?: string; size?: number } }
      responses: { 200: { content: { 'application/json': CursorPage<CommentItem> } } }
    }
    post: {
      parameters: RecipePath
      requestBody: { content: { 'application/json': { content: string; parentId?: number } } }
      responses: { 201: { content: { 'application/json': { commentId: number } } } }
    }
  }
  '/api/reports': {
    post: {
      parameters: object
      requestBody: { content: { 'application/json': { targetType: ReportTargetType; targetId: number } } }
      responses: { 201: { content: { 'application/json': { reportId: number } } } }
    }
  }
}

const client = api as unknown as Client<CommunityPaths>

/** 좋아요(on=true) 또는 취소. 이미 그 상태면 ApiError(409 L001 / 404 L002)를 던진다. */
export async function setLike(recipeId: number, on: boolean) {
  const params = { params: { path: { recipeId } } }
  const { error, response } = on
    ? await client.POST('/api/recipes/{recipeId}/likes', params)
    : await client.DELETE('/api/recipes/{recipeId}/likes', params)
  throwIfFailed(response, error, '좋아요를 처리하지 못했어요')
}

/** 저장(on=true) 또는 해제. 서버가 멱등으로 처리한다. */
export async function setBookmark(recipeId: number, on: boolean) {
  const params = { params: { path: { recipeId } } }
  const { error, response } = on
    ? await client.POST('/api/recipes/{recipeId}/bookmarks', params)
    : await client.DELETE('/api/recipes/{recipeId}/bookmarks', params)
  throwIfFailed(response, error, '저장을 처리하지 못했어요')
}

/** 최상위 댓글을 오래된 순으로 커서 페이지 조회한다. 대댓글은 replies에 붙어 온다. */
export async function fetchComments(recipeId: number, cursor?: string): Promise<CursorPage<CommentItem>> {
  const { data, error, response } = await client.GET('/api/recipes/{recipeId}/comments', {
    params: { path: { recipeId }, query: { cursor, size: 20 } },
  })
  throwIfFailed(response, error, '댓글을 불러오지 못했어요')
  return data!
}

/** parentId가 있으면 대댓글. */
export async function createComment(recipeId: number, content: string, parentId?: number) {
  const { error, response } = await client.POST('/api/recipes/{recipeId}/comments', {
    params: { path: { recipeId } },
    body: { content, parentId },
  })
  throwIfFailed(response, error, '댓글을 등록하지 못했어요')
}

export async function report(targetType: ReportTargetType, targetId: number) {
  const { error, response } = await client.POST('/api/reports', { body: { targetType, targetId } })
  throwIfFailed(response, error, '신고하지 못했어요')
}
