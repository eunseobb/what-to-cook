package com.whattocook.recipe.controller;

import com.whattocook.recipe.domain.TagType;
import com.whattocook.recipe.dto.TagResponse;
import com.whattocook.recipe.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Tag", description = "태그 마스터")
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    // shortcut: 태그 마스터가 수십 개라 페이지네이션 없이 전체를 준다, 수백 개로 늘면 커서 페이지네이션으로 바꾼다.
    @Operation(summary = "태그 목록", description = "온보딩·레시피 태그 선택지. type을 주면 그 종류만 준다.")
    @SecurityRequirements
    @GetMapping
    public List<TagResponse> list(
            @Parameter(description = "태그 종류 (생략 시 전체)") @RequestParam(required = false) TagType type) {
        return tagService.list(type);
    }
}
