package showroomz.api.seller.account.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.common.account.dto.MemberAccountStatusResponse;

@Tag(name = "Seller - Account Status", description = "파트너센터 계정 상태 API")
public interface SellerAccountStatusControllerDocs {

    @Operation(
            summary = "계정 상태 조회(정지 배너)",
            description = "모든 페이지 탑바 아래 정지 배너 · 탑바 「정지」 칩의 재료입니다. 가벼운 조회라 페이지 진입마다 불러도 됩니다.\n\n"
                    + "- `sanction == null` → 배너 없음(정상 계정은 상태 칩도 두지 않는다)\n"
                    + "- `sanction.reasonDetail` 이 있을 때만 배너 [자세히]로 상세 사유를 펼친다\n"
                    + "- `sanction.payoutHeld = true` → 배너에 정산 줄(정산은 확정까지 진행되며, 정지가 풀리면 다음 영업일에 지급)\n"
                    + "- [운영팀에 소명하기] → 연결·소통의 `operatorChannelThreadId` 스레드, `suspendCardMessageId` 카드 위치\n"
                    + "- `sanction.term` → 정지 기간 · 자동 해제 · 이의 신청 기한 줄(기간 없는 정지면 `endsOn` · `autoReleaseAt` · `periodDays` 가 null)\n\n"
                    + "**권한:** SELLER"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = MemberAccountStatusResponse.class),
                            examples = {
                                    @ExampleObject(name = "정지 · 지급 정지 사유", value = """
                                            {
                                              "status": "SUSPENDED",
                                              "sanction": {
                                                "reason": "SETTLEMENT_INFO_MISMATCH",
                                                "reasonLabel": "정산 정보 불일치",
                                                "reasonDetail": "정산 계좌 예금주가 사업자등록증 상호와 일치하지 않습니다.",
                                                "suspendedAt": "2026-10-08T14:20:00",
                                                "payoutHeld": true,
                                                "operatorChannelThreadId": 31,
                                                "suspendCardMessageId": 902,
                                                "term": {
                                                  "endsOn": "2026-10-14",
                                                  "autoReleaseAt": "2026-10-15T00:00:00",
                                                  "periodDays": 7,
                                                  "appealDeadlineAt": "2026-10-14T23:59:59",
                                                  "appealReviewBusinessDays": null
                                                }
                                              },
                                              "withdrawalPending": false
                                            }"""),
                                    @ExampleObject(name = "정상", value = """
                                            {"status": "ACTIVE", "sanction": null, "withdrawalPending": false}""")
                            })),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "SELLER · CREATOR 가 아님 — FORBIDDEN",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "판매자 · 마켓 없음 — SELLER_NOT_FOUND · MARKET_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<MemberAccountStatusResponse> getAccountStatus();
}
