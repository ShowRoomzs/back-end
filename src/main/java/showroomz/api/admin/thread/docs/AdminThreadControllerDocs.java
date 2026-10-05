package showroomz.api.admin.thread.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.admin.thread.dto.AdminThreadDto.ChannelInfo;
import showroomz.api.admin.thread.dto.AdminThreadDto.ChannelListItem;
import showroomz.api.admin.thread.dto.AdminThreadDto.MessageItem;
import showroomz.api.admin.thread.dto.AdminThreadDto.MessageList;
import showroomz.api.admin.thread.dto.AdminThreadDto.ResendNoticeResponse;
import showroomz.api.admin.thread.dto.AdminThreadDto.SendRequest;
import showroomz.api.admin.thread.dto.AdminThreadDto.Summary;
import showroomz.api.admin.thread.type.AdminChannelTab;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.app.auth.entity.UserPrincipal;
import showroomz.api.common.attachment.dto.AttachmentDownloadRequest;
import showroomz.api.common.attachment.dto.AttachmentDownloadResponse;
import showroomz.api.common.attachment.dto.AttachmentSummary;
import showroomz.api.common.attachment.dto.CompleteAttachmentRequest;
import showroomz.api.common.attachment.dto.PresignRequest;
import showroomz.api.common.attachment.dto.PresignResponse;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

import java.util.List;

import static showroomz.api.admin.thread.docs.AdminThreadDocsExamples.*;

@Tag(name = "Admin - Thread", description = """
        관리자 소통 스레드 API""")
public interface AdminThreadControllerDocs {

    // ── 목록 · 배지 ──────────────────────────────────────────────────────────

    @Operation(summary = "운영팀 채널 목록", description = """
            탭별 운영팀 1:1 채널을 최근 메시지순으로 반환합니다.

            **권한:** ADMIN

            모든 브랜드 · 인플루언서는 가입 시점에 운영팀 채널이 하나씩 미리 만들어집니다. 그래서 「새 대화」 버튼이 없고,
            **메시지가 한 건도 없는 채널도 목록에 나옵니다**(맨 아래, `lastMessageAt = null`). 상대를 찾는 방법은 검색입니다.

            **탭 `tab`** — 필수. 없거나 아래 외의 값이면 400입니다.

            | 값 | 채널 상대 | `name` | `imageUrl` | `memberNo` |
            |---|---|---|---|---|
            | `BRAND` | 브랜드(마켓) | 브랜드명 | 브랜드 대표 이미지 | `BRD-{마켓 ID}` |
            | `INFLUENCER` | 인플루언서(크리에이터) | 쇼룸명 | 프로필 이미지 | `INF-{크리에이터 ID}` |

            이슈 스레드(공구 3자 스레드) 탭은 추후 기획이라 이 API에 없습니다.

            **검색 `keyword`** — 앞뒤 공백은 무시합니다.
            - `BRAND` 탭: 브랜드명 **또는** 판매 담당자 이름 부분 일치
            - `INFLUENCER` 탭: 쇼룸명 부분 일치
            - **회원번호 검색:** 그 탭의 접두사(`BRD-` · `INF-`, 대소문자 무시)로 시작하면 이름 검색을 하지 않고 회원번호로만 찾습니다.
              접두사 뒤가 숫자가 아니면(`BRD-abc`) **전체 목록이 아니라 0건**입니다. 다른 탭의 접두사(`BRAND` 탭에 `INF-3021`)는 이름 검색으로 처리됩니다.

            **정렬** — 최근 메시지 시각 내림차순 고정. 메시지가 없는 채널은 맨 뒤입니다.

            **페이징** — `page`(1부터, 기본 1) · `size`(기본 20).

            **행 그리기**
            - 보조 줄: 브랜드는 `managerName`으로 「담당 {이름}」, 인플루언서는 `businessType`으로 「사업자」(`BUSINESS`) / 「개인」(`INDIVIDUAL`).
              해당 없는 쪽 값은 null입니다.
            - 미리보기: `lastMessagePreview`. 마지막 메시지가 시스템 카드면 카드 제목, 첨부만 보낸 메시지면 첫 첨부 종류로 「사진」 · 「동영상 2개」 · 「파일 3개」입니다.
              `lastMessageByOperator = true`면 앞에 「운영팀: 」을 붙입니다(운영자 말풍선 · 자동 안내 · 직권 취소 카드).
              재발송 요청 카드는 상대가 만든 카드라 false입니다.
            - `unreadCount`: **운영팀 공용** 안 읽은 수입니다. 어느 운영자가 읽든 모두에게서 함께 줄어듭니다.
            - `memberStatus`: `ACTIVE` · `DORMANT` · `SUSPENDED` · `WITHDRAWN`. 인플루언서의 `NORMAL`은 `ACTIVE`로 맞춰 내립니다.
            - `writable = false`(탈퇴 회원)면 열람만 됩니다. 입력창 · 첨부 버튼을 닫습니다. **정지 회원은 쓸 수 있습니다** — 정지 사유를 이 채널에서 설명해야 하기 때문입니다.

            **갱신** — 실시간 푸시가 없습니다. 탭 전환 · 검색 · 메시지 전송 후 다시 조회합니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "채널 목록과 페이지 정보",
                    content = @Content(schema = @Schema(implementation = PageResponse.class), examples = {
                            @ExampleObject(name = "브랜드 탭", summary = "안 읽음 2 · 정지 회원(쓰기 가능) · 메시지 없는 채널(맨 아래)", value = LIST_BRAND),
                            @ExampleObject(name = "인플루언서 탭", summary = "마지막이 재발송 요청 카드 · 탈퇴 회원(writable=false)", value = LIST_INFLUENCER),
                            @ExampleObject(name = "결과 없음", summary = "keyword=BRD-abc 처럼 접두사 뒤가 숫자가 아닌 경우 포함", value = LIST_EMPTY)
                    })),
            @ApiResponse(responseCode = "400", description = "`tab` 누락 · 없는 `tab` 값 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_INVALID_INPUT))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    PageResponse<ChannelListItem> list(
            @Parameter(description = "탭 — BRAND · INFLUENCER (필수)", example = "BRAND", required = true) AdminChannelTab tab,
            @Parameter(description = "검색어 — 브랜드명 · 담당자명 · 쇼룸명 부분 일치, 또는 회원번호(BRD-1017 · INF-3021)", example = "무드") String keyword,
            PagingRequest paging);

    @Operation(summary = "탭 배지", description = """
            두 탭의 배지 값을 한 번에 내립니다. **파라미터를 받지 않습니다** — 검색어와 무관한 전체 기준입니다.

            **권한:** ADMIN

            **폴링 대상입니다.** 실시간 푸시가 없으므로 어드민 화면에 있는 동안 30~60초 간격으로 호출합니다.
            채널 수와 관계없이 쿼리 2회로 끝나는 가벼운 API입니다.

            | 필드 | 뜻 |
            |---|---|
            | `unreadCount` | 그 탭 전체 채널의 **운영팀 기준** 안 읽은 메시지 수 합 |
            | `pendingCardCount` | 운영팀 확인을 기다리는 재발송 요청 카드 수 — 알림 전 ∧ 계약이 아직 서명 진행중(`SIGNING`)인 것만 센다 |

            - 탭 숫자로 무엇을 보여줄지 확정되기 전이라 두 값을 함께 내립니다. FE가 고르거나 합쳐 씁니다.
            - `pendingCardCount`는 요청자 기준으로 나뉩니다 — 브랜드가 요청하면 `brand`, 인플루언서가 요청하면 `influencer`.
            - 계약이 서명 단계를 벗어나 닫힌 카드(`CLOSED`)는 세지 않습니다. 계약 관리의 재발송 큐와 같은 정의입니다.
            - `issue`는 이슈 스레드 기획 전까지 **항상 null**입니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "탭별 배지 값",
                    content = @Content(schema = @Schema(implementation = Summary.class), examples = {
                            @ExampleObject(name = "처리할 것 있음", summary = "브랜드 안 읽음 3 · 인플루언서 안 읽음 2 + 재발송 요청 1", value = SUMMARY),
                            @ExampleObject(name = "처리할 것 없음", summary = "배지를 그리지 않는다", value = SUMMARY_EMPTY)
                    })),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    Summary summary();

    // ── 스레드 ───────────────────────────────────────────────────────────────

    @Operation(summary = "스레드 헤더 · 접이식 정보 바", description = """
            스레드 헤더와 정보 바 3칸(① 정보 · ② 진행 중 · ③ 열린 이슈 스레드)입니다.
            정보 바가 접혀 있어도 요약 줄이 보이므로 **스레드 진입 시 1회** 호출합니다.

            **권한:** ADMIN

            **헤더** — `name` · `imageUrl` · `memberNo` · `memberStatus`는 목록 행과 같은 규칙입니다.
            `memberId`는 회원 상세 링크용입니다(브랜드 = 마켓 ID · 인플루언서 = 크리에이터 ID).
            `writable = false`면 입력창을 닫습니다.

            **① 정보 `profile`** — 브랜드와 인플루언서가 같은 골격이고 채우는 칸만 다릅니다. 해당 없는 칸은 null입니다.

            | 필드 | 브랜드 | 인플루언서 |
            |---|---|---|
            | `managerName` · `managerContact` | 판매 담당자 이름 · 연락처 | null |
            | `businessType` | null | `BUSINESS` 사업자 · `INDIVIDUAL` 개인 |
            | `taxType` | null | **항상 null** — 저장 값이 없다 |
            | `businessEmail` · `instagramUrl` | null | 업무용 이메일 · 인스타그램 링크 |
            | `joinedAt` | 판매자 가입일 | 인플루언서 등록일 |

            **② 진행 중 `progress`** — 각 모듈의 집계를 그대로 읽습니다. **null은 0이 아니라 「집계할 수 없음 / 해당 없음」입니다 — 그 행을 그리지 않습니다.**
            0은 「없다」는 사실이므로 그대로 0으로 그립니다.

            | 필드 | 브랜드 | 인플루언서 |
            |---|---|---|
            | `contractSigning` | 서명 진행중 + 체결 처리 대기 계약 수 | 같음 |
            | `contractConcluded` | 체결 완료 계약 수 | 같음 |
            | `groupBuyOngoing` | 진행중 공구 수 | 같음 |
            | `groupBuyEnded` | null | 종료 + 정산완료 공구 수 |
            | `unsettledCount` | **항상 null** — 정산 모듈 없음 | null |
            | `connectedBrandCount` | null | 연결 중인 브랜드 수 |

            인플루언서의 계약 수는 스튜디오에 도착한 계약(서명 진행중 이후)만 셉니다 — 브랜드가 작성 · 검토 중인 계약은 세지 않습니다.

            **③ 열린 이슈 스레드 `openIssueThreads`** — 그 회원이 당사자인 열린 공구 3자 스레드입니다(`GROUP_BUY_ISSUE` · `GROUP_BUY_FULFILLMENT`).
            이슈 스레드 화면이 기획 전이라 **목록만 내립니다** — 이 스레드 ID로 아래 메시지 API를 호출하면 403입니다. 없으면 빈 배열입니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "헤더와 정보 바",
                    content = @Content(schema = @Schema(implementation = ChannelInfo.class), examples = {
                            @ExampleObject(name = "브랜드", summary = "담당자 · 연락처 / 미정산 null / 이행 이견 3자 스레드 1건", value = INFO_BRAND),
                            @ExampleObject(name = "인플루언서", summary = "사업자 · 이메일 · 인스타 / 종료 공구 · 연결 브랜드 수", value = INFO_INFLUENCER),
                            @ExampleObject(name = "탈퇴 회원", summary = "writable=false — 열람만", value = INFO_WITHDRAWN)
                    })),
            @ApiResponse(responseCode = "403", description = "운영팀 1:1 채널이 아닌 스레드 — 브랜드↔인플루언서 쌍 스레드 · 공구 3자 스레드 (`THREAD_ACCESS_DENIED`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_THREAD_ACCESS_DENIED))),
            @ApiResponse(responseCode = "404", description = "스레드 없음 (`THREAD_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_THREAD_NOT_FOUND)))
    })
    ChannelInfo info(@Parameter(description = "스레드 ID — 목록의 threadId", example = "55") Long threadId);

    @Operation(summary = "메시지 조회", description = """
            말풍선과 시스템 카드를 **최신순**(messageId 내림차순)으로 반환합니다. 화면에는 뒤집어서 아래가 최신이 되게 그립니다.

            **권한:** ADMIN

            **커서 페이징**
            - 첫 페이지는 `cursor`를 생략합니다. 위로 스크롤하면 직전 응답의 `nextCursor`를 넣어 더 오래된 메시지를 읽습니다.
            - `cursor`는 그 messageId **미만**을 읽습니다(경계 메시지는 중복되지 않습니다).
            - `size` 기본 30. 생략하거나 0 이하이면 30입니다.
            - `hasNext = false`면 `nextCursor = null`입니다.
            - 조회는 읽음 처리를 하지 않습니다 — 화면에 보이면 `POST /read`를 따로 호출합니다.

            **메시지 종류 `messageType`**

            | 값 | 그리기 | 읽을 필드 |
            |---|---|---|
            | `TEXT` | 말풍선 — `mine`이면 오른쪽 | `content` · `attachments` |
            | `SYSTEM` | 가운데 정렬 카드 | `card` (`content`는 카드 제목과 같다) |

            **발신 표시**

            | 경우 | `senderType` | `mine` | `senderName` | `operatorName` | `autoNotice` |
            |---|---|---|---|---|---|
            | 상대(브랜드 · 인플루언서) 말풍선 | `SELLER` · `CREATOR` | false | 브랜드명 · 쇼룸명 | null | false |
            | 운영자 말풍선 | `ADMIN` | true | null | 작성 운영자 이름 | false |
            | 재발송 완료 자동 안내 | `ADMIN` | true | null | 누른 운영자 이름 | **true** |
            | 시스템 발송(가입 환영 등) | `ADMIN` | true | null | **null** | **true** |
            | 재발송 요청 카드 | 요청자(`SELLER` · `CREATOR`) | false | 요청자 이름 | null | false |
            | 직권 취소 결과 카드 | `ADMIN` | false | null | null | false |

            - `mine`은 **운영자 개인이 아니라 운영팀 기준**입니다 — 다른 운영자의 말풍선도 true입니다. 카드는 항상 false입니다.
            - 운영자 말풍선은 「운영팀 · {operatorName}」으로, `operatorName`이 null이면 「운영팀」으로 그립니다.
            - `autoNotice = true`면 「자동 안내」 태그를 붙입니다.
            - **운영자 이름 · 자동 안내 태그는 어드민에만 내려갑니다.** 상대(파트너센터 · 스튜디오)에게는 모두 「SHOWROOMZ 운영팀」의 일반 메시지입니다.
            - 운영자 이름은 메시지 당시가 아니라 **현재 이름**입니다.

            **첨부 `attachments`** — `sortOrder` 순서입니다. 이미지 · 영상은 `fileUrl`로 미리보기하고,
            파일 받기는 `POST /v1/admin/attachments/download`로 URL을 발급받습니다(`fileUrl`로 직접 받지 않습니다).

            **시스템 카드 `card`** — 계약 관리의 조치에서만 생깁니다. 이 화면에서 만들 수 없습니다.

            | `cardType` | 언제 생기나 | `detail` | `action` |
            |---|---|---|---|
            | `CONTRACT_RESEND_REQUEST` 「요청 · 서명 안내 다시 받기」 | 브랜드 · 인플루언서가 계약의 [서명 안내 다시 받기]를 누름 → 요청자 채널 | `requesterType` · `requesterName` · `requestedAt` | 있음 |
            | `CONTRACT_ADMIN_CANCELED` 「계약 직권 취소 처리됨」 | 운영자가 계약 관리에서 [계약 취소] → 요청이 들어온 채널 | `reasonLabel` · `processedAt` · `processedByName` · `notifiedBothParties` | null |

            - `detail`은 **값이 있는 필드만** 내려갑니다(카드 종류마다 키가 다릅니다).
            - `contractId`로 계약 상세 링크를 겁니다. `reasonLabel`은 「사유 라벨 — 메모」 형식이며 당사자에게도 같은 문구가 보입니다.
            - `tone`: `WARNING`(운영자 조치가 남은 요청 = `PENDING`) · `NEUTRAL`(그 외 전부)

            **재발송 요청 카드의 `action.state`**

            | 값 | 뜻 | 그리기 |
            |---|---|---|
            | `PENDING` | 알림 전 ∧ 계약 서명 진행중 | [재발송 완료 알림 보내기] 버튼 (`canExecute = true`) |
            | `DONE` | 알림 전송됨 | 「전송됨 · {doneAt} · {doneByName}」 — `noticeMessageId`가 자동 안내 말풍선 |
            | `CLOSED` | 알림 전에 계약이 서명 단계를 벗어남(체결 · 취소 등) | 버튼 없음 — 다시 보낼 안내가 없다 |

            카드 상태는 조회 시점의 계약 상태로 계산합니다. 같은 카드라도 다시 조회하면 `PENDING` → `CLOSED`로 바뀔 수 있습니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "메시지 목록",
                    content = @Content(schema = @Schema(implementation = MessageList.class), examples = {
                            @ExampleObject(name = "재발송 요청 대기", summary = "PENDING 카드 · 첨부 2개 말풍선 · 운영자 말풍선 · 가입 환영(시스템 발송)", value = MESSAGES_PENDING_CARD),
                            @ExampleObject(name = "재발송 알림 전송 후", summary = "DONE 카드 + 자동 안내 말풍선 · 다음 페이지 있음", value = MESSAGES_DONE_CARD),
                            @ExampleObject(name = "닫힌 요청 · 직권 취소", summary = "CLOSED 카드 · 직권 취소 결과 카드", value = MESSAGES_CLOSED_AND_CANCELED),
                            @ExampleObject(name = "메시지 없음", value = MESSAGES_EMPTY)
                    })),
            @ApiResponse(responseCode = "403", description = "운영팀 1:1 채널이 아닌 스레드 (`THREAD_ACCESS_DENIED`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_THREAD_ACCESS_DENIED))),
            @ApiResponse(responseCode = "404", description = "스레드 없음 (`THREAD_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_THREAD_NOT_FOUND)))
    })
    MessageList messages(
            @Parameter(description = "스레드 ID", example = "55") Long threadId,
            @Parameter(description = "직전 응답의 nextCursor — 첫 페이지는 생략. 이 messageId 미만을 읽는다", example = "9012") Long cursor,
            @Parameter(description = "페이지 크기 — 생략 · 0 이하면 30", example = "30") Integer size);

    @Operation(summary = "메시지 전송", description = """
            운영자 말풍선을 보냅니다.

            **권한:** ADMIN

            상대에게는 **「SHOWROOMZ 운영팀」으로만 표시**되고, 작성 운영자는 내부 기록으로만 남습니다(어드민 응답의 `operatorName`).

            **요청**
            - `clientMessageId` — 필수 · 64자 이하. FE가 메시지마다 새로 발급하는 멱등키(UUID 권장)입니다.
            - `content` · `attachmentIds` — **둘 중 하나는 있어야 합니다.** 첨부만 보내도 됩니다.
            - `attachmentIds` — 이 스레드에서 **내가** presign → S3 PUT → complete(`UPLOADED`)까지 마친 첨부만 붙일 수 있습니다.
              배열 순서가 표시 순서(`sortOrder`)입니다. 최대 20개 · 합계 500MB.

            **멱등 재전송** — 응답을 못 받아 다시 보낼 때는 **같은 `clientMessageId`** 로 보냅니다.
            이미 저장된 메시지가 있으면 새로 저장하지 않고 그 메시지를 **200**으로 돌려줍니다(본문 · 첨부는 다시 검사하지 않습니다).
            처음 저장되면 **201**입니다. FE는 두 코드를 같은 성공으로 처리하고, 응답의 `messageId`로 임시 말풍선을 교체합니다.

            **부수 효과**
            - 채널의 최근 메시지 · 미리보기가 갱신됩니다 — 목록에서 맨 위로 오고 미리보기에 「운영팀: 」이 붙습니다.
            - 상대의 안 읽은 수가 늘어납니다. **운영팀의 읽음 위치는 움직이지 않습니다** — 필요하면 `POST /read`를 호출합니다.
            - 시스템 카드는 이 API로 만들 수 없습니다 — 계약 관리의 조치에서만 생깁니다.

            탈퇴 회원의 채널은 409입니다. 정지 · 휴면 회원에게는 보낼 수 있습니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "전송됨",
                    content = @Content(schema = @Schema(implementation = MessageItem.class), examples = @ExampleObject(name = "본문 + 첨부 2개", value = SEND_CREATED))),
            @ApiResponse(responseCode = "200", description = "같은 clientMessageId의 재전송 — 새로 저장하지 않고 기존 메시지 반환",
                    content = @Content(schema = @Schema(implementation = MessageItem.class), examples = @ExampleObject(name = "기존 메시지", value = SEND_CREATED))),
            @ApiResponse(responseCode = "400", description = """
                    - `clientMessageId` 누락 · 공백 · 64자 초과 (`INVALID_INPUT`)
                    - 본문 · 첨부 모두 없음 (`MESSAGE_EMPTY`)
                    - 첨부 20개 초과 (`ATTACHMENT_COUNT_EXCEEDED`) · 합계 500MB 초과 (`ATTACHMENT_SIZE_EXCEEDED`)
                    - complete 전 · REJECTED 첨부 (`ATTACHMENT_NOT_UPLOADED`)""",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "clientMessageId 누락", value = ERR_NOT_BLANK),
                            @ExampleObject(name = "본문 · 첨부 없음", value = ERR_MESSAGE_EMPTY),
                            @ExampleObject(name = "첨부 개수 초과", value = ERR_ATTACHMENT_COUNT_EXCEEDED),
                            @ExampleObject(name = "첨부 용량 초과", value = ERR_ATTACHMENT_SIZE_EXCEEDED),
                            @ExampleObject(name = "업로드 미완료", value = ERR_ATTACHMENT_NOT_UPLOADED)
                    })),
            @ApiResponse(responseCode = "401", description = "운영자 계정이 아님 (`UNAUTHORIZED`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = """
                    - 운영팀 1:1 채널이 아닌 스레드 (`THREAD_ACCESS_DENIED`)
                    - 없는 첨부 · 다른 스레드의 첨부 · 다른 운영자가 올린 첨부 (`ATTACHMENT_ACCESS_DENIED`)""",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "운영팀 채널 아님", value = ERR_THREAD_ACCESS_DENIED),
                            @ExampleObject(name = "내 첨부 아님", value = ERR_ATTACHMENT_ACCESS_DENIED)
                    })),
            @ApiResponse(responseCode = "404", description = "스레드 없음 (`THREAD_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_THREAD_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = """
                    - 탈퇴한 회원의 채널 (`THREAD_READ_ONLY`)
                    - 이미 다른 메시지에 붙은 첨부 (`ATTACHMENT_ALREADY_ATTACHED`) — 새 clientMessageId로 같은 첨부를 다시 보낸 경우""",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "탈퇴 회원", value = ERR_THREAD_READ_ONLY),
                            @ExampleObject(name = "이미 붙은 첨부", value = ERR_ATTACHMENT_ALREADY_ATTACHED)
                    }))
    })
    ResponseEntity<MessageItem> send(
            @Parameter(description = "스레드 ID", example = "55") Long threadId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    schema = @Schema(implementation = SendRequest.class), examples = {
                            @ExampleObject(name = "본문만", value = REQ_SEND_TEXT),
                            @ExampleObject(name = "본문 + 첨부", value = REQ_SEND_WITH_ATTACHMENTS),
                            @ExampleObject(name = "첨부만", value = REQ_SEND_ATTACHMENTS_ONLY)
                    })) SendRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "읽음 처리", description = """
            운영팀의 읽음 위치를 이 스레드의 최신 메시지로 옮깁니다.

            **권한:** ADMIN

            - 읽음 위치는 **운영팀 공용 하나**입니다 — 누가 호출하든 모든 운영자의 `unreadCount`가 함께 0이 됩니다.
            - 스레드를 열었을 때와 새 메시지를 받아 화면에 보일 때 호출합니다. 몇 번을 불러도 결과가 같습니다(멱등).
            - 상대(브랜드 · 인플루언서)의 읽음 위치는 바뀌지 않습니다. 탈퇴 회원의 채널도 읽음 처리는 됩니다.
            - 호출 뒤 목록 행 · 탭 배지를 다시 조회하면 줄어든 값이 보입니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "처리됨 — 본문 없음"),
            @ApiResponse(responseCode = "403", description = "운영팀 1:1 채널이 아닌 스레드 (`THREAD_ACCESS_DENIED`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_THREAD_ACCESS_DENIED))),
            @ApiResponse(responseCode = "404", description = "스레드 없음 (`THREAD_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_THREAD_NOT_FOUND)))
    })
    ResponseEntity<Void> markRead(@Parameter(description = "스레드 ID", example = "55") Long threadId);

    // ── 첨부 ─────────────────────────────────────────────────────────────────

    @Operation(summary = "첨부 업로드 URL 발급", description = """
            첨부 업로드 3단계의 ①입니다. 파트너센터 · 스튜디오와 같은 규칙입니다.

            **권한:** ADMIN

            **흐름**
            1. 이 API로 `attachmentId` · `uploadUrl` 발급 — 첨부 행이 `PENDING`으로 먼저 생깁니다.
            2. `uploadUrl`에 파일 바이트를 **직접 PUT**합니다(서버를 거치지 않음). 헤더 `Content-Type`은 응답의 `requiredContentType`과 **정확히 같아야** 합니다 — 다르면 S3가 `SignatureDoesNotMatch`로 거절합니다.
            3. `PATCH /v1/admin/attachments/{attachmentId}/complete`로 완료를 알립니다.
            4. 메시지 전송의 `attachmentIds`에 넣습니다.

            - `uploadUrl`은 **900초(15분)** 뒤 만료됩니다. 만료되면 이 API를 다시 호출해 새 첨부로 올립니다.
            - 전송하지 않고 버린 첨부는 정리 배치가 지웁니다.

            **허용 확장자**(파일명 기준 · 대소문자 무시)

            | 종류 | 확장자 |
            |---|---|
            | `IMAGE` | jpg · jpeg · png · gif · webp · bmp · svg · heic · heif |
            | `VIDEO` | mp4 · mov · avi · wmv · mkv · webm · m4v |
            | `DOCUMENT` | pdf · doc · docx · hwp · hwpx · xls · xlsx · ppt · pptx · txt · csv · rtf · zip · rar · 7z |

            그 외(실행 파일 · 스크립트 · html 등)와 확장자 없는 파일은 400입니다.

            **용량** — 파일 1개가 500MB를 넘으면 여기서 바로 400입니다. 메시지 1건의 합계 500MB · 20개 제한은 전송 때 검사합니다.

            탈퇴 회원의 채널에는 발급하지 않습니다(409).
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "발급됨",
                    content = @Content(schema = @Schema(implementation = PresignResponse.class), examples = @ExampleObject(value = PRESIGN_CREATED))),
            @ApiResponse(responseCode = "400", description = """
                    - `fileName` · `contentType` 공백, `sizeBytes` 누락 · 0 이하 (`INVALID_INPUT`)
                    - 허용하지 않는 확장자 (`ATTACHMENT_EXTENSION_NOT_ALLOWED`)
                    - 파일 1개가 500MB 초과 (`ATTACHMENT_SIZE_EXCEEDED`)""",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "필수값 누락", value = ERR_NOT_BLANK),
                            @ExampleObject(name = "허용하지 않는 확장자", value = ERR_ATTACHMENT_EXTENSION_NOT_ALLOWED),
                            @ExampleObject(name = "용량 초과", value = ERR_ATTACHMENT_SIZE_EXCEEDED)
                    })),
            @ApiResponse(responseCode = "401", description = "운영자 계정이 아님 (`UNAUTHORIZED`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "운영팀 1:1 채널이 아닌 스레드 (`THREAD_ACCESS_DENIED`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_THREAD_ACCESS_DENIED))),
            @ApiResponse(responseCode = "404", description = "스레드 없음 (`THREAD_NOT_FOUND`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_THREAD_NOT_FOUND))),
            @ApiResponse(responseCode = "409", description = "탈퇴한 회원의 채널 (`THREAD_READ_ONLY`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_THREAD_READ_ONLY)))
    })
    ResponseEntity<PresignResponse> presign(
            @Parameter(description = "스레드 ID", example = "55") Long threadId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    schema = @Schema(implementation = PresignRequest.class), examples = @ExampleObject(value = REQ_PRESIGN))) PresignRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "첨부 업로드 완료 통지", description = """
            첨부 업로드 3단계의 ③입니다. S3 PUT이 끝난 뒤 호출합니다.

            **권한:** ADMIN — 발급받은 운영자 **본인만** 호출할 수 있습니다.

            서버가 S3에 실제로 올라간 파일의 크기 · Content-Type을 다시 확인하고 결과를 `status`로 돌려줍니다.

            | `status` | 뜻 | FE |
            |---|---|---|
            | `UPLOADED` | 검증 통과 — 전송에 붙일 수 있다 | 첨부 칩을 완료로 표시 |
            | `REJECTED` | 실제 크기 500MB 초과, 또는 이미지 · 영상의 Content-Type이 확장자와 다름. S3 파일은 지워진다 | 목록에서 빼고 안내 |

            - **REJECTED는 오류(4xx)가 아니라 200 응답의 값입니다.**
            - 본문은 생략할 수 있습니다. 영상이면 `<video>`의 `loadedmetadata`로 읽은 `durationSeconds`를 실어 보냅니다(표시용 참고값).
            - 이미 처리된 첨부에 다시 호출하면 현재 상태를 그대로 돌려줍니다(멱등).
            - S3에 파일이 없으면(PUT 전 · 실패) 400 `ATTACHMENT_NOT_UPLOADED`입니다 — PUT을 마친 뒤 다시 호출합니다.
            - 응답의 `sortOrder`는 메시지에 붙기 전이라 null입니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검증 결과",
                    content = @Content(schema = @Schema(implementation = AttachmentSummary.class), examples = {
                            @ExampleObject(name = "UPLOADED", value = COMPLETE_UPLOADED),
                            @ExampleObject(name = "REJECTED", summary = "jpg인데 실제 Content-Type이 image/*가 아님", value = COMPLETE_REJECTED)
                    })),
            @ApiResponse(responseCode = "400", description = """
                    - S3에 파일이 아직 없음 (`ATTACHMENT_NOT_UPLOADED`)
                    - `durationSeconds`가 0 이하 (`INVALID_INPUT`)""",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "업로드 전", value = ERR_ATTACHMENT_NOT_UPLOADED),
                            @ExampleObject(name = "잘못된 재생시간", value = ERR_INVALID_INPUT)
                    })),
            @ApiResponse(responseCode = "403", description = "없는 첨부 · 내가 발급받지 않은 첨부 (`ATTACHMENT_ACCESS_DENIED`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_ATTACHMENT_ACCESS_DENIED)))
    })
    AttachmentSummary completeUpload(
            @Parameter(description = "첨부 ID — presign 응답의 attachmentId", example = "601") Long attachmentId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(
                    schema = @Schema(implementation = CompleteAttachmentRequest.class), examples = {
                            @ExampleObject(name = "영상", value = REQ_COMPLETE_VIDEO),
                            @ExampleObject(name = "이미지 · 문서 (본문 생략 가능)", value = "{}")
                    })) CompleteAttachmentRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "첨부 다운로드 URL 일괄 발급", description = """
            운영팀 채널 첨부의 presigned GET URL을 **한 번에** 발급합니다.
            파일 하나를 누를 때도, 메시지의 **전체 다운로드**도 이 API 하나입니다. 파트너센터 · 스튜디오의 `POST /attachments/download`와 같은 규칙입니다.

            **권한:** ADMIN

            **요청** — `attachmentIds` 1~20개. 「전체 다운로드」는 메시지의 `attachments`를 `sortOrder` 순서 그대로 넣습니다.

            **응답**
            - `attachmentIds` 순서를 따릅니다. 같은 ID가 여러 번 있으면 한 번만 발급합니다.
            - URL은 원본 파일명으로 저장되도록 서명돼 있습니다(`Content-Disposition: attachment`).

            **FE 처리** — 응답 순서대로 URL마다 숨긴 `<a download>`를 클릭하거나 간격을 두고 차례로 엽니다.
            파일은 S3에서 바로 내려옵니다(서버 경유 · ZIP 없음).

            **규칙**
            - **전부 되거나 전부 안 됩니다** — 하나라도 받을 수 없으면 아무 URL도 발급하지 않습니다.
              일부만 받으면 운영자가 빠진 파일을 모른 채 「전체 다운로드」를 끝낸 것으로 알게 됩니다.
            - 운영팀 1:1 채널의 첨부만 받을 수 있습니다. 여러 채널의 첨부를 한 요청에 섞어도 됩니다.
            - 상대가 보낸 첨부도 받을 수 있습니다. **아직 전송하지 않은 첨부**는 올린 운영자 본인만 받습니다.
            - URL은 **300초** 뒤 만료되지만 다운로드가 **시작되는** 시점에만 검사하므로 차례로 받아도 됩니다. 받아온 URL은 바로 씁니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "요청 순서대로 발급된 URL 목록",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AttachmentDownloadResponse.class)),
                            examples = @ExampleObject(name = "전체 다운로드 (2개)", value = DOWNLOAD_OK))),
            @ApiResponse(responseCode = "400", description = """
                    - `attachmentIds`가 비었거나 20개 초과 · null 원소 (`INVALID_INPUT`)
                    - 업로드가 끝나지 않은 첨부 포함 (`ATTACHMENT_NOT_UPLOADED`)""",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "개수 오류", value = ERR_INVALID_INPUT),
                            @ExampleObject(name = "업로드 미완료", value = ERR_ATTACHMENT_NOT_UPLOADED)
                    })),
            @ApiResponse(responseCode = "403", description = """
                    - 없는 첨부 ID 포함 · 전송 전인 다른 운영자의 첨부 (`ATTACHMENT_ACCESS_DENIED`) — 존재 여부를 따로 알려주지 않는다
                    - 운영팀 1:1 채널이 아닌 스레드의 첨부 (`THREAD_ACCESS_DENIED`)""",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "받을 수 없는 첨부", value = ERR_ATTACHMENT_ACCESS_DENIED),
                            @ExampleObject(name = "운영팀 채널 아님", value = ERR_THREAD_ACCESS_DENIED)
                    }))
    })
    List<AttachmentDownloadResponse> download(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    schema = @Schema(implementation = AttachmentDownloadRequest.class), examples = {
                            @ExampleObject(name = "전체 다운로드", value = REQ_DOWNLOAD_ALL),
                            @ExampleObject(name = "파일 하나", value = REQ_DOWNLOAD_ONE)
                    })) AttachmentDownloadRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    // ── 시스템 카드 ──────────────────────────────────────────────────────────

    @Operation(summary = "재발송 완료 알림 보내기", description = """
            재발송 요청 카드(`CONTRACT_RESEND_REQUEST`)의 [재발송 완료 알림 보내기] 버튼입니다.

            **권한:** ADMIN

            **모두싸인에서 서명 안내를 직접 재발송한 뒤** 호출합니다. 이 버튼은 「보냈다」는 운영자의 신고이고,
            서버는 모두싸인 API를 쓰지 않으므로 실제 재발송 여부를 확인하지 않습니다.

            **한 번의 호출로 일어나는 일**
            1. 정해진 안내 문구가 운영자 말풍선으로 전송됩니다(`autoNotice = true`, 작성자 = 누른 운영자).
               > 모두싸인에서 서명 안내를 다시 보내드렸습니다. 메일함(스팸함 포함)을 확인해 주세요.
            2. 카드가 「전송됨 · 시각 · 처리자」로 굳습니다(`action.state = DONE`, `tone = NEUTRAL`).

            **바뀌지 않는 것** — 계약 상태 · 계약 이력(계약 관리 화면에 처리 기록이 남지 않습니다).

            **응답** — 굳은 카드(`card`)와 자동 안내 말풍선(`notice`)입니다. FE는 이 둘로 화면의 카드를 교체하고 말풍선을 추가합니다.

            **중복 · 동시 클릭** — 요청 행을 잠그고 처리합니다. 이미 전송된 카드에 다시 호출하면 **아무것도 보내지 않고**
            200 · `alreadyNotified = true`와 현재 카드 상태(처리자 = 먼저 누른 운영자)를 돌려줍니다. 두 운영자가 동시에 눌러도 안내는 한 번만 나갑니다.
            카드 도입 전 방식으로 처리된 요청이면 `notice`가 null입니다.

            **판정 순서**
            1. 운영팀 채널인가 (403 · 404)
            2. `messageId`가 **이 스레드의** 재발송 요청 카드인가 (404 `MESSAGE_CARD_NOT_FOUND`) — 직권 취소 카드 · 말풍선 ID도 404
            3. 이미 전송됨 → 200 `alreadyNotified = true` (계약 상태 · 회원 상태와 무관)
            4. 계약이 서명 진행중(`SIGNING`)인가 (409 `CONTRACT_STATUS_CONFLICT`) — 카드가 `CLOSED`인 경우. 다시 보낼 안내가 없다
            5. 탈퇴 회원의 채널인가 (409 `THREAD_READ_ONLY`)
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "전송 결과 — 굳은 카드와 안내 말풍선",
                    content = @Content(schema = @Schema(implementation = ResendNoticeResponse.class), examples = {
                            @ExampleObject(name = "전송됨", summary = "이번 호출로 안내가 나갔다", value = RESEND_NOTICE_SENT),
                            @ExampleObject(name = "이미 전송된 카드", summary = "다른 운영자가 먼저 눌렀다 — 아무것도 보내지 않음", value = RESEND_NOTICE_ALREADY)
                    })),
            @ApiResponse(responseCode = "401", description = "운영자 계정이 아님 (`UNAUTHORIZED`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_UNAUTHORIZED))),
            @ApiResponse(responseCode = "403", description = "운영팀 1:1 채널이 아닌 스레드 (`THREAD_ACCESS_DENIED`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = @ExampleObject(value = ERR_THREAD_ACCESS_DENIED))),
            @ApiResponse(responseCode = "404", description = """
                    - 스레드 없음 (`THREAD_NOT_FOUND`)
                    - 그 스레드의 재발송 요청 카드가 아님 (`MESSAGE_CARD_NOT_FOUND`)""",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "스레드 없음", value = ERR_THREAD_NOT_FOUND),
                            @ExampleObject(name = "카드 아님", value = ERR_MESSAGE_CARD_NOT_FOUND)
                    })),
            @ApiResponse(responseCode = "409", description = """
                    - 계약이 서명 진행중이 아님 — 체결 · 취소 등으로 카드가 닫힘 (`CONTRACT_STATUS_CONFLICT`). 메시지를 다시 조회하면 카드가 `CLOSED`로 보인다
                    - 탈퇴한 회원의 채널 (`THREAD_READ_ONLY`)""",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class), examples = {
                            @ExampleObject(name = "서명 단계 아님", value = ERR_CONTRACT_STATUS_CONFLICT),
                            @ExampleObject(name = "탈퇴 회원", value = ERR_THREAD_READ_ONLY)
                    }))
    })
    ResendNoticeResponse sendResendNotice(
            @Parameter(description = "스레드 ID", example = "71") Long threadId,
            @Parameter(description = "재발송 요청 카드의 메시지 ID — 메시지 조회의 messageId", example = "9012") Long messageId,
            @Parameter(hidden = true) UserPrincipal principal);
}
