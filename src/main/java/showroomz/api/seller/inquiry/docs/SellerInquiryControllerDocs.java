package showroomz.api.seller.inquiry.docs;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.seller.inquiry.dto.ProductInquiryDetailResponse;
import showroomz.api.seller.inquiry.dto.SellerInquiryAnswerRequest;
import showroomz.api.seller.inquiry.dto.SellerInquiryDeleteRequest;
import showroomz.api.seller.inquiry.dto.SellerInquiryListResponse;
import showroomz.api.seller.inquiry.dto.SellerInquirySearchCondition;
import showroomz.global.dto.PagingRequest;

@Hidden
@Tag(name = "Seller - Inquiry", description = "파트너센터 문의 관리 API (§23)")
public interface SellerInquiryControllerDocs {

    @Operation(
            summary = "파트너센터 문의 목록 조회",
            description = "본인 마켓의 **상품 문의**를 조회합니다. 1:1 문의는 어드민(운영자)으로만 접수되므로 포함되지 않습니다.\n\n" +
                    "**권한:** SELLER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "**상태 두 축 (§23-1)**\n" +
                    "- 답변 축(`status`): `WAITING`(답변대기) / `ANSWERED`(답변완료)\n" +
                    "- 노출 축(`exposureStatus`): `NORMAL` / `DELETE_REQUESTED`(삭제 요청) / `DELETED`(삭제)\n" +
                    "- 화면에서는 한 열에 합쳐 보여주며 그 문자열이 `statusLabel`입니다. " +
                    "삭제 요청 중에도 답변 축 값이 보존되므로 반려되면 요청 직전 상태로 그대로 복귀합니다.\n\n" +
                    "**검색 조건 (§23-2)**\n" +
                    "- `status`: 상태 탭 — 배타적 단일선택 (`ALL`, `WAITING`, `ANSWERED`, `DELETE_REQUESTED`, `DELETED`)\n" +
                    "- `types`: 문의 유형 — 다중선택 (`OPTION`, `INGREDIENT_USAGE`, `RESTOCK`, `DELIVERY`, `ETC`)\n" +
                    "- `visibilities`: 공개여부 — 다중선택 (`PUBLIC`, `SECRET`)\n" +
                    "- `keyword`: 상품명·질문 통합 검색\n" +
                    "- `sort`: `WAITING_FIRST`(기본 · 답변대기 우선) / `CREATED_AT`(등록일순)\n\n" +
                    "**건수 필드**\n" +
                    "`statusCounts` · `typeCounts` · `visibilityCounts`는 검색어·필터와 무관하게 " +
                    "**마켓 전체 기준**으로 셉니다. 검색 결과가 없어도 카운트는 그대로 두는 화면 규칙 때문입니다. " +
                    "`totalCount`만 현재 탭·필터·검색 기준입니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "문의 목록 조회 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "판매자 마켓을 찾을 수 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<SellerInquiryListResponse> getInquiries(
            @ModelAttribute SellerInquirySearchCondition condition,
            @ModelAttribute PagingRequest pagingRequest
    );

    @Operation(
            summary = "상품 문의 상세 조회",
            description = "본인 마켓 상품에 등록된 상품 문의의 상세를 조회합니다 (§23-3).\n\n" +
                    "**권한:** SELLER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "**작성자 표기**\n" +
                    "닉네임 마스킹(`구****`)만 내려갑니다. 브랜드는 실명·이메일·연락처를 볼 수 없고 회원 상세 링크도 없습니다.\n\n" +
                    "**포함 정보**\n" +
                    "- 문의번호(`QNA-YYYYMMDD-NNN`) · 유형 · 상품 · 공개여부 · 등록일시\n" +
                    "- 문의 내용과 첨부 사진(최대 3장)\n" +
                    "- 브랜드 답변 — 등록 시각과 수정 시각을 함께 내려 조용한 수정을 막습니다\n" +
                    "- `answerElapsedText`: 답변 소요(실제 걸린 시간). SLA 잔여 시간이 아닙니다\n" +
                    "- `deleteRequest`: 삭제 요청 경위와 결과. **운영자의 삭제 사유는 내부 기록이라 내려가지 않습니다**\n" +
                    "- `history`: 처리 이력(최신순)\n" +
                    "- `canRegisterAnswer` · `canModifyAnswer` · `canRequestDelete`: 버튼 활성 여부\n\n" +
                    "목록 조회와 같은 검색 조건을 함께 넘기면 그 순서 기준의 `prevInquiryId` · `nextInquiryId`를 계산합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "문의 상세 조회 성공"),
            @ApiResponse(
                    responseCode = "403",
                    description = "본인 마켓의 문의가 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "문의를 찾을 수 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<ProductInquiryDetailResponse> getInquiryDetail(
            @PathVariable("inquiryId") Long inquiryId,
            @ModelAttribute SellerInquirySearchCondition condition
    );

    @Operation(
            summary = "상품 문의 답변 등록",
            description = "브랜드 답변을 등록합니다 (§23-4). 파트너센터가 **답변의 유일한 작성 창구**입니다.\n\n" +
                    "**권한:** SELLER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "- 등록 즉시 **공개 콘텐츠로 전환**됩니다 — 상품 상세 문의 탭에 질문·답변이 함께 노출되며 작성자 마스킹은 유지됩니다.\n" +
                    "- 비밀글은 답변해도 공개로 전환되지 않습니다.\n" +
                    "- 답변 상한은 2,000자입니다([근거 대기] 잠정치). 화면에서는 상한에서 입력이 막히므로 초과 상태 자체가 없습니다.\n" +
                    "- 삭제 요청 검토 중이거나 삭제된 문의에는 답변할 수 없습니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "답변 등록 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "이미 답변된 문의(`INQUIRY_ALREADY_ANSWERED`) · 삭제 검토 중(`INQUIRY_UNDER_DELETE_REVIEW`) · 삭제된 문의(`INQUIRY_ALREADY_DELETED`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "본인 마켓의 문의가 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<Void> registerAnswer(
            @PathVariable("inquiryId") Long inquiryId,
            @Valid @RequestBody SellerInquiryAnswerRequest request
    );

    @Operation(
            summary = "상품 문의 답변 수정",
            description = "등록된 브랜드 답변을 수정합니다 (§23-4).\n\n" +
                    "**권한:** SELLER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "공개 콘텐츠라 잘못된 안내를 고칠 경로가 없으면 브랜드가 삭제 요청으로 우회하게 되므로 수정을 허용합니다. " +
                    "대신 조용한 수정은 막습니다 — 등록 시각은 유지한 채 `answerModifiedAt`이 갱신되고 처리 이력에 「답변 수정」이 남습니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "답변 수정 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "미답변 문의(`INQUIRY_NOT_ANSWERED`) · 삭제 검토 중(`INQUIRY_UNDER_DELETE_REVIEW`) · 삭제된 문의(`INQUIRY_ALREADY_DELETED`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<Void> modifyAnswer(
            @PathVariable("inquiryId") Long inquiryId,
            @Valid @RequestBody SellerInquiryAnswerRequest request
    );

    @Operation(
            summary = "상품 문의 삭제 요청",
            description = "부적절한 문의의 삭제를 운영자에게 요청합니다 (§23-5). **브랜드는 요청까지, 집행은 운영자**입니다.\n\n" +
                    "**권한:** SELLER\n" +
                    "**요청 헤더:** Authorization: Bearer {accessToken}\n\n" +
                    "- 답변대기·답변완료 어느 쪽에서도 요청할 수 있습니다.\n" +
                    "- 요청해도 문의는 즉시 삭제되지 않고 **검토 중에도 계속 게시**됩니다.\n" +
                    "- **요청 취소는 불가**하며, 검토 중에는 답변 등록·수정도 막힙니다.\n" +
                    "- 반려되면 노출 축만 정상으로 돌아가 **요청 직전 상태**(답변대기/답변완료)로 복귀하고, 반려 사유가 전달됩니다. " +
                    "사유를 보완해 **재요청**할 수 있습니다.\n\n" +
                    "**사유 (`reason`)** — [근거 대기] 목록 잠정\n" +
                    "| 코드 | 설명 |\n" +
                    "|------|------|\n" +
                    "| `ABUSE` | 비방·욕설 |\n" +
                    "| `PRIVACY_EXPOSURE` | 개인정보 노출 |\n" +
                    "| `ADVERTISEMENT` | 광고·홍보 |\n" +
                    "| `BRAND_COMPARISON` | 타 브랜드 비교·비방 |\n" +
                    "| `ETC` | 기타(직접 입력) — 이 값일 때만 `detail`이 필수입니다 |"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "삭제 요청 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "이미 요청함(`INQUIRY_DELETE_ALREADY_REQUESTED`) · 삭제된 문의(`INQUIRY_ALREADY_DELETED`) · 기타 사유인데 상세 설명 누락(`INVALID_INPUT`)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "본인 마켓의 문의가 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    ResponseEntity<Void> requestDelete(
            @PathVariable("inquiryId") Long inquiryId,
            @Valid @RequestBody SellerInquiryDeleteRequest request
    );
}
