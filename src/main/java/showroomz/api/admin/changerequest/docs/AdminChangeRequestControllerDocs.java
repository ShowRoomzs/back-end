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

@Tag(name = "Admin - Change Request", description = "관리자 회원정보 변경 요청 관리 API")
public interface AdminChangeRequestControllerDocs {

    @Operation(
            summary = "목록 조회",
            description = "회원정보 변경 요청 목록이다 — 브랜드(사업자 정보 · 정산 계좌) + 인플루언서(사업자 구분 변경 · rev.7 §16-2a).\n\n" +
                    "- 1열 「요청자」는 `requester { type: BRAND | CREATOR, id, name }` — 브랜드명 / 쇼룸명. `brandName` 은 브랜드 행만(deprecated)\n" +
                    "- `type`: `BUSINESS_INFO` · `SETTLEMENT_ACCOUNT` · `TO_BUSINESS`(사업자 전환) · `TO_INDIVIDUAL`(비사업자 전환). 유형 필터는 두지 않는다(§16-1)\n" +
                    "- 검토 대기(`PENDING`)가 항상 위, 그 안에서 경과 내림차순(SLA 초과 건이 최상단) — 요청자가 섞여도 한 정렬. 정렬 셀렉트는 두지 않는다(§16-1)\n" +
                    "- `CANCELED`는 `status=ALL`에서만 노출\n" +
                    "- `keyword`는 브랜드명 · 쇼룸명 부분 일치\n" +
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
                                            "      \"requester\": { \"type\": \"BRAND\", \"id\": 7, \"name\": \"코코브라운\" },\n" +
                                            "      \"brandName\": \"코코브라운\",\n" +
                                            "      \"type\": \"BUSINESS_INFO\",\n" +
                                            "      \"requestedAt\": \"2026-08-07T10:00:00\",\n" +
                                            "      \"processedAt\": null,\n" +
                                            "      \"elapsedText\": \"2일 6h\",\n" +
                                            "      \"slaExceeded\": true,\n" +
                                            "      \"status\": \"PENDING\"\n" +
                                            "    },\n" +
                                            "    {\n" +
                                            "      \"requestId\": 31,\n" +
                                            "      \"requestCode\": \"CHG-2026-0031\",\n" +
                                            "      \"requester\": { \"type\": \"CREATOR\", \"id\": 31, \"name\": \"예린 뷰티\" },\n" +
                                            "      \"brandName\": null,\n" +
                                            "      \"type\": \"TO_BUSINESS\",\n" +
                                            "      \"requestedAt\": \"2026-08-08T15:00:00\",\n" +
                                            "      \"processedAt\": null,\n" +
                                            "      \"elapsedText\": \"1일 1h\",\n" +
                                            "      \"slaExceeded\": false,\n" +
                                            "      \"status\": \"PENDING\"\n" +
                                            "    },\n" +
                                            "    {\n" +
                                            "      \"requestId\": 15,\n" +
                                            "      \"requestCode\": \"CHG-2026-0004\",\n" +
                                            "      \"requester\": { \"type\": \"BRAND\", \"id\": 9, \"name\": \"데일리코스\" },\n" +
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
                                            "    \"totalResults\": 3,\n" +
                                            "    \"limit\": 20,\n" +
                                            "    \"hasNext\": false\n" +
                                            "  },\n" +
                                            "  \"statusCounts\": {\n" +
                                            "    \"pending\": 3,\n" +
                                            "    \"approved\": 10,\n" +
                                            "    \"rejected\": 3,\n" +
                                            "    \"canceled\": 1,\n" +
                                            "    \"all\": 17\n" +
                                            "  }\n" +
                                            "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 권한 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<AdminChangeRequestDto.ListResponse> getList(
            @Parameter(description = "탭 — PENDING(기본) / APPROVED / REJECTED / ALL", example = "PENDING")
            @RequestParam(value = "status", defaultValue = "PENDING") AdminChangeRequestStatusFilter status,
            @Parameter(description = "브랜드명 · 쇼룸명 검색어(부분 일치) — 비우면 전체", example = "코코")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "요청자 — BRAND · CREATOR(1021 기-22) · 비우면 전체", example = "CREATOR")
            @RequestParam(value = "requesterType", required = false)
            showroomz.domain.changerequest.type.ChangeRequesterType requesterType,
            @Parameter(description = "요청 유형 — BUSINESS_INFO · SETTLEMENT_ACCOUNT · TO_BUSINESS · TO_INDIVIDUAL · CREATOR_ACCOUNT · 비우면 전체",
                    example = "CREATOR_ACCOUNT")
            @RequestParam(value = "type", required = false) showroomz.domain.changerequest.type.ChangeRequestType type,
            @ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "GNB 배지용 검토 대기 건수",
            description = "§16-0 상위 '입점 관리' 배지 합산에 쓰인다. `pendingCount`만 반환한다.\n\n**권한:** ADMIN"
    )
    @ApiResponses({
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
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 권한 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<AdminChangeRequestDto.SummaryResponse> getSummary();

    @Operation(
            summary = "반려 사유 드롭다운",
            description = "유형별 정형 반려 사유를 내려준다(§16-5).\n\n" +
                    "- `BUSINESS_INFO`: 6종(증빙 미첨부·값 불일치·판독 불가·유효기간 경과·사유 불충분·기타)\n" +
                    "- `SETTLEMENT_ACCOUNT`: 5종(통장 미첨부·예금주 불일치·계좌 오류·통장 판독 불가·기타)\n" +
                    "- `TO_BUSINESS` · `TO_INDIVIDUAL`: 6종 공통(C8b — 등록증 판독 불가 · 대표자명 불일치 · 등록번호 불일치 · 휴·폐업 · 폐업 미확인 · 기타)\n" +
                    "- `detailRequired`는 `OTHER`만 `true` — 선택 시 `reasonDetail` 필수\n\n" +
                    "**권한:** ADMIN"
    )
    @ApiResponses({
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
                                    ),
                                    @ExampleObject(
                                            name = "사업자 구분 변경(TO_BUSINESS · TO_INDIVIDUAL)",
                                            value = "[\n" +
                                                    "  { \"code\": \"LICENSE_UNREADABLE\", \"label\": \"사업자등록증 판독 불가 (흐림·잘림)\", \"detailRequired\": false },\n" +
                                                    "  { \"code\": \"LICENSE_OWNER_NAME_MISMATCH\", \"label\": \"등록증의 대표자명이 본인 실명과 다름\", \"detailRequired\": false },\n" +
                                                    "  { \"code\": \"BUSINESS_NUMBER_MISMATCH\", \"label\": \"사업자등록번호가 등록증과 다름\", \"detailRequired\": false },\n" +
                                                    "  { \"code\": \"BUSINESS_SUSPENDED_OR_CLOSED\", \"label\": \"휴업 · 폐업 상태의 사업자\", \"detailRequired\": false },\n" +
                                                    "  { \"code\": \"CLOSURE_NOT_CONFIRMED\", \"label\": \"폐업 사실이 확인되지 않음 (비사업자 전환)\", \"detailRequired\": false },\n" +
                                                    "  { \"code\": \"OTHER\", \"label\": \"기타\", \"detailRequired\": true }\n" +
                                                    "]"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 권한 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    ResponseEntity<List<AdminChangeRequestDto.RejectReasonOption>> getRejectReasons(
            @Parameter(description = "요청 유형 — BUSINESS_INFO / SETTLEMENT_ACCOUNT / TO_BUSINESS / TO_INDIVIDUAL", required = true, example = "BUSINESS_INFO")
            @RequestParam("type") ChangeRequestType type);

    @Operation(
            summary = "상세 조회",
            description = "변경 요청 상세(대조표·증빙·이력·이전/다음)이다.\n\n" +
                    "- 대조표(`diff`)는 변경 없는 행까지 전부 내려준다. 사업자등록번호는 `locked=true`('변경 요청 불가')\n" +
                    "- `SETTLEMENT_ACCOUNT`는 `holderCheck`(예금주 vs 상호 불일치 여부)를 포함한다\n" +
                    "- 이전/다음(`prevRequestId`/`nextRequestId`)은 `status` 파라미터가 가리키는 현재 탭 목록 순서를 따른다(§16-2) — 요청자를 넘나든다\n" +
                    "- `diff[].source`: `REQUEST`(요청 항목) · `LIVE`(현재 값) · `DERIVED`(구분 변경에 따라 바뀜) · `DOCUMENT`(서류로 운영자 확인) · `CHECK`(규칙 판정)\n" +
                    "- **인플루언서 건(C3a · C3b · C5b)** — `requester.type=CREATOR` · `requesterName`/`reason` null · 대조표 6행(사업자 구분 · 등록번호 · " +
                    "상호/대표자 또는 주민등록번호 · 지급 방식 · 예금주 · 정산 계좌(마스킹)) · `compareItems`(서류와 대조할 값 — 생년월일은 서버가 " +
                    "`MATCH`/`MISMATCH` 판정) · `approvalImpact`(검토 대기일 때만 — 「다음 정산부터」 · 기존 구분으로 끝나는 정산). 본인확인 실명 · " +
                    "생년월일은 검토 대기 건에서만 실값이다\n\n" +
                    "**권한:** ADMIN"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminChangeRequestDto.DetailResponse.class),
                            examples = {@ExampleObject(
                                    name = "사업자 정보 PENDING",
                                    value = "{\n" +
                                            "  \"requestId\": 12,\n" +
                                            "  \"requestCode\": \"CHG-2026-0001\",\n" +
                                            "  \"requester\": { \"type\": \"BRAND\", \"id\": 7, \"name\": \"코코브라운\" },\n" +
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
                                            "  \"compareItems\": null,\n" +
                                            "  \"approvalImpact\": null,\n" +
                                            "  \"history\": [\n" +
                                            "    { \"event\": \"REQUESTED\", \"occurredAt\": \"2026-08-07T10:00:00\", \"actorLabel\": \"브랜드 파트너센터\" }\n" +
                                            "  ],\n" +
                                            "  \"prevRequestId\": null,\n" +
                                            "  \"nextRequestId\": 31\n" +
                                            "}"
                            ),
                            @ExampleObject(
                                    name = "인플루언서 사업자 전환(TO_BUSINESS) PENDING — C3a",
                                    value = "{\n" +
                                            "  \"requestId\": 31,\n" +
                                            "  \"requestCode\": \"CHG-2026-0031\",\n" +
                                            "  \"requester\": { \"type\": \"CREATOR\", \"id\": 31, \"name\": \"예린 뷰티\" },\n" +
                                            "  \"brandName\": null,\n" +
                                            "  \"marketId\": null,\n" +
                                            "  \"type\": \"TO_BUSINESS\",\n" +
                                            "  \"status\": \"PENDING\",\n" +
                                            "  \"slaExceeded\": false,\n" +
                                            "  \"requestedAt\": \"2026-08-08T15:00:00\",\n" +
                                            "  \"processedAt\": null,\n" +
                                            "  \"requesterName\": null,\n" +
                                            "  \"elapsedText\": \"1일 1h\",\n" +
                                            "  \"reason\": null,\n" +
                                            "  \"diff\": [\n" +
                                            "    { \"fieldKey\": \"CREATOR_BUSINESS_TYPE\", \"label\": \"사업자 구분\", \"currentValue\": \"개인 (비사업자)\", \"requestedValue\": \"개인사업자\", \"changed\": true, \"locked\": false, \"source\": \"REQUEST\" },\n" +
                                            "    { \"fieldKey\": \"CREATOR_BUSINESS_REG_NUMBER\", \"label\": \"사업자등록번호\", \"currentValue\": null, \"requestedValue\": \"123-45-67890\", \"changed\": true, \"locked\": false, \"source\": \"REQUEST\" },\n" +
                                            "    { \"fieldKey\": \"LICENSE_COMPANY_REPRESENTATIVE\", \"label\": \"상호 · 대표자 (등록증)\", \"currentValue\": null, \"requestedValue\": null, \"changed\": false, \"locked\": false, \"source\": \"DOCUMENT\" },\n" +
                                            "    { \"fieldKey\": \"PAYOUT_METHOD\", \"label\": \"지급 방식\", \"currentValue\": \"원천징수 3.3%\", \"requestedValue\": \"세금계산서 · 리워드 + 부가세 10%\", \"changed\": true, \"locked\": false, \"source\": \"DERIVED\" },\n" +
                                            "    { \"fieldKey\": \"ACCOUNT_HOLDER\", \"label\": \"정산 계좌 예금주\", \"currentValue\": \"오예린\", \"requestedValue\": null, \"changed\": false, \"locked\": false, \"source\": \"CHECK\" },\n" +
                                            "    { \"fieldKey\": \"SETTLEMENT_ACCOUNT\", \"label\": \"정산 계좌\", \"currentValue\": \"신한은행 110***4567\", \"requestedValue\": null, \"changed\": false, \"locked\": false, \"source\": \"LIVE\" }\n" +
                                            "  ],\n" +
                                            "  \"changedFieldLabels\": [\"사업자 구분\", \"사업자등록번호\"],\n" +
                                            "  \"evidence\": {\n" +
                                            "    \"documentLabel\": \"사업자등록증\",\n" +
                                            "    \"fileName\": \"사업자등록증_예린뷰티.pdf\",\n" +
                                            "    \"fileSizeBytes\": 1258291,\n" +
                                            "    \"extension\": \"pdf\",\n" +
                                            "    \"fileUrl\": \"https://cdn…/creator-document/license.pdf\",\n" +
                                            "    \"uploadedAt\": \"2026-08-08T15:00:00\"\n" +
                                            "  },\n" +
                                            "  \"referenceItems\": [ { \"label\": \"정산 계좌 예금주\", \"value\": \"오예린\" } ],\n" +
                                            "  \"holderCheck\": { \"currentHolder\": \"오예린\", \"rule\": \"REPRESENTATIVE_NAME\", \"requestedHolder\": null, \"companyName\": null, \"mismatch\": false },\n" +
                                            "  \"compareItems\": [\n" +
                                            "    { \"key\": \"REPRESENTATIVE_NAME\", \"label\": \"대표자명\", \"value\": \"오예린\", \"mustBeBefore\": null, \"serverCheck\": null },\n" +
                                            "    { \"key\": \"BUSINESS_REG_NUMBER\", \"label\": \"사업자등록번호\", \"value\": \"123-45-67890\", \"mustBeBefore\": null, \"serverCheck\": null },\n" +
                                            "    { \"key\": \"OPENING_DATE\", \"label\": \"개업일\", \"value\": null, \"mustBeBefore\": \"2026-08-08T15:00:00\", \"serverCheck\": null }\n" +
                                            "  ],\n" +
                                            "  \"approvalImpact\": {\n" +
                                            "    \"appliesFrom\": \"NEXT_SETTLEMENT\",\n" +
                                            "    \"payoutMethodBefore\": \"WITHHOLDING_3_3\",\n" +
                                            "    \"payoutMethodAfter\": \"TAX_INVOICE\",\n" +
                                            "    \"retainedSettlements\": [ { \"settlementId\": 1021, \"settlementNumber\": \"STL-2609-016\", \"status\": \"REVIEWING\" } ],\n" +
                                            "    \"accountChangeRequired\": false,\n" +
                                            "    \"accountHolderRuleAfter\": \"REPRESENTATIVE_NAME\"\n" +
                                            "  },\n" +
                                            "  \"history\": [\n" +
                                            "    { \"event\": \"REQUESTED\", \"occurredAt\": \"2026-08-08T15:00:00\", \"actorLabel\": \"쇼룸 스튜디오\" }\n" +
                                            "  ],\n" +
                                            "  \"prevRequestId\": 12,\n" +
                                            "  \"nextRequestId\": 15\n" +
                                            "}"
                            ),
                            @ExampleObject(
                                    name = "인플루언서 비사업자 전환(TO_INDIVIDUAL) PENDING — C3b",
                                    value = "{\n" +
                                            "  \"requestId\": 35,\n" +
                                            "  \"requestCode\": \"CHG-2026-0035\",\n" +
                                            "  \"requester\": { \"type\": \"CREATOR\", \"id\": 44, \"name\": \"지민 스타일\" },\n" +
                                            "  \"type\": \"TO_INDIVIDUAL\",\n" +
                                            "  \"status\": \"PENDING\",\n" +
                                            "  \"requesterName\": null,\n" +
                                            "  \"reason\": null,\n" +
                                            "  \"diff\": [\n" +
                                            "    { \"fieldKey\": \"CREATOR_BUSINESS_TYPE\", \"label\": \"사업자 구분\", \"currentValue\": \"개인사업자\", \"requestedValue\": \"개인 (비사업자)\", \"changed\": true, \"locked\": false, \"source\": \"REQUEST\" },\n" +
                                            "    { \"fieldKey\": \"CREATOR_BUSINESS_REG_NUMBER\", \"label\": \"사업자등록번호\", \"currentValue\": \"123-45-67890\", \"requestedValue\": \"폐업\", \"changed\": true, \"locked\": false, \"source\": \"DERIVED\" },\n" +
                                            "    { \"fieldKey\": \"CREATOR_RESIDENT_NUMBER\", \"label\": \"주민등록번호\", \"currentValue\": null, \"requestedValue\": \"950812-*******\", \"changed\": true, \"locked\": false, \"source\": \"REQUEST\" },\n" +
                                            "    { \"fieldKey\": \"PAYOUT_METHOD\", \"label\": \"지급 방식\", \"currentValue\": \"세금계산서 · 리워드 + 부가세 10%\", \"requestedValue\": \"원천징수 3.3%\", \"changed\": true, \"locked\": false, \"source\": \"DERIVED\" },\n" +
                                            "    { \"fieldKey\": \"ACCOUNT_HOLDER\", \"label\": \"정산 계좌 예금주\", \"currentValue\": \"김지민\", \"requestedValue\": null, \"changed\": false, \"locked\": false, \"source\": \"CHECK\" },\n" +
                                            "    { \"fieldKey\": \"SETTLEMENT_ACCOUNT\", \"label\": \"정산 계좌\", \"currentValue\": \"국민은행 604***-**-2218\", \"requestedValue\": null, \"changed\": false, \"locked\": false, \"source\": \"LIVE\" }\n" +
                                            "  ],\n" +
                                            "  \"changedFieldLabels\": [\"사업자 구분\", \"주민등록번호\"],\n" +
                                            "  \"evidence\": { \"documentLabel\": \"폐업사실증명원\", \"fileName\": \"폐업사실증명원.pdf\", \"fileSizeBytes\": 302144, \"extension\": \"pdf\", \"fileUrl\": \"https://cdn…/creator-document/closure.pdf\", \"uploadedAt\": \"2026-08-09T10:00:00\" },\n" +
                                            "  \"referenceItems\": [ { \"label\": \"주민등록번호\", \"value\": \"950812-*******\" }, { \"label\": \"정산 계좌 예금주\", \"value\": \"김지민\" } ],\n" +
                                            "  \"holderCheck\": { \"currentHolder\": \"김지민\", \"rule\": \"REAL_NAME\", \"requestedHolder\": null, \"companyName\": null, \"mismatch\": false },\n" +
                                            "  \"compareItems\": [\n" +
                                            "    { \"key\": \"REPRESENTATIVE_NAME\", \"label\": \"대표자 성명\", \"value\": \"김지민\", \"mustBeBefore\": null, \"serverCheck\": null },\n" +
                                            "    { \"key\": \"BIRTHDAY\", \"label\": \"생년월일\", \"value\": \"1995-08-12\", \"mustBeBefore\": null, \"serverCheck\": \"MATCH\" },\n" +
                                            "    { \"key\": \"BUSINESS_REG_NUMBER\", \"label\": \"사업자등록번호\", \"value\": \"123-45-67890\", \"mustBeBefore\": null, \"serverCheck\": null },\n" +
                                            "    { \"key\": \"CLOSURE_DATE\", \"label\": \"폐업일\", \"value\": null, \"mustBeBefore\": \"2026-08-09T10:00:00\", \"serverCheck\": null }\n" +
                                            "  ],\n" +
                                            "  \"approvalImpact\": { \"appliesFrom\": \"NEXT_SETTLEMENT\", \"payoutMethodBefore\": \"TAX_INVOICE\", \"payoutMethodAfter\": \"WITHHOLDING_3_3\", \"retainedSettlements\": [], \"accountChangeRequired\": false, \"accountHolderRuleAfter\": \"REAL_NAME\" },\n" +
                                            "  \"history\": [ { \"event\": \"REQUESTED\", \"occurredAt\": \"2026-08-09T10:00:00\", \"actorLabel\": \"쇼룸 스튜디오\" } ]\n" +
                                            "}"
                            )}
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
                    "- 처리 후 브랜드 담당자 이메일로 결과 안내 메일을 발송한다\n" +
                    "- **인플루언서 건** — 사업자 구분 · 등록번호 · 등록증(증빙) 또는 주민등록번호를 반영한다. 이전 구분 정산이 읽는 값(주민번호 · " +
                    "등록번호)은 지우지 않는다. 정산은 「다음 정산부터」(생성 시점 스냅샷). 탈퇴 회원 409 `CREATOR_WITHDRAWN` · 접수 뒤 구분이 바뀌었으면 " +
                    "409 `CHANGE_REQUEST_BUSINESS_TYPE_MISMATCH` · PG 셀러가 연결된 인플루언서는 PG 재등록 준비 전까지 409 `CHANGE_REQUEST_PG_RESYNC_PENDING`\n" +
                    "- 동시에 두 번 처리하면 두 번째는 `CHANGE_REQUEST_NOT_PENDING`\n\n" +
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
                                            "  \"requester\": { \"type\": \"BRAND\", \"id\": 7, \"name\": \"코코브라운\" },\n" +
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
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "인플루언서 건만 — 탈퇴 회원 / 접수 뒤 구분이 바뀜 / PG 재등록 준비 전",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "탈퇴 회원",
                                            value = "{\n" +
                                                    "  \"code\": \"CREATOR_WITHDRAWN\",\n" +
                                                    "  \"message\": \"탈퇴한 인플루언서입니다. 조치할 수 없습니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "구분 불일치",
                                            value = "{\n" +
                                                    "  \"code\": \"CHANGE_REQUEST_BUSINESS_TYPE_MISMATCH\",\n" +
                                                    "  \"message\": \"현재 사업자 구분에서는 요청할 수 없습니다. 새로고침 후 다시 시도해 주세요.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "PG 재등록 대기",
                                            value = "{\n" +
                                                    "  \"code\": \"CHANGE_REQUEST_PG_RESYNC_PENDING\",\n" +
                                                    "  \"message\": \"PG 셀러가 연결된 인플루언서의 사업자 구분 변경은 PG 재등록 절차가 준비된 뒤 승인할 수 있습니다.\"\n" +
                                                    "}"
                                    )
                            }
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
                    "- 정형 사유 문구는 브랜드 배너·통지 메일에 그대로 노출된다\n" +
                    "- **인플루언서 건** — 사유는 `GET /reject-reasons?type=TO_BUSINESS`(C8b 6종). 정형 사유 · 상세가 스튜디오 정산 정보 탭 배너(T4a · T9a)에 " +
                    "그대로 실린다. 주민등록번호 원문(TO_INDIVIDUAL)은 반려 즉시 지운다. 반려 뒤 인플루언서는 바로 다시 요청할 수 있다\n\n" +
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
                                    name = "인플루언서 — 등록증 대표자명 불일치",
                                    value = "{\n" +
                                            "  \"reasonType\": \"LICENSE_OWNER_NAME_MISMATCH\",\n" +
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
                                            "  \"requester\": { \"type\": \"BRAND\", \"id\": 7, \"name\": \"코코브라운\" },\n" +
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
