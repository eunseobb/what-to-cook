package com.whattocook.recipe.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RecipeTest {

    @Test
    @DisplayName("대표 태그는 음식 종류를 먼저 고르고, 같은 종류면 id가 작은 태그를 고른다")
    void representativeTag() {
        Tag spicy = tag(TagType.TASTE, "매운맛", 7L);
        Tag western = tag(TagType.CUISINE, "양식", 4L);
        Tag korean = tag(TagType.CUISINE, "한식", 1L);

        assertThat(recipe(List.of(spicy, western, korean)).getRepresentativeTag()).isSameAs(korean);
        assertThat(recipe(List.of(spicy, tag(TagType.DIFFICULTY, "초보", 17L))).getRepresentativeTag()).isSameAs(spicy);
        assertThat(recipe(List.of()).getRepresentativeTag()).isNull();
    }

    private static Recipe recipe(List<Tag> tags) {
        return Recipe.builder().source(RecipeSource.COMMUNITY).title("레시피")
                .difficulty(Difficulty.EASY).cookTimeMinutes(10).servings(1).tags(tags).build();
    }

    private static Tag tag(TagType type, String name, Long id) {
        Tag tag = new Tag(type, name);
        ReflectionTestUtils.setField(tag, "id", id);
        return tag;
    }
}
