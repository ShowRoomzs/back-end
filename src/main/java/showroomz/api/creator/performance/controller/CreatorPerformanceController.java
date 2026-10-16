package showroomz.api.creator.performance.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.creator.performance.docs.CreatorPerformanceControllerDocs;
import showroomz.api.creator.performance.dto.CreatorPerformanceDto;
import showroomz.api.creator.performance.service.CreatorPerformanceQueryService;
import showroomz.api.creator.performance.type.CreatorPerformanceSort;
import showroomz.api.creator.performance.type.CreatorPerformanceStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.Set;

@RestController
@RequestMapping("/v1/creator/performance")
@RequiredArgsConstructor
public class CreatorPerformanceController implements CreatorPerformanceControllerDocs {

    private final CreatorPerformanceQueryService queryService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<CreatorPerformanceDto.ListItem>> getList(
            @RequestParam(value = "months", required = false) Integer months,
            @RequestParam(value = "status", required = false) Set<CreatorPerformanceStatus> status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sort", required = false) CreatorPerformanceSort sort,
            @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(queryService.getList(getCurrentUserEmail(), months, status, keyword, sort,
                pagingRequest));
    }

    @Override
    @GetMapping("/summary")
    public ResponseEntity<CreatorPerformanceDto.Summary> getSummary(
            @RequestParam(value = "months", required = false) Integer months) {
        return ResponseEntity.ok(queryService.getSummary(getCurrentUserEmail(), months));
    }

    @Override
    @GetMapping("/{groupBuyId}")
    public ResponseEntity<CreatorPerformanceDto.Detail> getDetail(@PathVariable Long groupBuyId) {
        return ResponseEntity.ok(queryService.getDetail(getCurrentUserEmail(), groupBuyId));
    }

    private String getCurrentUserEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return userPrincipal.getUsername();
    }
}
