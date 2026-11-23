package com.whattocook.community.domain;

import com.whattocook.global.common.BaseTimeEntity;
import com.whattocook.recipe.domain.Recipe;
import com.whattocook.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 레시피 댓글. parent가 있으면 대댓글이며, 대댓글은 1단계까지만 허용한다.
 */
@Getter
@Entity
@Table(name = "comment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** 최상위 댓글이면 null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(nullable = false)
    private boolean deleted;

    public Comment(Recipe recipe, User user, Comment parent, String content) {
        this.recipe = recipe;
        this.user = user;
        this.parent = parent;
        this.content = content.strip();
    }

    public boolean isReply() {
        return parent != null;
    }

    public boolean isWrittenBy(Long userId) {
        return Objects.equals(user.getId(), userId);
    }

    public void changeContent(String content) {
        this.content = content.strip();
    }

    /** 소프트 삭제. 대댓글을 보존하고 신고 처리 때 원문을 볼 수 있게 내용은 지우지 않는다. */
    public void delete() {
        this.deleted = true;
    }
}
