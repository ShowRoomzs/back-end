package showroomz.api.admin.thread.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

@Tag(name = "Admin - Thread", description = """
        관리자 소통 스레드 API""")
public interface AdminThreadControllerDocs {

    @Operation(summary = "운영팀 채널 목록", description = """
            탭별 운영팀 1:1 채널을 최근 메시지순으로 반환합니다. **메시지가 없는 채널도 나옵니다**(맨 아래).

            - `tab`: `BRAND`(브랜드) · `INFLUENCER`(인플루언서) — 필수
            - `keyword`: 브랜드 탭은 브랜드명 · 담당자, 인플루언서 탭은 쇼룸명 부분 일치.
              회원번호(`BRD-1017` · `INF-3021`)를 접두사째 넣으면 번호로만 찾고, 숫자가 아니면 결과가 0건입니다.
            - `lastMessageByOperator`가 true면 미리보기 앞에 「운영팀: 」을 붙입니다. 마지막이 시스템 카드면 미리보기는 카드 제목입니다.
            - 보조 줄은 `managerName`(「담당 {이름}」) · `businessType`(사업자/개인)으로 조립합니다.
            - `unreadCount`는 **운영팀 기준**입니다 — 어느 운영자가 읽든 함께 줄어듭니다.
            - `writable`이 false면(탈퇴 회원) 입력창을 닫습니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "채널 목록과 페이지 정보",
                    content = @Content(schema = @Schema(implementation = PageResponse.class),
                            examples = @ExampleObject(value = """
                                    {"content":[{"threadId":55,"tab":"BRAND","name":"무드코스메틱","imageUrl":null,"memberNo":"BRD-1017","memberId":1017,"managerName":"이현","businessType":null,"memberStatus":"ACTIVE","lastMessagePreview":"여름 수분 세럼 계약 취소 요청드립니다.","lastMessageByOperator":false,"lastMessageAt":"2026-08-14T10:05:00","unreadCount":1,"writable":true}],"pageInfo":{"currentPage":1,"totalPages":1,"totalResults":1,"limit":20,"hasNext":false}}
                                    """))),
            @ApiResponse(responseCode = "400", description = "tab 누락 · 잘못된 값", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    PageResponse<ChannelListItem> list(
            @Parameter(description = "탭 — BRAND · INFLUENCER", example = "BRAND", required = true) AdminChannelTab tab,
            @Parameter(description = "이름 부분 일치 또는 회원번호", example = "무드") String keyword,
            PagingRequest paging);

    @Operation(summary = "탭 배지", description = """
            탭별 안 읽은 메시지 수(`unreadCount`)와 운영팀 확인을 기다리는 재발송 요청 카드 수(`pendingCardCount`)입니다.
            탭 숫자의 뜻이 확정되기 전이라 두 값을 함께 내립니다. `issue`는 이슈 스레드 기획 전까지 항상 null입니다. 폴링 대상입니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "탭별 배지 값",
                    content = @Content(schema = @Schema(implementation = Summary.class), examples = @ExampleObject(value = """
                            {"brand":{"unreadCount":3,"pendingCardCount":0},"influencer":{"unreadCount":2,"pendingCardCount":1},"issue":null}
                            """))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    Summary summary();

    @Operation(summary = "스레드 헤더 · 접이식 정보 바", description = """
            스레드 헤더와 정보 바 3칸(① 정보 · ② 진행 중 · ③ 열린 이슈 스레드)입니다. 접혀 있어도 요약 줄이 보이므로 스레드 진입 시 1회 호출합니다.

            - 브랜드와 인플루언서는 같은 골격이고 채우는 칸만 다릅니다. 해당 없는 값은 null입니다.
            - `progress`의 값이 **null이면 0이 아니라 집계할 수 없다는 뜻**입니다 — 그 행을 그리지 않습니다.
              미정산 건수(`unsettledCount`)와 과세 유형(`taxType`)은 현재 항상 null입니다.
            - `contractSigning`은 서명 진행중 + 체결 처리 대기 계약 수입니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "헤더와 정보 바",
                    content = @Content(schema = @Schema(implementation = ChannelInfo.class))),
            @ApiResponse(responseCode = "403", description = "운영팀 채널이 아닌 스레드 (`THREAD_ACCESS_DENIED`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "스레드 없음 (`THREAD_NOT_FOUND`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ChannelInfo info(@Parameter(description = "스레드 ID", example = "55") Long threadId);

    @Operation(summary = "메시지 조회", description = """
            말풍선과 시스템 카드를 최신순으로 반환합니다. `cursor`에 직전 응답의 `nextCursor`를 넣어 이어 읽습니다(기본 30건).

            - `messageType`: `TEXT`(말풍선) · `SYSTEM`(시스템 카드 — 가운데 정렬 카드로 그리고 `card`를 읽습니다)
            - `mine`: 운영팀의 말풍선이면 true입니다. **운영자 개인이 아니라 운영팀 기준**이라 다른 운영자의 말풍선도 true입니다.
            - `operatorName`: 「운영팀 · {이름}」의 이름입니다. 시스템이 보낸 안내는 null입니다.
            - `autoNotice`: true면 `자동 안내` 태그를 붙입니다. 이 태그와 운영자 이름은 어드민에만 보이고 상대에게는 일반 운영팀 메시지입니다.
            - `card.tone`: `WARNING`(운영자 조치가 남은 요청) · `NEUTRAL`(조치가 끝났거나 결과 카드)
            - `card.action.state`: `PENDING`(버튼 노출) · `DONE`(「전송됨 · 시각 · 처리자」) · `CLOSED`(계약이 서명 단계를 벗어나 닫힘 — 버튼 없음)
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "메시지 목록",
                    content = @Content(schema = @Schema(implementation = MessageList.class), examples = @ExampleObject(value = """
                            {"content":[{"messageId":9013,"messageType":"TEXT","senderType":"ADMIN","mine":true,"senderName":null,"operatorName":"김운영","autoNotice":true,"content":"모두싸인에서 서명 안내를 다시 보내드렸습니다. 메일함(스팸함 포함)을 확인해 주세요.","attachments":[],"card":null,"createdAt":"2026-08-14T08:55:00"},{"messageId":9012,"messageType":"SYSTEM","senderType":"CREATOR","mine":false,"senderName":"뷰티_소연","operatorName":null,"autoNotice":false,"content":"요청 · 서명 안내 다시 받기","attachments":[],"card":{"cardType":"CONTRACT_RESEND_REQUEST","title":"요청 · 서명 안내 다시 받기","tone":"NEUTRAL","contractId":41,"contractNumber":"CTR-20260813-041","groupBuyTitle":"겨울 리페어 크림 공구","detail":{"requesterType":"CREATOR","requesterName":"뷰티_소연","requestedAt":"2026-08-14T08:50:00"},"action":{"type":"RESEND_NOTICE","state":"DONE","canExecute":false,"doneAt":"2026-08-14T08:55:00","doneByName":"김운영","noticeMessageId":9013}},"createdAt":"2026-08-14T08:50:00"}],"nextCursor":null,"hasNext":false}
                            """))),
            @ApiResponse(responseCode = "403", description = "운영팀 채널이 아닌 스레드 (`THREAD_ACCESS_DENIED`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "스레드 없음 (`THREAD_NOT_FOUND`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    MessageList messages(
            @Parameter(description = "스레드 ID", example = "55") Long threadId,
            @Parameter(description = "직전 페이지의 nextCursor — 첫 페이지는 생략") Long cursor,
            @Parameter(description = "페이지 크기 — 기본 30", example = "30") Integer size);

    @Operation(summary = "메시지 전송", description = """
            운영자 말풍선을 보냅니다. 상대에게는 **SHOWROOMZ 운영팀**으로만 표시되고 작성 운영자는 내부 기록으로만 남습니다.

            - `clientMessageId`는 FE가 발급하는 멱등키입니다. 같은 값으로 다시 보내면 새로 저장하지 않고 기존 메시지를 200으로 돌려줍니다.
            - `content`와 `attachmentIds`가 모두 비면 400입니다. 첨부는 presign → S3 PUT → complete를 마친 것만 붙일 수 있습니다(최대 20개 · 합계 500MB).
            - 시스템 카드는 이 API로 만들 수 없습니다 — 계약 관리의 조치에서만 생깁니다.
            - 전송은 읽음 처리를 하지 않습니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "전송됨", content = @Content(schema = @Schema(implementation = MessageItem.class))),
            @ApiResponse(responseCode = "200", description = "같은 clientMessageId의 재전송 — 기존 메시지 반환", content = @Content(schema = @Schema(implementation = MessageItem.class))),
            @ApiResponse(responseCode = "400", description = "본문 · 첨부 모두 없음 (`MESSAGE_EMPTY`) · 첨부 개수/용량 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "운영팀 채널이 아닌 스레드 (`THREAD_ACCESS_DENIED`) · 내가 올리지 않은 첨부", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "스레드 없음 (`THREAD_NOT_FOUND`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "탈퇴한 회원의 채널 (`THREAD_READ_ONLY`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<MessageItem> send(
            @Parameter(description = "스레드 ID", example = "55") Long threadId,
            SendRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "읽음 처리", description = "운영팀의 읽음 위치를 최신 메시지로 옮깁니다. 운영팀 공용이라 누가 호출하든 모든 운영자의 안 읽은 수가 함께 줄어듭니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "처리됨"),
            @ApiResponse(responseCode = "403", description = "운영팀 채널이 아닌 스레드 (`THREAD_ACCESS_DENIED`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "스레드 없음 (`THREAD_NOT_FOUND`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> markRead(@Parameter(description = "스레드 ID", example = "55") Long threadId);

    @Operation(summary = "첨부 업로드 URL 발급", description = "파트너센터 · 스튜디오와 같은 3단계입니다 — ① 이 API로 URL 발급 ② 그 URL로 S3에 직접 PUT ③ complete 호출. 허용 확장자 · 용량 규칙도 같습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "발급됨", content = @Content(schema = @Schema(implementation = PresignResponse.class))),
            @ApiResponse(responseCode = "400", description = "허용하지 않는 확장자 · 용량 초과", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "탈퇴한 회원의 채널 (`THREAD_READ_ONLY`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PresignResponse> presign(
            @Parameter(description = "스레드 ID", example = "55") Long threadId,
            PresignRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "첨부 업로드 완료 통지", description = "S3 PUT이 끝난 뒤 호출합니다. 서버가 실제 업로드 크기 · 타입을 다시 확인하고 `status`를 UPLOADED 또는 REJECTED로 돌려줍니다. 발급받은 운영자 본인만 호출할 수 있습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검증 결과", content = @Content(schema = @Schema(implementation = AttachmentSummary.class))),
            @ApiResponse(responseCode = "403", description = "내가 발급받지 않은 첨부 (`ATTACHMENT_ACCESS_DENIED`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    AttachmentSummary completeUpload(
            @Parameter(description = "첨부 ID", example = "501") Long attachmentId,
            CompleteAttachmentRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "첨부 다운로드 URL 일괄 발급", description = """
            운영팀 채널 첨부의 presigned GET URL을 **한 번에** 발급합니다. 파일 하나를 누를 때도, 메시지의 **전체 다운로드**도 이 API 하나입니다.
            파트너센터 · 스튜디오의 `POST /attachments/download`와 같은 규칙입니다.

            - FE는 응답 순서대로 URL마다 숨긴 `<a download>`를 클릭하거나 간격을 두고 차례로 엽니다. 파일은 S3에서 바로 내려옵니다(ZIP 없음).
            - 응답은 `attachmentIds` 순서를 따르고, 같은 ID는 한 번만 발급합니다. 최대 20개입니다.
            - **전부 되거나 전부 안 됩니다** — 하나라도 운영팀 채널의 첨부가 아니거나 받을 수 없으면 아무 URL도 발급하지 않습니다.
            - URL은 300초 뒤 만료되지만 다운로드가 **시작되는** 시점에만 검사하므로 차례로 받아도 됩니다.
            - 상대가 보낸 첨부도 받을 수 있습니다. 아직 보내지 않은 첨부는 올린 운영자 본인만 받습니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "요청 순서대로 발급된 URL 목록",
                    content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(
                            schema = @Schema(implementation = AttachmentDownloadResponse.class)))),
            @ApiResponse(responseCode = "400", description = "attachmentIds가 비었거나 20개 초과 (`INVALID_INPUT`) · 업로드 미완료 (`ATTACHMENT_NOT_UPLOADED`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "운영팀 채널의 첨부가 아님 (`THREAD_ACCESS_DENIED`) · 없는 첨부 · 전송 전인 남의 첨부 (`ATTACHMENT_ACCESS_DENIED`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    List<AttachmentDownloadResponse> download(
            AttachmentDownloadRequest request,
            @Parameter(hidden = true) UserPrincipal principal);

    @Operation(summary = "재발송 완료 알림 보내기", description = """
            재발송 요청 카드의 버튼입니다. **모두싸인에서 재발송한 뒤** 호출합니다 — 서버는 실제 재발송 여부를 확인하지 않습니다.

            한 번의 호출로 ① 정해진 안내 문구가 운영자 말풍선으로 전송되고(`autoNotice = true`) ② 카드가 「전송됨 · 시각 · 처리자」로 굳습니다.
            계약 상태와 계약 이력은 바뀌지 않습니다.

            이미 전송된 카드에 다시 호출하면 아무것도 보내지 않고 200과 함께 `alreadyNotified = true` · 현재 카드 상태를 돌려줍니다 —
            두 운영자가 동시에 눌러도 안내는 한 번만 나갑니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "전송 결과 — 굳은 카드와 안내 말풍선",
                    content = @Content(schema = @Schema(implementation = ResendNoticeResponse.class))),
            @ApiResponse(responseCode = "403", description = "운영팀 채널이 아닌 스레드 (`THREAD_ACCESS_DENIED`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "그 스레드의 재발송 요청 카드가 아님 (`MESSAGE_CARD_NOT_FOUND`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "계약이 서명 진행중이 아님 (`CONTRACT_STATUS_CONFLICT`) · 탈퇴한 회원의 채널 (`THREAD_READ_ONLY`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResendNoticeResponse sendResendNotice(
            @Parameter(description = "스레드 ID", example = "55") Long threadId,
            @Parameter(description = "재발송 요청 카드의 메시지 ID", example = "9012") Long messageId,
            @Parameter(hidden = true) UserPrincipal principal);
}
