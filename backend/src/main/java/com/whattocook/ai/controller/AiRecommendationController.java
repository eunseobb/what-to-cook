package com.whattocook.ai.controller;

import com.whattocook.ai.dto.AiRecipeJobStatus;
import com.whattocook.ai.dto.AiRecommendationRequest;
import com.whattocook.ai.dto.AiRecommendationResponse;
import com.whattocook.ai.service.AiRecipeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI Recommendation", description = "LLM 기타 추천")
@RestController
@RequestMapping("/api/recommendations/ai")
@RequiredArgsConstructor
public class AiRecommendationController {

    private final AiRecipeService aiRecipeService;

    @Operation(summary = "AI 레시피 추천 요청",
            description = "같은 재료 집합으로 만든 AI 레시피가 있으면 바로 COMPLETED(200)로 준다. "
                    + "없으면 생성을 시작하고 PENDING(202)과 jobId를 준다: GET /api/recommendations/ai/{jobId}로 폴링한다. "
                    + "생성 요청은 사용자별 한도가 있어 넘으면 429(AI006)를 준다(재사용은 세지 않는다).")
    @PostMapping
    public ResponseEntity<AiRecommendationResponse> request(@AuthenticationPrincipal Long userId,
                                                            @Valid @RequestBody AiRecommendationRequest request) {
        AiRecommendationResponse response = aiRecipeService.request(userId, request.ingredients());
        return ResponseEntity.status(response.status() == AiRecipeJobStatus.PENDING ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(response);
    }

    @Operation(summary = "AI 레시피 추천 결과 조회", description = "PENDING이면 잠시 뒤 다시 조회한다. FAILED면 errorCode(AI003 서킷 오픈, AI004 타임아웃, AI005 생성 실패)를 보고 다시 요청한다.")
    @GetMapping("/{jobId}")
    public AiRecommendationResponse get(@AuthenticationPrincipal Long userId, @PathVariable UUID jobId) {
        return aiRecipeService.getJob(userId, jobId);
    }
}
