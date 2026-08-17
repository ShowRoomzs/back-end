package showroomz.api.creator.thread.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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
import showroomz.api.common.attachment.dto.AttachmentDownloadResponse;
import showroomz.api.common.attachment.dto.AttachmentSummary;
import showroomz.api.common.attachment.dto.CompleteAttachmentRequest;
import showroomz.api.common.attachment.dto.PresignRequest;
import showroomz.api.common.attachment.dto.PresignResponse;
import showroomz.api.creator.thread.dto.MessageItem;
import showroomz.api.creator.thread.dto.MessageListResponse;
import showroomz.api.creator.thread.dto.SendMessageRequest;
import showroomz.api.creator.thread.dto.ThreadListItem;
import showroomz.api.creator.thread.dto.ThreadSummaryResponse;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Creator - Thread", description = "쇼룸 스튜디오 연결·소통 스레드/메시지 API (§14)")
public interface CreatorThreadControllerDocs {

    @Operation(
            summary = "`연결됨` 탭 — 스레드 목록",
            description = "운영자 채널이 항상 최상단 고정되고, 그 아래 연결됨(OPEN) 상태의 브랜드만 최근 메시지순으로 노출된다.\n\n" +
                    "휴면(DORMANT) 스레드는 목록에서 완전히 제외된다(§13-12 #2 해소).\n\n" +
                    "`keyword`는 목록 상단 \"브랜드명 검색\" 입력이다 — 값을 주면 브랜드명 부분 일치만 남고, " +
                    "브랜드가 아닌 운영자 고정 채널은 검색 결과에서 빠진다(값이 없을 때만 최상단에 고정 노출).\n\n" +
                    "**권한:** CREATOR"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"threadId\": 1,\n" +
                                                    "      \"counterpartName\": \"SHOWROOMZ 운영팀\",\n" +
                                                    "      \"counterpartImageUrl\": null,\n" +
                                                    "      \"operatorChannel\": true,\n" +
                                                    "      \"hasContract\": false,\n" +
                                                    "      \"lastMessagePreview\": \"등록이 승인되었습니다. 궁금한 점이 있으면 말씀해주세요.\",\n" +
                                                    "      \"lastMessageAt\": \"2026-08-01T10:00:00\",\n" +
                                                    "      \"unreadCount\": 0\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"threadId\": 55,\n" +
                                                    "      \"counterpartName\": \"쇼룸즈\",\n" +
                                                    "      \"counterpartImageUrl\": \"https://s3.ap-northeast-2.amazonaws.com/bucket/market/7.jpg\",\n" +
                                                    "      \"operatorChannel\": false,\n" +
                                                    "      \"hasContract\": false,\n" +
                                                    "      \"lastMessagePreview\": \"계약서 확인 부탁드립니다\",\n" +
                                                    "      \"lastMessageAt\": \"2026-08-08T14:22:10\",\n" +
                                                    "      \"unreadCount\": 2\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"pageInfo\": {\n" +
                                                    "    \"currentPage\": 1,\n" +
                                                    "    \"totalPages\": 1,\n" +
                                                    "    \"totalResults\": 2,\n" +
                                                    "    \"limit\": 20,\n" +
                                                    "    \"hasNext\": false\n" +
                                                    "  }\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<PageResponse<ThreadListItem>> getThreads(
            @Parameter(description = "브랜드명 검색어 — 비우면 전체(운영자 채널 포함)", example = "글로우랩")
            @RequestParam(value = "keyword", required = false) String keyword,
            @ModelAttribute PagingRequest pagingRequest);

    @Operation(
            summary = "탭 배지용 안 읽은 수·미처리 요청 수",
            description = "폴링 대상 경량 엔드포인트(§0). `연결됨` 탭 배지(unreadCount)와 `요청함` 탭 배지(pendingRequestCount)를 " +
                    "한 번에 내려준다.\n\n**권한:** CREATOR"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ThreadSummaryResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"unreadCount\": 4,\n" +
                                                    "  \"pendingRequestCount\": 2\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<ThreadSummaryResponse> getSummary();

    @Operation(
            summary = "메시지 조회(커서 페이징)",
            description = "최신순으로 내려주며, cursor 미지정 시 최신 페이지부터 시작한다.\n\n**권한:** CREATOR(본인 스레드만)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageListResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "성공 예시",
                                            value = "{\n" +
                                                    "  \"content\": [\n" +
                                                    "    {\n" +
                                                    "      \"messageId\": 1001,\n" +
                                                    "      \"senderType\": \"CREATOR\",\n" +
                                                    "      \"mine\": true,\n" +
                                                    "      \"content\": \"네, 확인했습니다\",\n" +
                                                    "      \"attachments\": [],\n" +
                                                    "      \"createdAt\": \"2026-08-08T14:22:10\"\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"messageId\": 1000,\n" +
                                                    "      \"senderType\": \"SELLER\",\n" +
                                                    "      \"mine\": false,\n" +
                                                    "      \"content\": \"촬영본 보내드렸습니다\",\n" +
                                                    "      \"attachments\": [\n" +
                                                    "        {\n" +
                                                    "          \"attachmentId\": 501,\n" +
                                                    "          \"status\": \"UPLOADED\",\n" +
                                                    "          \"attachmentType\": \"VIDEO\",\n" +
                                                    "          \"fileUrl\": \"https://cdn.example.com/uploads/message/55/uuid.mp4\",\n" +
                                                    "          \"originalName\": \"촬영본.mp4\",\n" +
                                                    "          \"extension\": \"mp4\",\n" +
                                                    "          \"sizeBytes\": 31457280,\n" +
                                                    "          \"durationSeconds\": 58,\n" +
                                                    "          \"sortOrder\": 0\n" +
                                                    "        }\n" +
                                                    "      ],\n" +
                                                    "      \"createdAt\": \"2026-08-08T13:10:00\"\n" +
                                                    "    }\n" +
                                                    "  ],\n" +
                                                    "  \"nextCursor\": 998,\n" +
                                                    "  \"hasNext\": true\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "본인 스레드가 아님",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "권한 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"THREAD_ACCESS_DENIED\",\n" +
                                                    "  \"message\": \"해당 스레드에 대한 권한이 없습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 스레드",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "스레드 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"THREAD_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 스레드입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<MessageListResponse> getMessages(
            @Parameter(description = "스레드 ID", required = true, example = "55") @PathVariable("threadId") Long threadId,
            @Parameter(description = "커서(직전 응답의 nextCursor)", example = "998") @RequestParam(value = "cursor", required = false) Long cursor,
            @Parameter(description = "페이지 크기(기본 30)", example = "30") @RequestParam(value = "size", required = false) Integer size
    );

    @Operation(
            summary = "메시지 전송",
            description = "clientMessageId는 FE가 발급한 멱등키다. 같은 키로 재전송하면 신규 저장 대신 " +
                    "기존 메시지를 200으로 반환한다(§13-10) — 신규 전송은 201.\n\n" +
                    "**권한:** CREATOR(본인 스레드만, 휴면 스레드는 작성 불가)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "신규 전송 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageItem.class),
                            examples = {
                                    @ExampleObject(
                                            name = "신규 전송",
                                            value = "{\n" +
                                                    "  \"messageId\": 1001,\n" +
                                                    "  \"senderType\": \"CREATOR\",\n" +
                                                    "  \"mine\": true,\n" +
                                                    "  \"content\": \"네, 확인했습니다\",\n" +
                                                    "  \"attachments\": [],\n" +
                                                    "  \"createdAt\": \"2026-08-08T14:22:10\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "200",
                    description = "멱등 재전송 — 기존 메시지 반환",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MessageItem.class),
                            examples = {
                                    @ExampleObject(
                                            name = "멱등 재전송",
                                            value = "{\n" +
                                                    "  \"messageId\": 1001,\n" +
                                                    "  \"senderType\": \"CREATOR\",\n" +
                                                    "  \"mine\": true,\n" +
                                                    "  \"content\": \"네, 확인했습니다\",\n" +
                                                    "  \"attachments\": [],\n" +
                                                    "  \"createdAt\": \"2026-08-08T14:22:10\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "content와 attachmentIds가 모두 비어 있음(MESSAGE_EMPTY) 등",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "빈 메시지",
                                            value = "{\n" +
                                                    "  \"code\": \"MESSAGE_EMPTY\",\n" +
                                                    "  \"message\": \"메시지 내용 또는 첨부 중 하나는 필요합니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "첨부 개수 초과",
                                            value = "{\n" +
                                                    "  \"code\": \"ATTACHMENT_COUNT_EXCEEDED\",\n" +
                                                    "  \"message\": \"첨부는 메시지 1건당 최대 20개까지 가능합니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "첨부 용량 초과",
                                            value = "{\n" +
                                                    "  \"code\": \"ATTACHMENT_SIZE_EXCEEDED\",\n" +
                                                    "  \"message\": \"첨부 총 용량은 메시지 1건당 500MB를 초과할 수 없습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "본인 스레드가 아님",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "권한 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"THREAD_ACCESS_DENIED\",\n" +
                                                    "  \"message\": \"해당 스레드에 대한 권한이 없습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 스레드",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "스레드 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"THREAD_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 스레드입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "휴면 스레드에 전송 시도(THREAD_DORMANT) 또는 이미 다른 메시지에 연결된 첨부",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "휴면 스레드",
                                            value = "{\n" +
                                                    "  \"code\": \"THREAD_DORMANT\",\n" +
                                                    "  \"message\": \"연결이 해제된 스레드입니다. 열람만 가능합니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "첨부 이미 연결됨",
                                            value = "{\n" +
                                                    "  \"code\": \"ATTACHMENT_ALREADY_ATTACHED\",\n" +
                                                    "  \"message\": \"이미 다른 메시지에 연결된 첨부입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<MessageItem> sendMessage(
            @Parameter(description = "스레드 ID", required = true, example = "55") @PathVariable("threadId") Long threadId,
            @Valid @RequestBody SendMessageRequest request
    );

    @Operation(
            summary = "읽음 처리",
            description = "현재 스레드의 마지막 메시지까지 읽음으로 표시한다.\n\n**권한:** CREATOR(본인 스레드만)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "처리 성공 (본문 없음)"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "본인 스레드가 아님",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "권한 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"THREAD_ACCESS_DENIED\",\n" +
                                                    "  \"message\": \"해당 스레드에 대한 권한이 없습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 스레드",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "스레드 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"THREAD_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 스레드입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<Void> markRead(
            @Parameter(description = "스레드 ID", required = true, example = "55") @PathVariable("threadId") Long threadId
    );

    @Operation(
            summary = "첨부 업로드용 Presigned URL 발급",
            description = "서버가 확장자(§2-1)·개별 파일 크기를 먼저 검증하고 PENDING 첨부 행을 선(先)생성한 뒤 " +
                    "S3 presigned PUT URL을 발급한다(§4-1 ①). 실제 파일은 서버를 거치지 않고 이 URL로 S3에 " +
                    "직접 PUT한다 — 응답의 requiredContentType과 정확히 같은 Content-Type 헤더로 PUT해야 한다.\n\n" +
                    "**권한:** CREATOR(본인 스레드만)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "발급 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PresignResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "발급 성공",
                                            value = "{\n" +
                                                    "  \"attachmentId\": 501,\n" +
                                                    "  \"uploadUrl\": \"https://bucket.s3.ap-northeast-2.amazonaws.com/uploads/message/55/uuid.mp4?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=900&...\",\n" +
                                                    "  \"requiredContentType\": \"video/mp4\",\n" +
                                                    "  \"expiresInSeconds\": 900\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "허용되지 않는 확장자 또는 500MB 초과 단일 파일",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "확장자 미허용",
                                            value = "{\n" +
                                                    "  \"code\": \"ATTACHMENT_EXTENSION_NOT_ALLOWED\",\n" +
                                                    "  \"message\": \"허용되지 않는 파일 형식입니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "용량 초과",
                                            value = "{\n" +
                                                    "  \"code\": \"ATTACHMENT_SIZE_EXCEEDED\",\n" +
                                                    "  \"message\": \"첨부 총 용량은 메시지 1건당 500MB를 초과할 수 없습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "본인 스레드가 아님",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "권한 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"THREAD_ACCESS_DENIED\",\n" +
                                                    "  \"message\": \"해당 스레드에 대한 권한이 없습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 스레드",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "스레드 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"THREAD_NOT_FOUND\",\n" +
                                                    "  \"message\": \"존재하지 않는 스레드입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<PresignResponse> createPresignedUpload(
            @Parameter(description = "스레드 ID", required = true, example = "55") @PathVariable("threadId") Long threadId,
            @Valid @RequestBody PresignRequest request
    );

    @Operation(
            summary = "첨부 업로드 완료 통지",
            description = "S3 직접 PUT이 끝난 뒤 호출한다. 서버가 HeadObject로 실제 업로드 크기·Content-Type을 " +
                    "재확인해 UPLOADED로 전환한다(§4-1 ③). 선언 값과 실측이 어긋나면 400이 아니라 200 + " +
                    "status=REJECTED로 응답한다 — FE는 응답의 status 필드로 성공/거부를 판정해야 한다.\n\n" +
                    "**권한:** CREATOR(본인이 올린 첨부만)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "검증 완료 — status로 UPLOADED/REJECTED 판정",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AttachmentSummary.class),
                            examples = {
                                    @ExampleObject(
                                            name = "업로드 완료",
                                            value = "{\n" +
                                                    "  \"attachmentId\": 501,\n" +
                                                    "  \"status\": \"UPLOADED\",\n" +
                                                    "  \"attachmentType\": \"VIDEO\",\n" +
                                                    "  \"fileUrl\": \"https://cdn.example.com/uploads/message/55/uuid.mp4\",\n" +
                                                    "  \"originalName\": \"촬영본.mp4\",\n" +
                                                    "  \"extension\": \"mp4\",\n" +
                                                    "  \"sizeBytes\": 31457280,\n" +
                                                    "  \"durationSeconds\": 58,\n" +
                                                    "  \"sortOrder\": null\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "검증 거부",
                                            value = "{\n" +
                                                    "  \"attachmentId\": 501,\n" +
                                                    "  \"status\": \"REJECTED\",\n" +
                                                    "  \"attachmentType\": \"VIDEO\",\n" +
                                                    "  \"fileUrl\": null,\n" +
                                                    "  \"originalName\": \"촬영본.mp4\",\n" +
                                                    "  \"extension\": \"mp4\",\n" +
                                                    "  \"sizeBytes\": 31457280,\n" +
                                                    "  \"durationSeconds\": null,\n" +
                                                    "  \"sortOrder\": null\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "S3에 아직 업로드되지 않음(ATTACHMENT_NOT_UPLOADED)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "업로드 미완료",
                                            value = "{\n" +
                                                    "  \"code\": \"ATTACHMENT_NOT_UPLOADED\",\n" +
                                                    "  \"message\": \"업로드가 완료되지 않은 첨부입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "본인이 올린 첨부가 아님",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "권한 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"ATTACHMENT_ACCESS_DENIED\",\n" +
                                                    "  \"message\": \"해당 첨부에 대한 권한이 없습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<AttachmentSummary> completeUpload(
            @Parameter(description = "첨부 ID", required = true, example = "501") @PathVariable("attachmentId") Long attachmentId,
            @Valid @RequestBody(required = false) CompleteAttachmentRequest request
    );

    @Operation(
            summary = "첨부 다운로드 URL 발급",
            description = "대화에 첨부된 파일을 내려받기 위한 presigned GET URL을 발급한다(§13-8). " +
                    "FE는 응답의 downloadUrl로 이동시키기만 하면 되며, 원본 파일명으로 저장되도록 " +
                    "Content-Disposition이 서명에 포함돼 있다.\n\n" +
                    "메시지 목록의 `fileUrl`은 미리보기·재생용이다 — **저장은 반드시 이 API로** 받아야 " +
                    "파일명이 UUID가 아닌 원본 이름으로 떨어진다.\n\n" +
                    "URL은 300초 후 만료되므로 캐시하지 말고 클릭 시점에 호출한다. 한 메시지의 첨부를 " +
                    "`전체 다운로드`할 때는 첨부 개수만큼 각각 호출한다(§13-9 — 서버 압축 없음).\n\n" +
                    "메시지에 아직 연결되지 않은(전송 전) 첨부는 업로드한 본인만 다운로드할 수 있다 — " +
                    "같은 스레드 참가자라도 상대가 보낸 뒤여야 받을 수 있다.\n\n" +
                    "**권한:** CREATOR(본인 스레드의 첨부만 — 상대가 보낸 첨부도 포함)"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "발급 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AttachmentDownloadResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "발급 성공",
                                            value = "{\n" +
                                                    "  \"attachmentId\": 501,\n" +
                                                    "  \"downloadUrl\": \"https://bucket.s3.ap-northeast-2.amazonaws.com/uploads/message/55/uuid.mp4?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=300&response-content-disposition=attachment%3B%20filename%2A%3DUTF-8%27%27...\",\n" +
                                                    "  \"originalName\": \"촬영본.mp4\",\n" +
                                                    "  \"sizeBytes\": 31457280,\n" +
                                                    "  \"expiresInSeconds\": 300\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "업로드가 완료되지 않은 첨부(ATTACHMENT_NOT_UPLOADED)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "업로드 미완료",
                                            value = "{\n" +
                                                    "  \"code\": \"ATTACHMENT_NOT_UPLOADED\",\n" +
                                                    "  \"message\": \"업로드가 완료되지 않은 첨부입니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "인증 실패",
                                            value = "{\n" +
                                                    "  \"code\": \"UNAUTHORIZED\",\n" +
                                                    "  \"message\": \"인증 정보가 유효하지 않습니다. 다시 로그인해주세요.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "존재하지 않거나 접근 권한이 없는 첨부 / 본인 스레드가 아님",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "첨부 권한 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"ATTACHMENT_ACCESS_DENIED\",\n" +
                                                    "  \"message\": \"해당 첨부에 대한 권한이 없습니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "스레드 권한 없음",
                                            value = "{\n" +
                                                    "  \"code\": \"THREAD_ACCESS_DENIED\",\n" +
                                                    "  \"message\": \"해당 스레드에 대한 권한이 없습니다.\"\n" +
                                                    "}"
                                    ),
                                    @ExampleObject(
                                            name = "미전송 첨부",
                                            value = "{\n" +
                                                    "  \"code\": \"ATTACHMENT_ACCESS_DENIED\",\n" +
                                                    "  \"message\": \"해당 첨부에 대한 권한이 없습니다.\"\n" +
                                                    "}"
                                    )
                            }
                    )
            )
    })
    ResponseEntity<AttachmentDownloadResponse> getDownloadUrl(
            @Parameter(description = "첨부 ID", required = true, example = "501") @PathVariable("attachmentId") Long attachmentId
    );
}
