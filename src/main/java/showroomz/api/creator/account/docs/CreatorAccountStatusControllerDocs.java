package showroomz.api.creator.account.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.common.account.dto.MemberAccountStatusResponse;

@Tag(name = "Creator - Account Status", description = "쇼룸 스튜디오 계정 상태 — 정지 배너(ui-studio-13 · 14 H5)")
public interface CreatorAccountStatusControllerDocs {

    @Operation(
            summary = "계정 상태 조회(정지 배너)",
            description = "모든 페이지 탑바 아래 정지 배너 · 탑바 「정지」 칩의 재료입니다.\n\n"
                    + "- `sanction == null` → 배너 없음\n"
                    + "- `sanction.reasonDetail` 이 있을 때만 [자세히]로 펼친다\n"
                    + "- `sanction.payoutHeld = true` → 배너 정산 줄(확정까지 진행되며, 내 리워드는 정지가 풀리면 다음 영업일에 지급)\n"
                    + "- [운영팀에 소명하기] → `operatorChannelThreadId` 스레드의 `suspendCardMessageId` 카드\n"
                    + "- `withdrawalPending` → 탈퇴 심사 중(새 연결 수락 · 계약 서명 제한)\n\n"
                    + "**권한:** CREATOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = MemberAccountStatusResponse.class),
                            examples = @ExampleObject(name = "정지 · 지급 영향 없음", value = """
                                    {
                                      "status": "SUSPENDED",
                                      "sanction": {
                                        "reason": "AD_LAW_VIOLATION_REPEATED",
                                        "reasonLabel": "표시광고법 위반 문구 반복",
                                        "reasonDetail": "공구 게시물에 근거 없는 효능 표현이 있습니다.",
                                        "suspendedAt": "2026-10-08T14:20:00",
                                        "payoutHeld": false,
                                        "operatorChannelThreadId": 44,
                                        "suspendCardMessageId": 1203
                                      },
                                      "withdrawalPending": false
                                    }""")))
    })
    ResponseEntity<MemberAccountStatusResponse> getAccountStatus();
}
