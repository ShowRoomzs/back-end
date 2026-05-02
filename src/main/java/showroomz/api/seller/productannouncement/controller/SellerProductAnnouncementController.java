package showroomz.api.seller.productannouncement.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.seller.productannouncement.docs.SellerProductAnnouncementControllerDocs;
import showroomz.api.seller.productannouncement.dto.SellerProductAnnouncementBulkDeleteRequest;
import showroomz.api.seller.productannouncement.dto.SellerProductAnnouncementBulkResult;
import showroomz.api.seller.productannouncement.dto.SellerProductAnnouncementBulkStatusRequest;
import showroomz.api.seller.productannouncement.dto.SellerProductAnnouncementCreateRequest;
import showroomz.api.seller.productannouncement.dto.SellerProductAnnouncementCreateResponse;
import showroomz.api.seller.productannouncement.dto.SellerProductAnnouncementDetailResponse;
import showroomz.api.seller.productannouncement.dto.SellerProductAnnouncementListItem;
import showroomz.api.seller.productannouncement.dto.SellerProductAnnouncementUpdateRequest;
import showroomz.api.seller.productannouncement.service.SellerProductAnnouncementService;
import showroomz.domain.productannouncement.type.ProductAnnouncementDisplayStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.net.URI;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/v1/seller/product-announcements")
@RequiredArgsConstructor
@Tag(name = SellerProductAnnouncementControllerDocs.SWAGGER_TAG, description = "마켓 상품 공지사항 관리")
public class SellerProductAnnouncementController implements SellerProductAnnouncementControllerDocs {

    private final SellerProductAnnouncementService sellerProductAnnouncementService;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<SellerProductAnnouncementListItem>> list(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "displayStatus", required = false) ProductAnnouncementDisplayStatus displayStatus,
            @RequestParam(value = "createdFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(value = "createdTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @ParameterObject @ModelAttribute PagingRequest pagingRequest
    ) {
        String sellerEmail = requireSellerEmail(userPrincipal);
        return ResponseEntity.ok(sellerProductAnnouncementService.search(
                sellerEmail, keyword, category, displayStatus, createdFrom, createdTo, pagingRequest));
    }

    @Override
    @PostMapping
    public ResponseEntity<SellerProductAnnouncementCreateResponse> create(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody SellerProductAnnouncementCreateRequest request
    ) {
        String sellerEmail = requireSellerEmail(userPrincipal);
        Long id = sellerProductAnnouncementService.create(sellerEmail, request);
        URI location = URI.create("/v1/seller/product-announcements/" + id);
        return ResponseEntity.created(location).body(new SellerProductAnnouncementCreateResponse(
                id,
                "상품 공지사항이 성공적으로 등록되었습니다."));
    }

    @Override
    @GetMapping("/{announcementId}")
    public ResponseEntity<SellerProductAnnouncementDetailResponse> getDetail(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("announcementId") Long announcementId) {
        String sellerEmail = requireSellerEmail(userPrincipal);
        return ResponseEntity.ok(sellerProductAnnouncementService.getDetail(sellerEmail, announcementId));
    }

    @Override
    @PutMapping("/{announcementId}")
    public ResponseEntity<Void> update(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("announcementId") Long announcementId,
            @Valid @RequestBody SellerProductAnnouncementUpdateRequest request
    ) {
        String sellerEmail = requireSellerEmail(userPrincipal);
        sellerProductAnnouncementService.update(sellerEmail, announcementId, request);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/{announcementId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable("announcementId") Long announcementId) {
        String sellerEmail = requireSellerEmail(userPrincipal);
        sellerProductAnnouncementService.delete(sellerEmail, announcementId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/bulk-delete")
    public ResponseEntity<SellerProductAnnouncementBulkResult> bulkDelete(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody SellerProductAnnouncementBulkDeleteRequest request
    ) {
        String sellerEmail = requireSellerEmail(userPrincipal);
        int affected = sellerProductAnnouncementService.bulkDelete(sellerEmail, request);
        return ResponseEntity.ok(new SellerProductAnnouncementBulkResult(affected));
    }

    @Override
    @PatchMapping("/bulk-status")
    public ResponseEntity<SellerProductAnnouncementBulkResult> bulkStatus(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody SellerProductAnnouncementBulkStatusRequest request
    ) {
        String sellerEmail = requireSellerEmail(userPrincipal);
        int affected = sellerProductAnnouncementService.bulkUpdateStatus(sellerEmail, request);
        return ResponseEntity.status(HttpStatus.OK).body(new SellerProductAnnouncementBulkResult(affected));
    }

    private static String requireSellerEmail(UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_INFO);
        }
        return userPrincipal.getUsername();
    }
}
