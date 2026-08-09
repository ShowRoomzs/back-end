package showroomz.api.seller.changerequest.docs;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.seller.changerequest.dto.ChangeRequestBannerResponse;
import showroomz.api.seller.changerequest.dto.ChangeRequestCreateResponse;
import showroomz.api.seller.changerequest.dto.ChangeRequestFieldOption;
import showroomz.api.seller.changerequest.dto.CreateChangeRequestRequest;
import showroomz.domain.changerequest.type.ChangeRequestType;

import java.util.List;

@Tag(name = "Seller - Change Request", description = "파트너센터 브랜드 회원정보(사업자 정보·정산 계좌) 변경 요청 API (§15-6·§15-7)")
public interface SellerChangeRequestControllerDocs {

    @Operation(
            summary = "변경 요청 생성 (M1·M2 공통)",
            description = "사업자 정보(`BUSINESS_INFO`) 또는 정산 계좌(`SETTLEMENT_ACCOUNT`) 변경을 요청한다.\n\n" +
                    "- 동일 (브랜드, 유형)에 PENDING 요청이 있으면 409(`CHANGE_REQUEST_ALREADY_PENDING`)\n" +
                    "- 요청값이 현재값과 같으면 400(`CHANGE_REQUEST_VALUE_UNCHANGED`)\n" +
                    "- 사업자등록번호 등 enum에 없는 항목은 역직렬화 단계에서 400\n" +
                    "- `BUSINESS_INFO`는 변경 사유·증빙(사업자등록증) 필수, `SETTLEMENT_ACCOUNT`는 은행·계좌번호·예금주 3항목 고정 + 통장 사본 필수\n" +
                    "- 증빙은 기존 `POST /v1/seller/images` (`CHANGE_REQUEST_DOCUMENT`) 업로드 결과를 사용\n\n" +
                    "**권한:** SELLER"
    )
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = CreateChangeRequestRequest.class),
                    examples = {
                            @ExampleObject(
                                    name = "사업자 정보 변경(M1)",
                                    value = "{\n" +
                                            "  \"type\": \"BUSINESS_INFO\",\n" +
                                            "  \"items\": [\n" +
                                            "    { \"fieldKey\": \"REPRESENTATIVE_NAME\", \"requestedValue\": \"이대표\" },\n" +
                                            "    { \"fieldKey\": \"BUSINESS_ADDRESS\", \"requestedValue\": \"서울시 강남구 테헤란로 123\" }\n" +
                                            "  ],\n" +
                                            "  \"reason\": \"대표자 변경 및 사업장 이전\",\n" +
                                            "  \"evidenceFileUrl\": \"https://s3.ap-northeast-2.amazonaws.com/bucket/change-request/biz.jpg\",\n" +
                                            "  \"evidenceFileName\": \"사업자등록증_변경.jpg\",\n" +
                                            "  \"evidenceFileSize\": 1258291\n" +
                                            "}"
                            ),
                            @ExampleObject(
                                    name = "정산 계좌 변경(M2)",
                                    value = "{\n" +
                                            "  \"type\": \"SETTLEMENT_ACCOUNT\",\n" +
                                            "  \"items\": [\n" +
                                            "    { \"fieldKey\": \"BANK_CODE\", \"requestedValue\": \"088\" },\n" +
                                            "    { \"fieldKey\": \"ACCOUNT_NUMBER\", \"requestedValue\": \"110123456789\" },\n" +
                                            "    { \"fieldKey\": \"ACCOUNT_HOLDER\", \"requestedValue\": \"(주)코코브라운\" }\n" +
                                            "  ],\n" +
                                            "  \"reason\": null,\n" +
                                            "  \"evidenceFileUrl\": \"https://s3.ap-northeast-2.amazonaws.com/bucket/change-request/bank.jpg\",\n" +
                                            "  \"evidenceFileName\": \"통장사본.jpg\",\n" +
                                            "  \"evidenceFileSize\": 845120\n" +
                                            "}"
                            )
                    }
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "접수 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ChangeRequestCreateResponse.class),
                            examples = @ExampleObject(
                                    name = "성공",
                                    value = "{\n" +
                                            "  \"requestId\": 12,\n" +
                                            "  \"requestCode\": \"CHG-2026-0001\",\n" +
                                            "  \"type\": \"BUSINESS_INFO\",\n" +
                                            "  \"status\": \"PENDING\",\n" +
                                            "  \"requestedAt\": \"2026-08-09T14:22:10\",\n" +
                                            "  \"notifyEmail\": \"seller@example.com\"\n" +
                                            "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "검증 실패(항목 없음/사유 누락/증빙 누락/값 동일 등)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "현재값과 동일",
                                            value = "{\n" +
                                                    "  \"code\": \"CHANGE_REQUEST_VALUE_UNCHANGED\",\n" +
                                                    "  \"message\": \"현재 값과 동일한 항목은 요청할 수 없습니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "증빙 누락",
                                            value = "{\n" +
                                                    "  \"code\": \"CHANGE_REQUEST_EVIDENCE_REQUIRED\",\n" +
                                                    "  \"message\": \"증빙 서류를 첨부해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 검토 중인 요청 존재",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "중복 PENDING",
                                    value = "{\n" +
                                            "  \"code\": \"CHANGE_REQUEST_ALREADY_PENDING\",\n" +
                                            "  \"message\": \"이미 검토 중인 변경 요청이 있습니다.\"\n" +
                                            "}"
                            )
                    )
            )
    })
    ResponseEntity<ChangeRequestCreateResponse> create(@Valid @org.springframework.web.bind.annotation.RequestBody CreateChangeRequestRequest request);

    @Operation(
            summary = "현재 배너 상태 조회",
            description = "기본정보 화면 상단 배너용이다.\n\n" +
                    "- `PENDING`이거나, 처리완료(`APPROVED`/`REJECTED`)인데 아직 [확인]하지 않은 최신 1건을 반환\n" +
                    "- 해당 없음이면 200 + 빈 본문(`null`)\n" +
                    "- `PENDING`이면 `cancelable=true`([요청 취소] 노출)\n" +
                    "- `SETTLEMENT_ACCOUNT`는 `requestedAccount`(마스킹 계좌)를 함께 내려준다\n\n" +
                    "**권한:** SELLER"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공(없으면 null)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ChangeRequestBannerResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "검토 대기",
                                            value = "{\n" +
                                                    "  \"requestId\": 12,\n" +
                                                    "  \"requestCode\": \"CHG-2026-0001\",\n" +
                                                    "  \"type\": \"BUSINESS_INFO\",\n" +
                                                    "  \"status\": \"PENDING\",\n" +
                                                    "  \"changedFieldLabels\": [\"대표자명\", \"사업장 주소\"],\n" +
                                                    "  \"requestedAt\": \"2026-08-09T14:22:10\",\n" +
                                                    "  \"processedAt\": null,\n" +
                                                    "  \"cancelable\": true,\n" +
                                                    "  \"rejectReason\": null,\n" +
                                                    "  \"rejectReasonDetail\": null,\n" +
                                                    "  \"requestedAccount\": null\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "반려 결과(미확인)",
                                            value = "{\n" +
                                                    "  \"requestId\": 12,\n" +
                                                    "  \"requestCode\": \"CHG-2026-0001\",\n" +
                                                    "  \"type\": \"BUSINESS_INFO\",\n" +
                                                    "  \"status\": \"REJECTED\",\n" +
                                                    "  \"changedFieldLabels\": [\"대표자명\"],\n" +
                                                    "  \"requestedAt\": \"2026-08-09T14:22:10\",\n" +
                                                    "  \"processedAt\": \"2026-08-10T11:05:00\",\n" +
                                                    "  \"cancelable\": false,\n" +
                                                    "  \"rejectReason\": \"변경 사유 불충분\",\n" +
                                                    "  \"rejectReasonDetail\": null,\n" +
                                                    "  \"requestedAccount\": null\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "정산 계좌 검토 대기",
                                            value = "{\n" +
                                                    "  \"requestId\": 15,\n" +
                                                    "  \"requestCode\": \"CHG-2026-0004\",\n" +
                                                    "  \"type\": \"SETTLEMENT_ACCOUNT\",\n" +
                                                    "  \"status\": \"PENDING\",\n" +
                                                    "  \"changedFieldLabels\": [\"은행\", \"계좌번호\", \"예금주\"],\n" +
                                                    "  \"requestedAt\": \"2026-08-09T16:00:00\",\n" +
                                                    "  \"processedAt\": null,\n" +
                                                    "  \"cancelable\": true,\n" +
                                                    "  \"rejectReason\": null,\n" +
                                                    "  \"rejectReasonDetail\": null,\n" +
                                                    "  \"requestedAccount\": {\n" +
                                                    "    \"bankName\": \"신한은행\",\n" +
                                                    "    \"maskedAccountNumber\": \"110***456789\"\n" +
                                                    "  }\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<ChangeRequestBannerResponse> getLatest(
            @Parameter(description = "요청 유형 — BUSINESS_INFO / SETTLEMENT_ACCOUNT", required = true, example = "BUSINESS_INFO")
            @RequestParam("type") ChangeRequestType type);

    @Operation(
            summary = "모달 진입용 항목 목록",
            description = "M1·M2 모달의 체크박스 항목·라벨·현재값을 내려준다.\n\n" +
                    "- `BUSINESS_INFO`: 브랜드명·대표자명·상호·업태·사업장 주소·통신판매업 신고번호 (6종)\n" +
                    "- `SETTLEMENT_ACCOUNT`: 은행·계좌번호·예금주 (3종)\n" +
                    "- 사업자등록번호는 변경 불가이므로 이 목록에 없다(§15-1 ③)\n\n" +
                    "**권한:** SELLER"
    )
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(
                    mediaType = "application/json",
                    examples = {
                            @ExampleObject(
                                    name = "사업자 정보 항목",
                                    value = "[\n" +
                                            "  { \"fieldKey\": \"MARKET_NAME\", \"label\": \"브랜드명\", \"currentValue\": \"코코브라운\" },\n" +
                                            "  { \"fieldKey\": \"REPRESENTATIVE_NAME\", \"label\": \"대표자명\", \"currentValue\": \"김대표\" },\n" +
                                            "  { \"fieldKey\": \"COMPANY_NAME\", \"label\": \"사업자등록증 상호\", \"currentValue\": \"(주)코코브라운\" },\n" +
                                            "  { \"fieldKey\": \"BUSINESS_CONDITION\", \"label\": \"업태\", \"currentValue\": \"도소매\" },\n" +
                                            "  { \"fieldKey\": \"BUSINESS_ADDRESS\", \"label\": \"사업장 주소\", \"currentValue\": \"서울시 서초구 ...\" },\n" +
                                            "  { \"fieldKey\": \"MAIL_ORDER_REG_NUMBER\", \"label\": \"통신판매업 신고번호\", \"currentValue\": \"2024-서울서초-0001\" }\n" +
                                            "]"
                            ),
                            @ExampleObject(
                                    name = "정산 계좌 항목",
                                    value = "[\n" +
                                            "  { \"fieldKey\": \"BANK_CODE\", \"label\": \"은행\", \"currentValue\": \"088\" },\n" +
                                            "  { \"fieldKey\": \"ACCOUNT_NUMBER\", \"label\": \"계좌번호\", \"currentValue\": \"110987654321\" },\n" +
                                            "  { \"fieldKey\": \"ACCOUNT_HOLDER\", \"label\": \"예금주\", \"currentValue\": \"(주)코코브라운\" }\n" +
                                            "]"
                            )
                    }
            )
    )
    ResponseEntity<List<ChangeRequestFieldOption>> getFields(
            @Parameter(description = "요청 유형 — BUSINESS_INFO / SETTLEMENT_ACCOUNT", required = true, example = "BUSINESS_INFO")
            @RequestParam("type") ChangeRequestType type);

    @Operation(
            summary = "요청 취소",
            description = "PENDING 상태인 본인 마켓 요청만 취소할 수 있다. 취소된 행은 `CANCELED`로 보존된다(감사 기록).\n\n" +
                    "**권한:** SELLER(본인 마켓 요청만)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "취소 성공 (본문 없음)"),
            @ApiResponse(
                    responseCode = "400",
                    description = "PENDING 상태가 아님",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "PENDING 아님",
                                    value = "{\n" +
                                            "  \"code\": \"CHANGE_REQUEST_NOT_PENDING\",\n" +
                                            "  \"message\": \"검토 대기 상태인 요청만 처리할 수 있습니다.\"\n" +
                                            "}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "본인 마켓의 요청이 아님",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "권한 없음",
                                    value = "{\n" +
                                            "  \"code\": \"CHANGE_REQUEST_ACCESS_DENIED\",\n" +
                                            "  \"message\": \"해당 변경 요청에 대한 권한이 없습니다.\"\n" +
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
    ResponseEntity<Void> cancel(@Parameter(description = "요청 ID", required = true, example = "12") @PathVariable("requestId") Long requestId);

    @Operation(
            summary = "결과 배너 확인",
            description = "처리완료 배너의 [확인] 버튼 — `result_acknowledged_at`을 기록해 배너를 닫고 재요청을 연다.\n\n" +
                    "**권한:** SELLER(본인 마켓 요청만)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "확인 성공 (본문 없음)"),
            @ApiResponse(
                    responseCode = "403",
                    description = "본인 마켓의 요청이 아님",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "권한 없음",
                                    value = "{\n" +
                                            "  \"code\": \"CHANGE_REQUEST_ACCESS_DENIED\",\n" +
                                            "  \"message\": \"해당 변경 요청에 대한 권한이 없습니다.\"\n" +
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
    ResponseEntity<Void> acknowledge(@Parameter(description = "요청 ID", required = true, example = "12") @PathVariable("requestId") Long requestId);
}
