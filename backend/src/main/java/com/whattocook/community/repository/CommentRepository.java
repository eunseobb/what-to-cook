package com.whattocook.community.repository;

import com.whattocook.community.domain.Comment;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * 레시피의 최상위 댓글을 커서(id) 이후부터 오래된 순으로 조회한다. 작성자를 함께 가져온다.
     * 삭제·숨김 댓글은 살아 있는(삭제·숨김되지 않은) 대댓글이 있을 때만 포함한다("삭제된 댓글"/"숨겨진 댓글"로 표시).
     */
    @Query("""
            select c from Comment c join fetch c.user
            where c.recipe.id = :recipeId and c.parent is null and c.id > :cursorId
              and ((c.deleted = false and c.hidden = false)
                   or exists (select 1 from Comment r where r.parent = c and r.deleted = false and r.hidden = false))
            order by c.id asc""")
    List<Comment> findRootPage(@Param("recipeId") Long recipeId, @Param("cursorId") Long cursorId, Limit limit);

    /** 최상위 댓글들의 살아 있는(삭제·숨김되지 않은) 대댓글을 한 번에 조회한다(N+1 방지). */
    @Query("""
            select c from Comment c join fetch c.user
            where c.parent.id in :parentIds and c.deleted = false and c.hidden = false
            order by c.id asc""")
    List<Comment> findRepliesByParentIds(@Param("parentIds") Collection<Long> parentIds);

    /** 행 잠금(SELECT ... FOR UPDATE)으로 조회한다. 같은 댓글 신고를 직렬화해 누적 수를 정확히 센다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Comment c where c.id = :id")
    Optional<Comment> findByIdForUpdate(@Param("id") Long id);
}
