package showroomz.api.creator.home.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.creator.home.dto.CreatorHomeDto;

@Tag(name = "Creator - Home", description = "쇼룸 스튜디오 홈(ui-studio-14-home)")
public interface CreatorHomeControllerDocs {

    @Operation(
            summary = "홈 조회",
            description = """
                    스튜디오 홈 진입 시 한 번 부릅니다.

                    | 블록 | 내용 |
                    |---|---|
                    | `account` | `GET /v1/creator/account-status` 와 같다 — `sanction != null` 이면 H5 정지 배너 |
                    | `guide` | H3 시작 가이드 — 브랜드 연결 요청을 한 번이라도 받으면 `visible = false`(숨기기 버튼 없음) |
                    | `live` | 진행 중 공구 실시간 띠 — 없으면 null(H3). 갱신은 `/home/live` |
                    | `todos` | 할 일 카드 — **서버가 정렬 · 문구를 정한다**. 정지 중 막힌 카드만 `blocked` |
                    | `upcoming` | 다가오는 일정 3건(오픈 · 종료) — `todos` 가 비면(H2) 그린다 |
                    | `reward` | 지급 예정 · 누적 수령(세후) · `payoutCondition`(「지급 조건 세금계산서 확인 후」) |
                    | `showroom7d` | 쇼룸 최근 7일 방문자 · 신규 팔로워 · 전환율 — 쇼룸 통계 7일과 같은 값 |

                    **할 일 정렬** — 세금계산서 「지급 대기」 맨 위 → 기한 이른 순(게시물 작성 · 재등록은 오픈일) → 기한 없는 일(숨김 게시물 수정) → 연결 요청 한 장 맨 아래.

                    | `kind` | 조건 | 정지 중 |
                    |---|---|---|
                    | `TAX_INVOICE_ISSUE` | 세금계산서 입력 대기 · 반려(정산 건별) | 허용 |
                    | `CONTRACT_SIGN` | 서명 진행중 · 내 서명 전 — 체결 보류 중이면 `dueLabel = 보류 중` | 허용 |
                    | `EXTENSION_RESPOND` | 진행 중 공구의 응답 대기 연장 요청 | 수락만 막힘(`blocked`) |
                    | `GROUP_BUY_POST_WRITE` | 준비중 · 게시물 미작성 · 작성중 | 등록 막힘 |
                    | `GROUP_BUY_POST_RESUBMIT` | 준비중 · 게시물 반려 | 재등록 막힘 |
                    | `GROUP_BUY_POST_HIDDEN_FIX` | 준비완료 · 진행중 · 운영자 숨김 게시물 | 허용 |
                    | `CONNECTION_REQUESTS` | 받은 연결 요청 ≥ 1 → 한 장(상위 2곳 이름 · `extra.count`) | 수락 막힘 |

                    **권한:** CREATOR"""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = CreatorHomeDto.Home.class),
                            examples = @ExampleObject(name = "할 일 2건", value = """
                                    {
                                      "account": {"status": "ACTIVE", "sanction": null, "withdrawalPending": false},
                                      "guide": {"visible": false, "checklist": [
                                        {"key": "SETTLEMENT_INFO", "done": true, "showroomUrl": null},
                                        {"key": "PROFILE", "done": true, "showroomUrl": null},
                                        {"key": "FIRST_POST", "done": true, "showroomUrl": null},
                                        {"key": "SHOWROOM_LINK", "done": null, "showroomUrl": "https://showroomz.shop/@beauty_log"}],
                                        "completed": 3, "total": 4},
                                      "live": null,
                                      "todos": [
                                        {"kind": "TAX_INVOICE_ISSUE", "dueAt": null, "dueLabel": "지급 대기",
                                         "title": "세금계산서 발행 · 밤 리페어 크림 공구",
                                         "description": "08.28 정산 자동 확정 · 세금계산서를 발행하고 승인번호를 입력하면 지급돼요",
                                         "actionLabel": "승인번호 입력", "target": {"type": "SETTLEMENT", "id": 41}, "blocked": null,
                                         "extra": {"settlementId": 41, "groupBuyName": "밤 리페어 크림 공구",
                                                   "confirmedAt": "2026-08-28T00:00:00", "amount": 602800, "rejected": false}},
                                        {"kind": "CONTRACT_SIGN", "dueAt": "2026-10-24T23:59:59", "dueLabel": "서명 기한 10.24",
                                         "title": "계약 서명 · 수분 앰플 공구",
                                         "description": "글로우랩 브랜드가 서명했어요 · 메일 · 문자로 받은 링크에서 서명해 주세요",
                                         "actionLabel": "계약 보기", "target": {"type": "CONTRACT", "id": 77}, "blocked": null,
                                         "extra": {"brandSigned": true, "brandName": "글로우랩"}}
                                      ],
                                      "upcoming": [],
                                      "reward": {"payoutScheduledAmount": 602800, "payoutScheduledDate": null,
                                                 "payoutCondition": "TAX_INVOICE_UNVERIFIED", "totalPaidNet": 4815000},
                                      "showroom7d": {"visitors": 1184, "newFollowers": 37, "conversionRate": 3.1}
                                    }""")))
    })
    ResponseEntity<CreatorHomeDto.Home> getHome();

    @Operation(
            summary = "진행 중 공구 실시간 띠",
            description = """
                    진행중 · 중단 예정 공구 1건(겹치면 먼저 시작한 공구). 없으면 `null`(H3).

                    - 판매 금액 · 주문은 **공구 관리 잠정 실적과 같은 값**(취소 · 반품 제외) — 파트너 홈의 「결제 기준 · 취소 전 포함」과 정의가 다르다
                    - `salesAmountToday` · `ordersToday` = 같은 제외 조건의 오늘(00:00 ~) 분 — 「오늘 +」
                    - `visits` · `visitsToday` = 쇼룸 방문 횟수(공구 기간 · 오늘)

                    **권한:** CREATOR"""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공(진행 중 공구가 없으면 본문 null)",
                    content = @Content(schema = @Schema(implementation = CreatorHomeDto.Live.class)))
    })
    ResponseEntity<CreatorHomeDto.Live> getLive();

    @Operation(
            summary = "월 달력",
            description = """
                    `month`(yyyy-MM · 생략 시 이번 달) 앞뒤 7일까지 내립니다. 파트너 홈 달력과 같은 모양입니다.

                    - `groupBuys` — 내 공구 막대 · `status` = IN_PROGRESS · UPCOMING · ENDED · `hold` = 승인 보류 · 오픈 보류
                    - `deadlines` — `SIGN_DUE`(내 서명 전 계약 · 체결 보류 중이면 빠진다) · `EXTENSION_RESPOND_DUE`(응답 대기 연장 요청 → 공구 종료일)
                    - 게시물 작성 · 재등록은 제출 마감 규칙이 없어 달력에 넣지 않는다

                    **권한:** CREATOR"""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = CreatorHomeDto.Calendar.class))),
            @ApiResponse(responseCode = "400", description = "INVALID_INPUT_VALUE — month 형식 오류")
    })
    ResponseEntity<CreatorHomeDto.Calendar> getCalendar(
            @Parameter(description = "조회 월(yyyy-MM) — 생략 시 이번 달", example = "2026-10") String month);
}
