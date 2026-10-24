package showroomz.api.admin.home.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.admin.home.dto.AdminHomeDto;
import showroomz.api.app.auth.DTO.ErrorResponse;

@Tag(name = "Admin - Home", description = "관리자 홈 API")
public interface AdminHomeControllerDocs {

    @Operation(
            summary = "홈 조회",
            description = """
                    어드민 홈 한 화면 — FE 가 5분마다 다시 부릅니다.

                    | 블록 | 내용 |
                    |---|---|
                    | `integrations` | 연동 3종 — PG(결제 운영 지표 임계 초과면 `DEGRADED`) · 택배 추적(꺼짐 `DISABLED` · 호출 장애 `DEGRADED` — 이 동안 **배송 예외 판정 · 집계 일시 중지**, `affectedQueues`) · 전자서명(`NOT_INTEGRATED` · 수동) |
                    | `todo` | 할 일 — `total` = 사이드바 배지 합(회원 수 제외). `failed · overdue · dueSoon(집행 대기 + 기한 임박) · waiting` 으로 나뉘고 `rows` 는 급한 순 |
                    | `todaySchedule` | 오늘 일정 — 공구 오픈 · 종료 · 정산 지급(배치 시각) · 확인 마감 · 조정 기한 · 직권 중단 집행 예정(**운영자 집행 필요**) |
                    | `metricsToday` | 주문 · 결제 거래액(취소 포함) · 신규 가입 · 반품·교환 접수 — 오늘 vs 어제 같은 시각 |

                    **할 일 행** — 실패(환불 · 분배) · 기한 초과(1:1 문의 24시간 · 오픈 승인 · 입점 심사 · 세금계산서 발행 마감) · 집행 대기(사유 환불) · 기한 임박(마감이 다음 영업일 이내 · 세금계산서는 5영업일 이내)은 **건별 행**,
                    나머지는 큐별 대기 행 하나(`count` · 가장 오래된 건의 `ageText`). 건별 행이 20건을 넘으면 「외 N건」 행으로 접습니다.
                    `target.type = QUEUE` 면 `filter` 의 목록으로 이동합니다.

                    `processedToday` 는 처리 시각이 기록되는 큐만 합산합니다(`note`).

                    **권한:** ADMIN"""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = AdminHomeDto.Home.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminHomeDto.Home> getHome();

    @Operation(
            summary = "사이드바 배지",
            description = """
                    사이드바 메뉴 숫자를 한 번에 — 홈 할 일과 **같은 큐 계산**이라 합계가 어긋나지 않습니다. 0 이면 FE 가 숨깁니다.

                    | 배지 | 큐 |
                    |---|---|
                    | `onboarding` | 브랜드 · 인플루언서 입점 심사 대기 + 정보 변경 요청 검토 |
                    | `members.todo` | 탈퇴 승인 검토(브랜드 + 인플루언서) — `brandCount · creatorCount · userCount` 는 회원 수(탈퇴 제외) |
                    | `contracts` | 계약 검토 대기 · 공구 생성 대기 · 만료 확인 · 서명 안내 재발송 요청 · 해지 요청(계약 요약 `actionRequiredCount` 와 같다) |
                    | `groupBuys` | 오픈 승인 · 중단 요청 · 조기 마감 요청 · 소명 검토(공구 요약 `actionRequiredCount` 와 같다) |
                    | `transactions` | 환불 실패 · 사유 환불 집행 대기 · 처리 지연 · 배송 예외(배지 범위 ALL 일 때) · 반려 이의 |
                    | `settlements` | 분배 실패 · 세금계산서 발행 · 대조 · 차감 미회수(정산 요약 배지와 같다) |
                    | `cs` | 1:1 문의 답변 대기 + 상품 문의 삭제 요청 |
                    | `posts` | 신고 접수 + 이의 신청 심사 중 |

                    **권한:** ADMIN"""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = AdminHomeDto.Badges.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "onboarding": {"total": 5, "brand": 2, "creator": 2, "changeRequests": 1},
                                      "members": {"todo": 1, "brandCount": 42, "creatorCount": 130, "userCount": 5210},
                                      "contracts": 5, "groupBuys": 4, "transactions": 9, "settlements": 3,
                                      "cs": {"total": 7, "inquiries": 6},
                                      "posts": {"total": 2, "general": 2}
                                    }"""))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AdminHomeDto.Badges> getBadges();
}
