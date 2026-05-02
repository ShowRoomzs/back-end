package showroomz.api.seller.productannouncement.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.seller.productannouncement.dto.SellerProductAnnouncementBulkDeleteRequest;
import showroomz.api.seller.productannouncement.dto.SellerProductAnnouncementBulkResult;
import showroomz.api.seller.productannouncement.dto.SellerProductAnnouncementBulkStatusRequest;
import showroomz.api.seller.productannouncement.dto.SellerProductAnnouncementCreateRequest;
import showroomz.api.seller.productannouncement.dto.SellerProductAnnouncementCreateResponse;
import showroomz.api.seller.productannouncement.dto.SellerProductAnnouncementDetailResponse;
import showroomz.api.seller.productannouncement.dto.SellerProductAnnouncementListItem;
import showroomz.api.seller.productannouncement.dto.SellerProductAnnouncementUpdateRequest;
import showroomz.domain.productannouncement.type.ProductAnnouncementDisplayStatus;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.time.LocalDateTime;

@Tag(name = SellerProductAnnouncementControllerDocs.SWAGGER_TAG, description = "마켓 상품 공지사항 관리")
public interface SellerProductAnnouncementControllerDocs {

    /** OpenAPI·Swagger UI 태그 (SpringDoc은 @Operation tags에 명시할 때 가장 확실히 그룹핑됨) */
    String SWAGGER_TAG = "Seller - Announcement";

    @Operation(
            tags = { SWAGGER_TAG },
            summary = "상품 공지 목록 조회",
            description = "로그인 판매자(마켓) 소유 공지만 조회. 검색어(제목/본문), 카테고리, 노출 상태, 등록 기간 필터 및 페이징(page, size)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공",
                    content = @Content(schema = @Schema(implementation = PageResponse.class)))
    })
    ResponseEntity<PageResponse<SellerProductAnnouncementListItem>> list(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "제목/본문 검색어") @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "카테고리 정확 일치") @RequestParam(value = "category", required = false) String category,
            @Parameter(description = "노출 상태") @RequestParam(value = "displayStatus", required = false) ProductAnnouncementDisplayStatus displayStatus,
            @Parameter(description = "등록일시 시작(포함)") @RequestParam(value = "createdFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @Parameter(description = "등록일시 종료(포함)") @RequestParam(value = "createdTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @ParameterObject PagingRequest pagingRequest
    );

    @Operation(
            tags = { SWAGGER_TAG },
            summary = "상품 공지 등록",
            description = "exposureType=SPECIFIC이면 targetProductIds 필수. 노출 기간 사용 시 시작/종료 일시 검증")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성됨",
                    content = @Content(schema = @Schema(implementation = SellerProductAnnouncementCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "입력 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerProductAnnouncementCreateResponse> create(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody SellerProductAnnouncementCreateRequest request
    );

    @Operation(
            tags = { SWAGGER_TAG },
            summary = "상품 공지 상세 조회",
            description = "지정 노출인 경우 대상 상품 정보 포함. 본인 소유만 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공",
                    content = @Content(schema = @Schema(implementation = SellerProductAnnouncementDetailResponse.class))),
            @ApiResponse(responseCode = "404", description = "없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<SellerProductAnnouncementDetailResponse> getDetail(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(name = "announcementId", description = "상품 공지 ID", in = ParameterIn.PATH)
            @PathVariable("announcementId") Long announcementId
    );

    @Operation(
            tags = { SWAGGER_TAG },
            summary = "상품 공지 수정",
            description = "기존 대상 상품 매핑 삭제 후 재등록")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "성공"),
            @ApiResponse(responseCode = "404", description = "없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> update(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(name = "announcementId", description = "상품 공지 ID", in = ParameterIn.PATH)
            @PathVariable("announcementId") Long announcementId,
            @Valid @RequestBody SellerProductAnnouncementUpdateRequest request
    );

    @Operation(tags = { SWAGGER_TAG }, summary = "상품 공지 삭제")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "성공"),
            @ApiResponse(responseCode = "404", description = "없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> delete(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(name = "announcementId", description = "상품 공지 ID", in = ParameterIn.PATH)
            @PathVariable("announcementId") Long announcementId
    );

    @Operation(
            tags = { SWAGGER_TAG },
            summary = "상품 공지 일괄 삭제",
            description = "본인 소유 ID만 벌크 삭제")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공",
                    content = @Content(schema = @Schema(implementation = SellerProductAnnouncementBulkResult.class)))
    })
    ResponseEntity<SellerProductAnnouncementBulkResult> bulkDelete(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody SellerProductAnnouncementBulkDeleteRequest request
    );

    @Operation(
            tags = { SWAGGER_TAG },
            summary = "상품 공지 노출 상태 일괄 변경",
            description = "벌크 UPDATE 후 영속성 컨텍스트 초기화. 본인 소유만")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공",
                    content = @Content(schema = @Schema(implementation = SellerProductAnnouncementBulkResult.class)))
    })
    ResponseEntity<SellerProductAnnouncementBulkResult> bulkStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody SellerProductAnnouncementBulkStatusRequest request
    );
}
