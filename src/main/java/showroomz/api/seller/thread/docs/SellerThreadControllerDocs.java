package showroomz.api.seller.thread.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.common.attachment.dto.AttachmentSummary;
import showroomz.api.common.attachment.dto.CompleteAttachmentRequest;
import showroomz.api.common.attachment.dto.PresignRequest;
import showroomz.api.common.attachment.dto.PresignResponse;
import showroomz.api.seller.thread.dto.MessageItem;
import showroomz.api.seller.thread.dto.MessageListResponse;
import showroomz.api.seller.thread.dto.SendMessageRequest;
import showroomz.api.seller.thread.dto.ThreadListItem;
import showroomz.api.seller.thread.dto.ThreadSummaryResponse;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Seller - Thread", description = "파트너센터 연결·소통 스레드/메시지 API (§13)")
public interface SellerThreadControllerDocs {

    @Operation(
            summary = "좌측 스레드 목록",
            description = "운영자 채널이 항상 최상단 고정되고, 그 아래 연결됨(OPEN) 상태의 상대만 최근 메시지순으로 노출된다.\n\n" +
                    "휴면(DORMANT) 스레드는 REQUESTED와 동일하게 목록에서 완전히 제외된다(§13-12 #2 해소) — " +
                    "별도의 휴면 화면은 존재하지 않는다.\n\n**권한:** SELLER"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<ThreadListItem>> getThreads(@ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "GNB 배지용 안 읽은 수 합계",
            description = "폴링 대상 경량 엔드포인트(§0). 실시간 알림 없이 이 값을 주기적으로 재조회해 배지를 갱신한다.\n\n**권한:** SELLER"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ThreadSummaryResponse> getSummary();

    @Operation(
            summary = "메시지 조회(커서 페이징)",
            description = "최신순으로 내려주며, cursor 미지정 시 최신 페이지부터 시작한다. cursor는 직전 응답의 " +
                    "nextCursor를 그대로 넘긴다.\n\n**권한:** SELLER(본인 마켓 스레드만)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "본인 스레드가 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 스레드",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<MessageListResponse> getMessages(
            @Parameter(description = "스레드 ID", required = true) @PathVariable("threadId") Long threadId,
            @Parameter(description = "커서(직전 응답의 nextCursor)") @RequestParam(value = "cursor", required = false) Long cursor,
            @Parameter(description = "페이지 크기(기본 30)") @RequestParam(value = "size", required = false) Integer size
    );

    @Operation(
            summary = "메시지 전송",
            description = "clientMessageId는 FE가 발급한 멱등키다. 같은 키로 재전송하면 신규 저장 대신 " +
                    "기존 메시지를 200으로 반환한다(§13-10) — 신규 전송은 201.\n\n" +
                    "**권한:** SELLER(본인 마켓 스레드만, 휴면 스레드는 작성 불가)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "신규 전송 성공"),
            @ApiResponse(responseCode = "200", description = "멱등 재전송 — 기존 메시지 반환"),
            @ApiResponse(responseCode = "400", description = "content와 attachmentIds가 모두 비어 있음(MESSAGE_EMPTY) 등",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "본인 스레드가 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 스레드",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "휴면 스레드에 전송 시도(THREAD_DORMANT)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<MessageItem> sendMessage(
            @Parameter(description = "스레드 ID", required = true) @PathVariable("threadId") Long threadId,
            @Valid @RequestBody SendMessageRequest request
    );

    @Operation(
            summary = "읽음 처리",
            description = "현재 스레드의 마지막 메시지까지 읽음으로 표시한다.\n\n**권한:** SELLER(본인 마켓 스레드만)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "처리 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "본인 스레드가 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 스레드",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> markRead(
            @Parameter(description = "스레드 ID", required = true) @PathVariable("threadId") Long threadId
    );

    @Operation(
            summary = "첨부 업로드용 Presigned URL 발급",
            description = "서버가 확장자(§2-1)·개별 파일 크기를 먼저 검증하고 PENDING 첨부 행을 선(先)생성한 뒤 " +
                    "S3 presigned PUT URL을 발급한다(§4-1 ①). 실제 파일은 서버를 거치지 않고 이 URL로 S3에 " +
                    "직접 PUT한다 — 응답의 requiredContentType과 정확히 같은 Content-Type 헤더로 PUT해야 한다.\n\n" +
                    "**권한:** SELLER(본인 마켓 스레드만)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "발급 성공"),
            @ApiResponse(responseCode = "400", description = "허용되지 않는 확장자 또는 500MB 초과 단일 파일",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "본인 스레드가 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 스레드",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PresignResponse> createPresignedUpload(
            @Parameter(description = "스레드 ID", required = true) @PathVariable("threadId") Long threadId,
            @Valid @RequestBody PresignRequest request
    );

    @Operation(
            summary = "첨부 업로드 완료 통지",
            description = "S3 직접 PUT이 끝난 뒤 호출한다. 서버가 HeadObject로 실제 업로드 크기·Content-Type을 " +
                    "재확인해 UPLOADED로 전환한다(§4-1 ③). 선언 값과 실측이 어긋나면 400이 아니라 200 + " +
                    "status=REJECTED로 응답한다 — 검증 자체는 정상 수행됐고 결과가 거부일 뿐이므로, FE는 " +
                    "응답의 status 필드로 성공/거부를 판정해야 한다.\n\n**권한:** SELLER(본인이 올린 첨부만)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "검증 완료 — status로 UPLOADED/REJECTED 판정"),
            @ApiResponse(responseCode = "400", description = "S3에 아직 업로드되지 않음(ATTACHMENT_NOT_UPLOADED)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "본인이 올린 첨부가 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<AttachmentSummary> completeUpload(
            @Parameter(description = "첨부 ID", required = true) @PathVariable("attachmentId") Long attachmentId,
            @Valid @RequestBody(required = false) CompleteAttachmentRequest request
    );
}
