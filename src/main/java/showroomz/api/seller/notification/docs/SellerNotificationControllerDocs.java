package showroomz.api.seller.notification.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import showroomz.api.common.notification.dto.NotificationItem;
import showroomz.api.common.notification.dto.NotificationSummary;
import showroomz.global.dto.PageResponse;

@Tag(name = "Seller - Notifications", description = "파트너센터 알림 API")
public interface SellerNotificationControllerDocs {

    @Operation(
            summary = "알림 목록(종 드롭다운)",
            description = """
                    브랜드가 아닌 쪽(운영팀 · 인플루언서 · 시스템)이 처리해 브랜드 상황이 바뀐 일만 옵니다(16 알림 표 20행).
                    소비자 쪽 사건(신규 주문 · 취소 요청 · 반품 신청)은 알림이 아니라 홈 처리 대기 숫자로 보입니다.

                    - 줄 머리 `actorLabel`(운영팀 · 인플루언서 쇼룸명 · 시스템) · 안 읽은 줄은 `read=false`(연한 인디고 면 + 점)
                    - 누르면 `POST /{id}/read` 후 `target` 의 **상세**로 이동하고 드롭다운을 닫는다(목록이 아니다 · 지금 상태의 상세)
                    - 정지 · 직권 중단 사전통지는 운영팀 채널 카드로 간다(`target.type = THREAD`, `target.sub` = 카드 message id)

                    **권한:** SELLER""")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN — SELLER · CREATOR 가 아님"),
            @ApiResponse(responseCode = "404", description = "SELLER_NOT_FOUND · MARKET_NOT_FOUND")})
    ResponseEntity<PageResponse<NotificationItem>> getNotifications(
            @Parameter(description = "true 면 안 읽음 탭") boolean unreadOnly,
            @Parameter(example = "1") int page,
            @Parameter(example = "20") int size);

    @Operation(summary = "안 읽은 수(종 배지)", description = "인디고 숫자 배지 — 모든 페이지 탑바가 60초마다 폴링합니다.\n\n**권한:** SELLER")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN — SELLER · CREATOR 가 아님"),
            @ApiResponse(responseCode = "404", description = "SELLER_NOT_FOUND · MARKET_NOT_FOUND")})
    ResponseEntity<NotificationSummary> getSummary();

    @Operation(summary = "알림 읽음", description = "**권한:** SELLER")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "읽음"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN — SELLER · CREATOR 가 아님"),
            @ApiResponse(responseCode = "404", description = "NOTIFICATION_NOT_FOUND · SELLER_NOT_FOUND · MARKET_NOT_FOUND")})
    ResponseEntity<Void> markRead(@Parameter(description = "알림 ID", example = "1024") Long notificationId);

    @Operation(summary = "모두 읽음", description = "드롭다운 [모두 읽음] — 배지가 0이 됩니다.\n\n**권한:** SELLER")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "모두 읽음"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN — SELLER · CREATOR 가 아님"),
            @ApiResponse(responseCode = "404", description = "SELLER_NOT_FOUND · MARKET_NOT_FOUND")})
    ResponseEntity<Void> markAllRead();
}
