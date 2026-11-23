package com.whattocook.user.service;

import com.whattocook.global.error.BusinessException;
import com.whattocook.global.error.ErrorCode;
import com.whattocook.ingredient.domain.Ingredient;
import com.whattocook.ingredient.repository.IngredientRepository;
import com.whattocook.recipe.domain.Tag;
import com.whattocook.recipe.dto.TagResponse;
import com.whattocook.recipe.repository.TagRepository;
import com.whattocook.user.domain.ExclusionType;
import com.whattocook.user.domain.User;
import com.whattocook.user.domain.UserExcludedIngredient;
import com.whattocook.user.domain.UserPreferenceTag;
import com.whattocook.user.dto.OnboardingRequest;
import com.whattocook.user.dto.OnboardingResponse;
import com.whattocook.user.dto.OnboardingResponse.IngredientItem;
import com.whattocook.user.repository.UserExcludedIngredientRepository;
import com.whattocook.user.repository.UserPreferenceTagRepository;
import com.whattocook.user.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 온보딩: 로그인 사용자 본인의 선호 태그와 알레르기·비선호 재료를 조회하고 통째로 교체한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OnboardingService {

    private final UserPreferenceTagRepository preferenceTagRepository;
    private final UserExcludedIngredientRepository excludedIngredientRepository;
    private final TagRepository tagRepository;
    private final IngredientRepository ingredientRepository;
    private final UserRepository userRepository;

    public OnboardingResponse get(Long userId) {
        List<TagResponse> tags = preferenceTagRepository.findAllByUserIdOrderByTagIdAsc(userId).stream()
                .map(p -> TagResponse.from(p.getTag()))
                .toList();
        List<UserExcludedIngredient> excluded = excludedIngredientRepository.findAllByUserIdOrderByIngredientIdAsc(userId);
        return new OnboardingResponse(tags, ingredientsOf(excluded, ExclusionType.ALLERGY),
                ingredientsOf(excluded, ExclusionType.DISLIKE));
    }

    /** 기존 선호 태그·제외 재료를 지우고 요청 내용으로 바꾼다. 선호 태그는 초기 가중치로 다시 시작한다. */
    @Transactional
    public void replace(Long userId, OnboardingRequest request) {
        List<Tag> tags = findTags(request.tagIds());
        Map<Long, Ingredient> ingredients = findIngredients(
                Stream.concat(request.allergyIngredientIds().stream(), request.dislikeIngredientIds().stream()).toList());
        User user = userRepository.getReferenceById(userId);

        preferenceTagRepository.deleteAllByUserId(userId);
        excludedIngredientRepository.deleteAllByUserId(userId);

        preferenceTagRepository.saveAll(tags.stream()
                .map(tag -> new UserPreferenceTag(user, tag, UserPreferenceTag.ONBOARDING_WEIGHT))
                .toList());
        excludedIngredientRepository.saveAll(Stream.concat(
                        request.allergyIngredientIds().stream()
                                .map(id -> new UserExcludedIngredient(user, ingredients.get(id), ExclusionType.ALLERGY)),
                        request.dislikeIngredientIds().stream()
                                .map(id -> new UserExcludedIngredient(user, ingredients.get(id), ExclusionType.DISLIKE)))
                .toList());
    }

    private static List<IngredientItem> ingredientsOf(List<UserExcludedIngredient> excluded, ExclusionType type) {
        return excluded.stream()
                .filter(e -> e.getType() == type)
                .map(e -> IngredientItem.from(e.getIngredient()))
                .toList();
    }

    private List<Tag> findTags(List<Long> tagIds) {
        List<Long> distinctIds = tagIds.stream().distinct().toList();
        List<Tag> tags = tagRepository.findAllById(distinctIds);
        if (tags.size() != distinctIds.size()) {
            throw new BusinessException(ErrorCode.ONBOARDING_TAG_NOT_FOUND);
        }
        return tags;
    }

    /** 한 재료는 알레르기·비선호 중 한 사유만 가지므로 두 목록을 합쳐 중복을 거부한다. */
    private Map<Long, Ingredient> findIngredients(List<Long> ids) {
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new BusinessException(ErrorCode.DUPLICATE_EXCLUDED_INGREDIENT);
        }
        Map<Long, Ingredient> byId = ingredientRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Ingredient::getId, Function.identity()));
        if (byId.size() != ids.size()) {
            throw new BusinessException(ErrorCode.ONBOARDING_INGREDIENT_NOT_FOUND);
        }
        return byId;
    }
}
