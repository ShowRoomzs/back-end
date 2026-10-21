package showroomz.api.app.notification.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.common.notification.dto.NotificationItem;
import showroomz.global.dto.PageResponse;

@Tag(name = "User - Notifications", description = "C11 알림 — 목록 · 읽음 · 종 배지 초기화")
public interface UserNotificationControllerDocs {

    @Operation(
            summary = "알림 목록(C11)",
            description = """
                    최근 90일 알림을 최신순으로 내립니다. 날짜 구분(오늘 · 최근 7일 · 이전)은 FE 가 `createdAt` 으로 나눕니다.

                    - 앞 이미지 — `imageKind` THUMB(상품 썸네일) · AVATAR(쇼룸 · `liveGroupBuy=true` 면 로즈 링) · ICON(계정 · 서비스)
                    - 굵게 — `title` 안의 `emphasis` 부분 문자열
                    - 할 일 — `todoDueAt` 이 있으면 로즈 한 줄 + 기한(`body` 가 문구)
                    - 광고 — `advertising=true` 면 시간 앞에 「광고」. 광고성 수신 미동의면 행 자체가 없다
                    - 누르면 `POST /{id}/read` 후 `target` 으로 이동. 대상이 지워졌으면 상세 404 → 토스트

                    목록에 들어오면 `POST /badge/seen` 으로 종 배지를 초기화하세요(로즈 점은 탭해야 사라진다).
                    이용 정지 계정도 열람할 수 있습니다.

                    **권한:** USER/CREATOR""")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "조회 성공"))
    ResponseEntity<PageResponse<NotificationItem>> getNotifications(
            @Parameter(description = "1부터", example = "1") int page,
            @Parameter(example = "20") int size);

    @Operation(summary = "알림 읽음", description = "탭한 알림의 로즈 점을 지웁니다. 멱등입니다.\n\n**권한:** USER/CREATOR")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "읽음"),
            @ApiResponse(responseCode = "404", description = "내 알림이 아니거나 보관 기간이 지났다(NOTIFICATION_NOT_FOUND)")})
    ResponseEntity<Void> markRead(Long notificationId);

    @Operation(summary = "종 배지 초기화", description = "C11 목록에 들어올 때 1회 부릅니다. 홈 요약의 `notificationBadgeCount` 가 0이 됩니다.\n\n**권한:** USER/CREATOR")
    @ApiResponses(@ApiResponse(responseCode = "204", description = "초기화"))
    ResponseEntity<Void> markBadgeSeen();
}
