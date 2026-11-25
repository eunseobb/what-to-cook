package com.whattocook.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.whattocook.global.error.BusinessException;
import com.whattocook.global.error.ErrorCode;
import com.whattocook.ingredient.domain.Ingredient;
import com.whattocook.ingredient.domain.IngredientCategory;
import com.whattocook.ingredient.repository.IngredientRepository;
import com.whattocook.recipe.domain.Tag;
import com.whattocook.recipe.domain.TagType;
import com.whattocook.recipe.dto.TagResponse;
import com.whattocook.recipe.repository.TagRepository;
import com.whattocook.user.domain.ExclusionType;
import com.whattocook.user.domain.User;
import com.whattocook.user.domain.UserExcludedIngredient;
import com.whattocook.user.domain.UserPreferenceTag;
import com.whattocook.user.dto.OnboardingRequest;
import com.whattocook.user.dto.OnboardingResponse;
import com.whattocook.user.repository.UserExcludedIngredientRepository;
import com.whattocook.user.repository.UserPreferenceTagRepository;
import com.whattocook.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock
    private UserPreferenceTagRepository preferenceTagRepository;
    @Mock
    private UserExcludedIngredientRepository excludedIngredientRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private IngredientRepository ingredientRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OnboardingService onboardingService;

    private final User user = withId(new User("me@example.com", "hashed", "나"), 1L);
    private final Tag korean = withId(new Tag(TagType.CUISINE, "한식"), 100L);
    private final Tag spicy = withId(new Tag(TagType.TASTE, "매운맛"), 101L);
    private final Ingredient peanut = withId(Ingredient.builder()
            .name("땅콩").category(IngredientCategory.ETC).baseUnit("g").build(), 10L);
    private final Ingredient cucumber = withId(Ingredient.builder()
            .name("오이").category(IngredientCategory.VEGETABLE).baseUnit("개").build(), 11L);

    @Test
    @DisplayName("교체 시 기존 정보를 지우고 선호 태그는 초기 가중치로, 재료는 사유별로 저장한다")
    @SuppressWarnings("unchecked")
    void replace() {
        given(tagRepository.findAllById(List.of(100L, 101L))).willReturn(List.of(korean, spicy));
        given(ingredientRepository.findAllById(List.of(10L, 11L))).willReturn(List.of(peanut, cucumber));
        given(userRepository.getReferenceById(1L)).willReturn(user);

        onboardingService.replace(1L, new OnboardingRequest(List.of(100L, 101L, 100L), List.of(10L), List.of(11L)));

        InOrder order = inOrder(preferenceTagRepository, excludedIngredientRepository);
        order.verify(preferenceTagRepository).deleteAllByUserId(1L);
        order.verify(excludedIngredientRepository).deleteAllByUserId(1L);

        ArgumentCaptor<List<UserPreferenceTag>> tags = ArgumentCaptor.forClass(List.class);
        verify(preferenceTagRepository).saveAll(tags.capture());
        assertThat(tags.getValue())
                .extracting(UserPreferenceTag::getTag, UserPreferenceTag::getWeight)
                .containsExactly(tuple(korean, UserPreferenceTag.ONBOARDING_WEIGHT),
                        tuple(spicy, UserPreferenceTag.ONBOARDING_WEIGHT));

        ArgumentCaptor<List<UserExcludedIngredient>> excluded = ArgumentCaptor.forClass(List.class);
        verify(excludedIngredientRepository).saveAll(excluded.capture());
        assertThat(excluded.getValue())
                .extracting(UserExcludedIngredient::getIngredient, UserExcludedIngredient::getType)
                .containsExactly(tuple(peanut, ExclusionType.ALLERGY), tuple(cucumber, ExclusionType.DISLIKE));
    }

    @Test
    @DisplayName("같은 재료가 알레르기·비선호에 함께 있으면 아무것도 바꾸지 않고 거부한다")
    void replaceRejectsDuplicateIngredient() {
        given(tagRepository.findAllById(List.of())).willReturn(List.of());

        assertThatThrownBy(() -> onboardingService.replace(1L,
                new OnboardingRequest(null, List.of(10L), List.of(10L))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_EXCLUDED_INGREDIENT);
        verify(preferenceTagRepository, never()).deleteAllByUserId(anyLong());
    }

    @Test
    @DisplayName("없는 태그나 재료가 있으면 거부한다")
    void replaceRejectsUnknownIds() {
        given(tagRepository.findAllById(List.of(999L))).willReturn(List.of());
        assertThatThrownBy(() -> onboardingService.replace(1L, new OnboardingRequest(List.of(999L), null, null)))
                .extracting("errorCode").isEqualTo(ErrorCode.ONBOARDING_TAG_NOT_FOUND);

        given(tagRepository.findAllById(List.of())).willReturn(List.of());
        given(ingredientRepository.findAllById(List.of(10L, 999L))).willReturn(List.of(peanut));
        assertThatThrownBy(() -> onboardingService.replace(1L, new OnboardingRequest(null, List.of(10L), List.of(999L))))
                .extracting("errorCode").isEqualTo(ErrorCode.ONBOARDING_INGREDIENT_NOT_FOUND);

        verify(excludedIngredientRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("조회 시 선호 태그와 사유별 제외 재료를 나눠 돌려준다")
    void get() {
        given(preferenceTagRepository.findAllByUserIdOrderByTagIdAsc(1L))
                .willReturn(List.of(new UserPreferenceTag(user, korean, 1.0)));
        given(excludedIngredientRepository.findAllByUserIdOrderByIngredientIdAsc(1L)).willReturn(List.of(
                new UserExcludedIngredient(user, peanut, ExclusionType.ALLERGY),
                new UserExcludedIngredient(user, cucumber, ExclusionType.DISLIKE)));

        OnboardingResponse response = onboardingService.get(1L);

        assertThat(response.preferenceTags()).containsExactly(new TagResponse(100L, TagType.CUISINE, "한식", "accent-sage"));
        assertThat(response.allergyIngredients()).containsExactly(new OnboardingResponse.IngredientItem(10L, "땅콩"));
        assertThat(response.dislikeIngredients()).containsExactly(new OnboardingResponse.IngredientItem(11L, "오이"));
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
