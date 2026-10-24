package showroomz.api.seller.performance.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.seller.performance.docs.SellerPerformanceControllerDocs;
import showroomz.api.seller.performance.dto.SellerPerformanceDto;
import showroomz.api.seller.performance.service.SellerPerformanceCostService;
import showroomz.api.seller.performance.service.SellerPerformanceQueryService;
import showroomz.domain.performance.type.GroupBuyPerformanceSort;
import showroomz.domain.performance.type.PerformanceStatus;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.util.Set;

@RestController
@RequestMapping("/v1/seller/performance")
@RequiredArgsConstructor
public class SellerPerformanceController implements SellerPerformanceControllerDocs {

    private final SellerPerformanceQueryService queryService;
    private final SellerPerformanceCostService costService;

    @Override
    @GetMapping("/group-buys")
    public ResponseEntity<SellerPerformanceDto.ListPage> getList(
            @RequestParam(value = "status", required = false) Set<PerformanceStatus> status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sort", required = false) GroupBuyPerformanceSort sort,
            @ModelAttribute PagingRequest pagingRequest) {
        return ResponseEntity.ok(queryService.getList(getCurrentSellerEmail(), status, keyword, sort, pagingRequest));
    }

    @Override
    @GetMapping("/summary")
    public ResponseEntity<SellerPerformanceDto.Summary> getSummary() {
        return ResponseEntity.ok(queryService.getSummary(getCurrentSellerEmail()));
    }

    @Override
    @GetMapping("/group-buys/{groupBuyId}")
    public ResponseEntity<SellerPerformanceDto.Detail> getDetail(@PathVariable Long groupBuyId) {
        return ResponseEntity.ok(queryService.getDetail(getCurrentSellerEmail(), groupBuyId));
    }

    @Override
    @PutMapping("/group-buys/{groupBuyId}/cost")
    public ResponseEntity<SellerPerformanceDto.Detail> saveCost(@PathVariable Long groupBuyId,
                                                                @RequestBody SellerPerformanceDto.CostRequest request) {
        return ResponseEntity.ok(costService.save(getCurrentSellerEmail(), groupBuyId, request));
    }

    private String getCurrentSellerEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return userPrincipal.getUsername();
    }
}
