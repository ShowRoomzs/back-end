package showroomz.api.admin.productinquiry.docs;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.admin.productinquiry.dto.AdminProductInquiryDeleteDecision;
import showroomz.api.admin.productinquiry.dto.AdminProductInquiryDto;
import showroomz.api.admin.productinquiry.type.AdminProductInquiryStatusFilter;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.domain.inquiry.type.ProductInquiryType;
import showroomz.global.dto.PagingRequest;

import java.util.List;

@Hidden
@Tag(name = "Admin - Product Inquiry Monitoring", description = "어드민 상품 문의 모니터링 API (§18) — 운영자는 답변하지 않는다")
public interface AdminProductInquiryControllerDocs {

    @Operation(
            summary = "상품 문의 목록 조회",
            description = "전체 마켓의 상품 문의를 모니터링합니다 (§18-2). 답변 주체는 브랜드이고, " +
                    "운영자는 부적절 게시물을 걸러내는 역할만 합니다 — 이 화면에 답변 입력란은 없습니다.\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "**상태 탭 (`status`)** — 배타적 단일선택, 기본 진입은 `ALL`(전체)\n" +
                    "| 코드 | 의미 | 배지 색 |\n" +
                    "|------|------|------|\n" +
                    "| `ALL` | 전체 | — |\n" +
                    "| `WAITING` | 답변대기 | 정보 |\n" +
                    "| `ANSWERED` | 답변완료 | 중립 |\n" +
                    "| `DELETE_REQUESTED` | 삭제 요청(브랜드) | 경고 — 이 화면에서 유일하게 운영자 조치가 필요한 상태 |\n" +
                    "| `DELETED` | 삭제 | 위험 — 소비자 노출이 실제로 막힌 상태라 원칙 ②의 의도적 예외입니다 |\n\n" +
                    "**검색 조건**\n" +
                    "- `type`: 문의 유형 단일선택 (`OPTION`, `INGREDIENT_USAGE`, `RESTOCK`, `DELIVERY`, `ETC`)\n" +
                    "- `keyword`: 상품명·브랜드·질문 통합 검색\n\n" +
                    "**건수 필드**\n" +
                    "`statusCounts`는 `type`·`keyword`는 반영하고 상태 탭 조건만 제외한 값입니다. " +
                    "`deleteRequestedCount`는 탭·필터와 무관한 **전체** 삭제 요청 건수로, " +
                    "운영자가 매번 확인해야 하는 유일한 수치이며 툴바의 `삭제 요청 N건`이 이 값입니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "목록 조회 성공")
    })
    ResponseEntity<AdminProductInquiryDto.ListResponse> getList(
            @RequestParam(value = "status", defaultValue = "ALL") AdminProductInquiryStatusFilter status,
            @RequestParam(value = "type", required = false) ProductInquiryType type,
            @RequestParam(value = "keyword", required = false) String keyword,
            @ModelAttribute PagingRequest pagingRequest
    );

    @Operation(
            summary = "GNB 배지용 삭제 요청 건수 조회",
            description = "탭·필터와 무관한 전체 삭제 요청 건수를 반환합니다 (§18-2). " +
                    "운영자가 매번 확인해야 하는 유일한 수치입니다.\n\n**권한:** ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    ResponseEntity<AdminProductInquiryDto.SummaryResponse> getSummary();

    @Operation(
            summary = "문의 유형 필터 옵션 조회",
            description = "목록 상단 `전체 유형` 셀렉트에 쓸 문의 유형 목록을 반환합니다 (§18-2-1).\n\n**권한:** ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    ResponseEntity<List<AdminProductInquiryDto.TypeOption>> getTypes();

    @Operation(
            summary = "상품 문의 상세 조회",
            description = "문의 정보 · 문의 내용 · 브랜드 답변 3카드와, 삭제 요청이 있는 건에만 존재하는 " +
                    "삭제 요청 카드, 우측 처리·이력을 함께 조회합니다 (§18-3).\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "**작성자 표기** — 실명 우선, 없으면 닉네임을 그대로 노출합니다. " +
                    "파트너센터(브랜드) 화면과 달리 마스킹하지 않고 회원 상세로 이동하는 링크(`userId`)도 함께 내려줍니다. " +
                    "상품(`productId`)·브랜드(`marketId`) 링크도 각각 별도 이동입니다.\n\n" +
                    "**우측 처리 패널(`processingMeta`)** — 상태와 무관하게 같은 레이아웃이며, " +
                    "표시 항목만 현재 상태에 맞는 그룹 하나로 바뀝니다: 답변완료 → 답변일시·답변자, " +
                    "삭제 요청 → 요청일시·요청자, 삭제 → 삭제일시·처리자·삭제 사유.\n\n" +
                    "**액션 노출 규칙(§18-4)**\n" +
                    "- `canExecuteDelete`: 이미 삭제된 건이 아니면 언제나 `true` — 삭제 요청 유무와 무관하게 운영자가 직접 삭제할 수 있습니다\n" +
                    "- `canReject`: 삭제 요청이 있을 때만 `true` — 기각할 대상이 없으면 반려는 성립하지 않습니다\n\n" +
                    "목록과 같은 검색 조건을 함께 넘기면 그 순서 기준의 `prevInquiryId` · `nextInquiryId`를 계산합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "상세 조회 성공"),
            @ApiResponse(
                    responseCode = "404",
                    description = "문의를 찾을 수 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<AdminProductInquiryDto.DetailResponse> getDetail(
            @PathVariable("inquiryId") Long inquiryId,
            @RequestParam(value = "status", defaultValue = "ALL") AdminProductInquiryStatusFilter status,
            @RequestParam(value = "type", required = false) ProductInquiryType type,
            @RequestParam(value = "keyword", required = false) String keyword
    );

    @Operation(
            summary = "문의 삭제 집행",
            description = "문의를 삭제 처리합니다 (§18-5). **되돌릴 수 없는 집행 액션**입니다 — 확인 모달은 FE 책임입니다.\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "- 삭제 요청 유무와 무관하게 답변대기·답변완료 어느 상태에서든 운영자가 직접 집행할 수 있습니다.\n" +
                    "- 집행 즉시 질문과 브랜드 답변이 **함께** 소비자·브랜드 화면에서 비노출됩니다. 원문·첨부·처리 내역은 내부 기록으로 보관됩니다.\n" +
                    "- `reason`은 내부 기록용이며 **작성자·브랜드에게 통지하지 않습니다**.\n" +
                    "- `reason`이 `ETC`(기타 직접 입력)일 때만 `detail`이 필수입니다.\n" +
                    "- 삭제 요청을 거쳐 삭제된 건은 삭제 요청 카드가 상세에 그대로 남습니다(§18-7) — " +
                    "요청 없이 직접 삭제한 건은 이 카드가 없습니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "삭제 집행 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "이미 삭제된 문의(`INQUIRY_ALREADY_DELETED`) · 기타 사유인데 상세 사유 누락(`INVALID_INPUT`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<Void> executeDelete(
            @PathVariable("inquiryId") Long inquiryId,
            @Valid @RequestBody AdminProductInquiryDeleteDecision.ExecuteRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    );

    @Operation(
            summary = "삭제 요청 반려",
            description = "브랜드의 삭제 요청만 기각하고 문의는 게시 유지합니다 (§18-6). " +
                    "**삭제 요청이 있을 때만 성립합니다** — 요청 없는 건에는 이 액션 자체가 노출되지 않습니다.\n\n" +
                    "**권한:** ADMIN\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "- 노출 축만 정상으로 되돌리므로 답변 축이 보존돼 있어 **요청 직전 상태**(답변대기/답변완료)로 정확히 복귀합니다. " +
                    "\"항상 답변완료로 복귀\"가 아닙니다.\n" +
                    "- `reason`(반려 사유)은 **요청 브랜드에게 전달됩니다** — 삭제 사유가 내부 기록인 것과 대비됩니다. " +
                    "요청자가 결과의 근거를 알아야 다음 요청 기준을 잡을 수 있기 때문입니다.\n" +
                    "- 작성자(소비자)에게는 통지하지 않습니다.\n" +
                    "- `reason`이 `ETC`(기타 직접 입력)일 때만 `detail`이 필수입니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "반려 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "삭제 요청이 없는 문의(`INQUIRY_DELETE_NOT_REQUESTED`) · 기타 사유인데 상세 사유 누락(`INVALID_INPUT`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<Void> rejectDeleteRequest(
            @PathVariable("inquiryId") Long inquiryId,
            @Valid @RequestBody AdminProductInquiryDeleteDecision.RejectRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    );
}
