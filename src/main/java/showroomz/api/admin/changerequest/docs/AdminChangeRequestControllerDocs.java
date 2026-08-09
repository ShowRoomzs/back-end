package showroomz.api.admin.changerequest.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.admin.changerequest.dto.AdminChangeRequestDto;
import showroomz.api.admin.changerequest.type.AdminChangeRequestStatusFilter;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.domain.changerequest.type.ChangeRequestType;
import showroomz.global.dto.PagingRequest;

import java.util.List;

@Tag(name = "Admin - Change Request", description = "어드민 브랜드 회원정보 변경 요청 검토·승인·반려 API (§16)")
public interface AdminChangeRequestControllerDocs {

    @Operation(
            summary = "목록 조회",
            description = "브랜드 회원정보(사업자 정보·정산 계좌) 변경 요청 목록이다.\n\n" +
                    "- 검토 대기(`PENDING`)가 항상 위, 그 안에서 경과 내림차순(SLA 초과 건이 최상단). 정렬 셀렉트는 두지 않는다(§16-1)\n" +
                    "- `CANCELED`는 `status=ALL`에서만 노출\n" +
                    "- `keyword`는 브랜드명 부분 일치\n" +
                    "- `statusCounts`로 탭 배지 건수를 함께 내려준다(`all`은 CANCELED 포함 합)\n" +
                    "- `elapsedText`: 24h 미만 → `18h`, 24h 이상 → `2일 3h` / 처리 완료 건은 `null`\n" +
                    "- `slaExceeded`: PENDING && 경과 > 48h\n\n" +
                    "**권한:** ADMIN"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminChangeRequestDto.ListResponse.class),
                            examples = @ExampleObject(
                                    name = "성공",
                                    value = "{\n" +
                                            "  \"content\": [\n" +
                                            "    {\n" +
                                            "      \"requestId\": 12,\n" +
                                            "      \"requestCode\": \"CHG-2026-0001\",\n" +
                                            "      \"brandName\": \"코코브라운\",\n" +
                                            "      \"type\": \"BUSINESS_INFO\",\n" +
                                            "      \"requestedAt\": \"2026-08-07T10:00:00\",\n" +
                                            "      \"processedAt\": null,\n" +
                                            "      \"elapsedText\": \"2일 6h\",\n" +
                                            "      \"slaExceeded\": true,\n" +
                                            "      \"status\": \"PENDING\"\n" +
                                            "    },\n" +
                                            "    {\n" +
                                            "      \"requestId\": 15,\n" +
                                            "      \"requestCode\": \"CHG-2026-0004\",\n" +
                                            "      \"brandName\": \"데일리코스\",\n" +
                                            "      \"type\": \"SETTLEMENT_ACCOUNT\",\n" +
                                            "      \"requestedAt\": \"2026-08-09T09:00:00\",\n" +
                                            "      \"processedAt\": null,\n" +
                                            "      \"elapsedText\": \"11h\",\n" +
                                            "      \"slaExceeded\": false,\n" +
                                            "      \"status\": \"PENDING\"\n" +
                                            "    }\n" +
                                            "  ],\n" +
                                            "  \"pageInfo\": {\n" +
                                            "    \"currentPage\": 1,\n" +
                                            "    \"totalPages\": 1,\n" +
                                            "    \"totalResults\": 2,\n" +
                                            "    \"limit\": 20,\n" +
                                            "    \"hasNext\": false\n" +
                                            "  },\n" +
                                            "  \"statusCounts\": {\n" +
                                            "    \"pending\": 2,\n" +
                                            "    \"approved\": 10,\n" +
                                            "    \"rejected\": 3,\n" +
                                            "    \"canceled\": 1,\n" +
                                            "    \"all\": 16\n" +
                                            "  }\n" +
                                            "}"
                            )
                    )
            )
    })
    ResponseEntity<AdminChangeRequestDto.ListResponse> getList(
            @Parameter(description = "탭 — PENDING(기본) / APPROVED / REJECTED / ALL", example = "PENDING")
            @RequestParam(value = "status", defaultValue = "PENDING") AdminChangeRequestStatusFilter status,
            @Parameter(description = "브랜드명 검색어 — 비우면 전체", example = "코코")
            @RequestParam(value = "keyword", required = false) String keyword,
            @ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "GNB 배지용 검토 대기 건수",
            description = "§16-0 상위 '입점 관리' 배지 합산에 쓰인다. `pendingCount`만 반환한다.\n\n**권한:** ADMIN"
    )
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AdminChangeRequestDto.SummaryResponse.class),
                    examples = @ExampleObject(
                            name = "성공",
                            value = "{\n  \"pendingCount\": 2\n}"
                    )
            )
    )
    ResponseEntity<AdminChangeRequestDto.SummaryResponse> getSummary();

    @Operation(
            summary = "반려 사유 드롭다운",
            description = "유형별 정형 반려 사유를 내려준다(§16-5).\n\n" +
                    "- `BUSINESS_INFO`: 6종(증빙 미첨부·값 불일치·판독 불가·유효기간 경과·사유 불충분·기타)\n" +
                    "- `SETTLEMENT_ACCOUNT`: 5종(통장 미첨부·예금주 불일치·계좌 오류·통장 판독 불가·기타)\n" +
                    "- `detailRequired`는 `OTHER`만 `true` — 선택 시 `reasonDetail` 필수\n\n" +
                    "**권한:** ADMIN"
    )
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(
                    mediaType = "application/json",
                    examples = {
                            @ExampleObject(
                                    name = "사업자 정보",
                                    value = "[\n" +
                                            "  { \"code\": \"EVIDENCE_MISSING\", \"label\": \"증빙 서류 미첨부\", \"detailRequired\": false },\n" +
                                            "  { \"code\": \"EVIDENCE_VALUE_MISMATCH\", \"label\": \"증빙 서류와 요청 값이 일치하지 않음\", \"detailRequired\": false },\n" +
                                            "  { \"code\": \"EVIDENCE_UNREADABLE\", \"label\": \"서류 판독 불가 (흐림·잘림)\", \"detailRequired\": false },\n" +
                                            "  { \"code\": \"EVIDENCE_EXPIRED\", \"label\": \"서류 유효기간 경과\", \"detailRequired\": false },\n" +
                                            "  { \"code\": \"REASON_INSUFFICIENT\", \"label\": \"변경 사유 불충분\", \"detailRequired\": false },\n" +
                                            "  { \"code\": \"OTHER\", \"label\": \"기타\", \"detailRequired\": true }\n" +
                                            "]"
                            ),
                            @ExampleObject(
                                    name = "정산 계좌",
                                    value = "[\n" +
                                            "  { \"code\": \"BANKBOOK_MISSING\", \"label\": \"통장 사본 미첨부\", \"detailRequired\": false },\n" +
                                            "  { \"code\": \"HOLDER_NAME_MISMATCH\", \"label\": \"예금주와 사업자 명의 불일치\", \"detailRequired\": false },\n" +
                                            "  { \"code\": \"ACCOUNT_NUMBER_INVALID\", \"label\": \"계좌번호 오류·미개설 계좌\", \"detailRequired\": false },\n" +
                                            "  { \"code\": \"BANKBOOK_UNREADABLE\", \"label\": \"통장 사본 판독 불가\", \"detailRequired\": false },\n" +
                                            "  { \"code\": \"OTHER\", \"label\": \"기타\", \"detailRequired\": true }\n" +
                                            "]"
                            )
                    }
            )
    )
    ResponseEntity<List<AdminChangeRequestDto.RejectReasonOption>> getRejectReasons(
            @Parameter(description = "요청 유형 — BUSINESS_INFO / SETTLEMENT_ACCOUNT", required = true, example = "BUSINESS_INFO")
            @RequestParam("type") ChangeRequestType type);

    @Operation(
            summary = "상세 조회",
            description = "변경 요청 상세(대조표·증빙·이력·이전/다음)이다.\n\n" +
                    "- 대조표(`diff`)는 변경 없는 행까지 전부 내려준다. 사업자등록번호는 `locked=true`('변경 요청 불가')\n" +
                    "- `SETTLEMENT_ACCOUNT`는 `holderCheck`(예금주 vs 상호 불일치 여부)를 포함한다\n" +
                    "- 이전/다음(`prevRequestId`/`nextRequestId`)은 `status` 파라미터가 가리키는 현재 탭 목록 순서를 따른다(§16-2)\n\n" +
                    "**권한:** ADMIN"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminChangeRequestDto.DetailResponse.class),
                            examples = @ExampleObject(
                                    name = "사업자 정보 PENDING",
                                    value = "{\n" +
                                            "  \"requestId\": 12,\n" +
                                            "  \"requestCode\": \"CHG-2026-0001\",\n" +
                                            "  \"brandName\": \"코코브라운\",\n" +
                                            "  \"marketId\": 7,\n" +
                                            "  \"type\": \"BUSINESS_INFO\",\n" +
                                            "  \"status\": \"PENDING\",\n" +
                                            "  \"slaExceeded\": true,\n" +
                                            "  \"requestedAt\": \"2026-08-07T10:00:00\",\n" +
                                            "  \"processedAt\": null,\n" +
                                            "  \"requesterName\": \"김담당\",\n" +
                                            "  \"elapsedText\": \"2일 6h\",\n" +
                                            "  \"reason\": \"대표자 변경 및 사업장 이전\",\n" +
                                            "  \"diff\": [\n" +
                                            "    { \"fieldKey\": \"BUSINESS_TYPE\", \"label\": \"사업자 유형\", \"currentValue\": \"법인\", \"requestedValue\": \"법인\", \"changed\": false, \"locked\": false },\n" +
                                            "    { \"fieldKey\": \"MARKET_NAME\", \"label\": \"브랜드명\", \"currentValue\": \"코코브라운\", \"requestedValue\": \"코코브라운\", \"changed\": false, \"locked\": false },\n" +
                                            "    { \"fieldKey\": \"REPRESENTATIVE_NAME\", \"label\": \"대표자명\", \"currentValue\": \"김대표\", \"requestedValue\": \"이대표\", \"changed\": true, \"locked\": false },\n" +
                                            "    { \"fieldKey\": \"COMPANY_NAME\", \"label\": \"사업자등록증 상호\", \"currentValue\": \"(주)코코브라운\", \"requestedValue\": \"(주)코코브라운\", \"changed\": false, \"locked\": false },\n" +
                                            "    { \"fieldKey\": \"BUSINESS_REG_NUMBER\", \"label\": \"사업자등록번호\", \"currentValue\": \"123-45-67890\", \"requestedValue\": \"123-45-67890\", \"changed\": false, \"locked\": true },\n" +
                                            "    { \"fieldKey\": \"BUSINESS_CONDITION\", \"label\": \"업태\", \"currentValue\": \"도소매\", \"requestedValue\": \"도소매\", \"changed\": false, \"locked\": false },\n" +
                                            "    { \"fieldKey\": \"BUSINESS_ADDRESS\", \"label\": \"사업장 주소\", \"currentValue\": \"서울시 서초구 ...\", \"requestedValue\": \"서울시 강남구 테헤란로 123\", \"changed\": true, \"locked\": false },\n" +
                                            "    { \"fieldKey\": \"MAIL_ORDER_REG_NUMBER\", \"label\": \"통신판매업 신고번호\", \"currentValue\": \"2024-서울서초-0001\", \"requestedValue\": \"2024-서울서초-0001\", \"changed\": false, \"locked\": false }\n" +
                                            "  ],\n" +
                                            "  \"changedFieldLabels\": [\"대표자명\", \"사업장 주소\"],\n" +
                                            "  \"evidence\": {\n" +
                                            "    \"documentLabel\": \"사업자등록증\",\n" +
                                            "    \"fileName\": \"사업자등록증_변경.jpg\",\n" +
                                            "    \"fileSizeBytes\": 1258291,\n" +
                                            "    \"extension\": \"jpg\",\n" +
                                            "    \"fileUrl\": \"https://s3.ap-northeast-2.amazonaws.com/bucket/change-request/biz.jpg\",\n" +
                                            "    \"uploadedAt\": \"2026-08-07T10:00:00\"\n" +
                                            "  },\n" +
                                            "  \"referenceItems\": [],\n" +
                                            "  \"holderCheck\": null,\n" +
                                            "  \"history\": [\n" +
                                            "    { \"event\": \"REQUESTED\", \"occurredAt\": \"2026-08-07T10:00:00\", \"actorLabel\": \"김담당\" }\n" +
                                            "  ],\n" +
                                            "  \"prevRequestId\": null,\n" +
                                            "  \"nextRequestId\": 15\n" +
                                            "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "미존재",
                                    value = "{\n" +
                                            "  \"code\": \"CHANGE_REQUEST_NOT_FOUND\",\n" +
                                            "  \"message\": \"존재하지 않는 변경 요청입니다.\"\n" +
                                            "}"
                            )
                    )
            )
    })
    ResponseEntity<AdminChangeRequestDto.DetailResponse> getDetail(
            @Parameter(description = "요청 ID", required = true, example = "12") @PathVariable("requestId") Long requestId,
            @Parameter(description = "이전/다음 계산 기준 탭", example = "PENDING")
            @RequestParam(value = "status", defaultValue = "PENDING") AdminChangeRequestStatusFilter status);

    @Operation(
            summary = "승인",
            description = "검토 대기 요청을 전체 승인한다(부분 승인 없음).\n\n" +
                    "- 승인 시 요청 항목이 브랜드/셀러 실제 정보에 반영된다\n" +
                    "- 브랜드명 변경 건은 승인 시점에 중복을 재검사한다(`DUPLICATE_MARKET_NAME`)\n" +
                    "- 정산 계좌 승인 시 통장 사본을 증빙 파일로 교체한다(§7-2)\n" +
                    "- 처리 후 브랜드 담당자 이메일로 결과 안내 메일을 발송한다\n\n" +
                    "**권한:** ADMIN"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "승인 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminChangeRequestDto.ProcessResponse.class),
                            examples = @ExampleObject(
                                    name = "성공",
                                    value = "{\n" +
                                            "  \"requestId\": 12,\n" +
                                            "  \"requestCode\": \"CHG-2026-0001\",\n" +
                                            "  \"brandName\": \"코코브라운\",\n" +
                                            "  \"type\": \"BUSINESS_INFO\",\n" +
                                            "  \"status\": \"APPROVED\",\n" +
                                            "  \"processedAt\": \"2026-08-10T11:05:00\",\n" +
                                            "  \"rejectReason\": null,\n" +
                                            "  \"rejectReasonDetail\": null\n" +
                                            "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "PENDING 상태가 아님 또는 브랜드명 중복",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "PENDING 아님",
                                            value = "{\n" +
                                                    "  \"code\": \"CHANGE_REQUEST_NOT_PENDING\",\n" +
                                                    "  \"message\": \"검토 대기 상태인 요청만 처리할 수 있습니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "브랜드명 중복",
                                            value = "{\n" +
                                                    "  \"code\": \"DUPLICATE_MARKET_NAME\",\n" +
                                                    "  \"message\": \"이미 사용 중인 마켓명입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "미존재",
                                    value = "{\n" +
                                            "  \"code\": \"CHANGE_REQUEST_NOT_FOUND\",\n" +
                                            "  \"message\": \"존재하지 않는 변경 요청입니다.\"\n" +
                                            "}"
                            )
                    )
            )
    })
    ResponseEntity<AdminChangeRequestDto.ProcessResponse> approve(
            @Parameter(description = "요청 ID", required = true, example = "12") @PathVariable("requestId") Long requestId,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(
            summary = "반려",
            description = "검토 대기 요청을 반려한다.\n\n" +
                    "- `reasonType`이 요청 유형에 맞지 않으면 400(`CHANGE_REQUEST_REJECT_REASON_TYPE_MISMATCH`)\n" +
                    "- `OTHER` 선택 시에만 `reasonDetail` 필수(`CHANGE_REQUEST_REJECT_DETAIL_REQUIRED`)\n" +
                    "- 정형 사유 문구는 브랜드 배너·통지 메일에 그대로 노출된다\n\n" +
                    "**권한:** ADMIN"
    )
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = AdminChangeRequestDto.RejectRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "정형 사유",
                                    value = "{\n" +
                                            "  \"reasonType\": \"REASON_INSUFFICIENT\",\n" +
                                            "  \"reasonDetail\": null\n" +
                                            "}"
                            ),
                            @ExampleObject(
                                    name = "기타(상세 필수)",
                                    value = "{\n" +
                                            "  \"reasonType\": \"OTHER\",\n" +
                                            "  \"reasonDetail\": \"제출하신 서류의 발급일이 6개월을 초과했습니다.\"\n" +
                                            "}"
                            )
                    }
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "반려 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminChangeRequestDto.ProcessResponse.class),
                            examples = @ExampleObject(
                                    name = "성공",
                                    value = "{\n" +
                                            "  \"requestId\": 12,\n" +
                                            "  \"requestCode\": \"CHG-2026-0001\",\n" +
                                            "  \"brandName\": \"코코브라운\",\n" +
                                            "  \"type\": \"BUSINESS_INFO\",\n" +
                                            "  \"status\": \"REJECTED\",\n" +
                                            "  \"processedAt\": \"2026-08-10T11:05:00\",\n" +
                                            "  \"rejectReason\": \"변경 사유 불충분\",\n" +
                                            "  \"rejectReasonDetail\": null\n" +
                                            "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "PENDING 아님 / 사유 유형 불일치 / 기타 사유 상세 누락",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "사유 유형 불일치",
                                            value = "{\n" +
                                                    "  \"code\": \"CHANGE_REQUEST_REJECT_REASON_TYPE_MISMATCH\",\n" +
                                                    "  \"message\": \"해당 유형에 사용할 수 없는 반려 사유입니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "기타 상세 누락",
                                            value = "{\n" +
                                                    "  \"code\": \"CHANGE_REQUEST_REJECT_DETAIL_REQUIRED\",\n" +
                                                    "  \"message\": \"기타 사유를 선택한 경우 상세 사유는 필수입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "미존재",
                                    value = "{\n" +
                                            "  \"code\": \"CHANGE_REQUEST_NOT_FOUND\",\n" +
                                            "  \"message\": \"존재하지 않는 변경 요청입니다.\"\n" +
                                            "}"
                            )
                    )
            )
    })
    ResponseEntity<AdminChangeRequestDto.ProcessResponse> reject(
            @Parameter(description = "요청 ID", required = true, example = "12") @PathVariable("requestId") Long requestId,
            @Valid @org.springframework.web.bind.annotation.RequestBody AdminChangeRequestDto.RejectRequest request,
            @Parameter(hidden = true) UserPrincipal principal);
}
