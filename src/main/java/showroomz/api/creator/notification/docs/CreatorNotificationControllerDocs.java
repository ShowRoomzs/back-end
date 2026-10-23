package showroomz.api.creator.notification.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.common.notification.dto.NotificationItem;
import showroomz.api.common.notification.dto.NotificationSummary;
import showroomz.global.dto.PageResponse;

@Tag(name = "Creator - Notifications", description = "쇼룸 스튜디오 알림 API")
public interface CreatorNotificationControllerDocs {

    @Operation(
            summary = "알림 목록(종 드롭다운)",
            description = """
                    운영팀 · 브랜드 · SHOWROOMZ 가 처리한 결과만 옵니다 — 내가 한 일은 알리지 않고, 새 메시지는 알림이 아닙니다(연결·소통 배지가 맡는다).

                    - 한 줄 = 분류 태그(`category` 계약 · 공구 · 연결 · 정산) · 내용 · 누가(`actorLabel`) · 언제 · 이동할 곳(`target`)
                    - 알림 안에 버튼을 두지 않습니다 — 누르면 `POST /{id}/read` 후 상세로 이동
                    - [모두 읽음]은 `POST /read-all`

                    **권한:** CREATOR""")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN — CREATOR 가 아님"),
            @ApiResponse(responseCode = "404", description = "CREATOR_NOT_FOUND")})
    ResponseEntity<PageResponse<NotificationItem>> getNotifications(
            @Parameter(description = "true 면 안 읽음만") boolean unreadOnly,
            @Parameter(example = "1") int page,
            @Parameter(example = "20") int size);

    @Operation(summary = "안 읽은 수(종 배지)", description = "**권한:** CREATOR")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN — CREATOR 가 아님"),
            @ApiResponse(responseCode = "404", description = "CREATOR_NOT_FOUND")})
    ResponseEntity<NotificationSummary> getSummary();

    @Operation(summary = "알림 읽음", description = "**권한:** CREATOR")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "읽음"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN — CREATOR 가 아님"),
            @ApiResponse(responseCode = "404", description = "NOTIFICATION_NOT_FOUND · CREATOR_NOT_FOUND")})
    ResponseEntity<Void> markRead(@Parameter(description = "알림 ID", example = "1024") Long notificationId);

    @Operation(summary = "모두 읽음", description = "**권한:** CREATOR")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "모두 읽음"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN — CREATOR 가 아님"),
            @ApiResponse(responseCode = "404", description = "CREATOR_NOT_FOUND")})
    ResponseEntity<Void> markAllRead();
}
