package com.whattocook.recipe.domain;

import com.whattocook.global.common.BaseTimeEntity;
import com.whattocook.user.domain.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 레시피. 커뮤니티 레시피와 AI 생성 레시피를 source로 구분해 한 테이블에 둔다.
 * 재료·단계는 레시피에 딸린 값 컬렉션이고, 태그는 태그 마스터를 recipe_tag로 참조한다.
 */
@Getter
@Entity
@Table(name = "recipe")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recipe extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** AI 생성 레시피는 작성자가 없을 수 있다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecipeSource source;

    /** AI 레시피 재사용 키(정규화·정렬한 재료 집합의 해시). 커뮤니티 레시피는 비워 둔다. */
    @Column(name = "ingredient_set_hash", length = 64)
    private String ingredientSetHash;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "main_image_url", length = 500)
    private String mainImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Difficulty difficulty;

    @Column(name = "cook_time_minutes", nullable = false)
    private int cookTimeMinutes;

    @Column(nullable = false)
    private int servings;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecipeStatus status;

    @ElementCollection
    @CollectionTable(name = "recipe_ingredient", joinColumns = @JoinColumn(name = "recipe_id"))
    @OrderColumn(name = "ingredient_order")
    private List<RecipeIngredient> ingredients = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "recipe_step", joinColumns = @JoinColumn(name = "recipe_id"))
    @OrderColumn(name = "step_order")
    private List<RecipeStep> steps = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "recipe_tag",
            joinColumns = @JoinColumn(name = "recipe_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new HashSet<>();

    @Builder
    private Recipe(User author, RecipeSource source, String ingredientSetHash, String title, String mainImageUrl,
                   Difficulty difficulty, int cookTimeMinutes, int servings,
                   Collection<RecipeIngredient> ingredients, Collection<RecipeStep> steps, Collection<Tag> tags) {
        this.author = author;
        this.source = source;
        this.ingredientSetHash = ingredientSetHash;
        this.title = title.strip();
        this.mainImageUrl = mainImageUrl;
        this.difficulty = difficulty;
        this.cookTimeMinutes = cookTimeMinutes;
        this.servings = servings;
        this.status = RecipeStatus.PUBLISHED;
        replaceIngredients(ingredients);
        replaceSteps(steps);
        replaceTags(tags);
    }

    /**
     * 카드에 보일 대표 태그 1개. TagType 선언 순서(음식 종류 → 맛 → 목적 → 난이도)로 고르고,
     * 같은 종류끼리는 id가 작은(먼저 등록된) 태그를 쓴다. 태그가 없으면 null.
     */
    public Tag getRepresentativeTag() {
        return tags.stream()
                .min(Comparator.comparing(Tag::getType)
                        .thenComparing(Tag::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    public boolean isWrittenBy(Long userId) {
        return author != null && Objects.equals(author.getId(), userId);
    }

    public void changeTitle(String title) {
        this.title = title.strip();
    }

    public void changeMainImageUrl(String mainImageUrl) {
        this.mainImageUrl = mainImageUrl;
    }

    public void changeDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public void changeCookTimeMinutes(int cookTimeMinutes) {
        this.cookTimeMinutes = cookTimeMinutes;
    }

    public void changeServings(int servings) {
        this.servings = servings;
    }

    public void replaceIngredients(Collection<RecipeIngredient> ingredients) {
        this.ingredients.clear();
        if (ingredients != null) {
            this.ingredients.addAll(ingredients);
        }
    }

    public void replaceSteps(Collection<RecipeStep> steps) {
        this.steps.clear();
        if (steps != null) {
            this.steps.addAll(steps);
        }
    }

    public void replaceTags(Collection<Tag> tags) {
        this.tags.clear();
        if (tags != null) {
            this.tags.addAll(tags);
        }
    }

    /** 비관적 락 방식 좋아요에서 행 락을 잡은 뒤 호출한다. */
    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        this.likeCount = Math.max(0, this.likeCount - 1);
    }

    /** 소프트 삭제. 좋아요·댓글 등 이후 연관 데이터를 보존하기 위해 행을 지우지 않는다. */
    public void delete() {
        this.status = RecipeStatus.DELETED;
    }

    /** 신고 누적으로 숨김. 게시 중인 레시피만 조회되므로 목록·상세·댓글에서 빠진다. */
    public void hide() {
        this.status = RecipeStatus.HIDDEN;
    }
}
