package com.whattocook.community.controller;

import com.whattocook.community.dto.ReportCreateRequest;
import com.whattocook.community.dto.ReportCreateResponse;
import com.whattocook.community.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Report", description = "레시피·댓글 신고")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "신고",
            description = "레시피나 댓글을 신고한다. 같은 대상은 한 번만 신고할 수 있고, 자신의 글은 신고할 수 없다. "
                    + "누적 신고 수가 기준값에 이르면 대상이 숨겨진다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportCreateResponse report(@AuthenticationPrincipal Long userId,
                                       @Valid @RequestBody ReportCreateRequest request) {
        return new ReportCreateResponse(reportService.report(userId, request));
    }
}
