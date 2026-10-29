package showroomz.api.common.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.domain.notification.type.NotificationActorType;
import showroomz.domain.notification.type.NotificationImageKind;
import showroomz.domain.notification.type.NotificationTargetType;
import showroomz.domain.notification.type.NotificationType;

import java.time.LocalDateTime;

/**
 * 알림함 한 줄(1017 설계서 1-7) — 소비자 C11 · 파트너 16 종 드롭다운 · 스튜디오 14 H1a 가 같은 꼴을 쓴다. 문구는 발생 시점에 굳힌 값이다.
 * 이동은 {@code target} — 목록이 아니라 그 건의 상세로 가고, 지워진 대상이면 상세 404 를 받아 토스트로 알린다.
 */
@Schema(description = "알림 한 줄")
public record NotificationItem(
        Long notificationId,
        @Schema(example = "CONTRACT_REVIEW_REJECTED") NotificationType type,
        @Schema(description = "분류 태그 — 스튜디오 「계약 · 공구 · 연결 · 정산」 · 파트너 메뉴명 · 소비자 아이콘 분기", example = "계약")
        String category,
        @Schema(description = "줄 머리 — OPERATOR(운영팀) · BRAND · CREATOR · SYSTEM(시스템) · PLATFORM(SHOWROOMZ) · SHOWROOM")
        NotificationActorType actorType,
        @Schema(description = "줄 머리 표시 — 「운영팀」 · 상대 이름 · 「시스템」 · 「SHOWROOMZ」", example = "뷰티로그") String actorLabel,
        @Schema(example = "계약 「아이크림 신제품 공구」가 반려되었습니다 — 리워드율 표기 확인 필요") String title,
        @Schema(description = "보조 줄 · 할 일 문구(「회수 송장 등록 필요」)", nullable = true) String body,
        @Schema(description = "C11 굵게 표시할 부분 문자열(상품명 · 쇼룸명 · 금액)", nullable = true) String emphasis,
        Target target,
        @Schema(description = "C11 앞 이미지 — THUMB(상품) · AVATAR(쇼룸) · ICON(계정 · 서비스)", nullable = true)
        NotificationImageKind imageKind,
        @Schema(description = "THUMB · AVATAR 이미지 URL", nullable = true) String imageUrl,
        @Schema(description = "AVATAR — 그 쇼룸에 지금 진행 중인 공구가 있으면 로즈 링") boolean liveGroupBuy,
        @Schema(description = "광고성 — 시간 앞 「광고」 표기") boolean advertising,
        @Schema(description = "할 일 기한(로즈 한 줄)", nullable = true) LocalDateTime todoDueAt,
        boolean read,
        LocalDateTime createdAt) {

    @Schema(name = "NotificationTarget", description = "누르면 갈 곳 — type 별 상세 화면. 없으면 이동하지 않는다")
    public record Target(
            @Schema(nullable = true) NotificationTargetType type,
            @Schema(description = "대상 id — ORDER_GROUP 은 하위주문 id · SHOWROOM 은 creatorId(쇼룸 API 의 showroomId 와 같은 값) · "
                    + "CLAIM 은 클레임 id · THREAD 는 스레드 id", nullable = true) Long id,
            @Schema(description = "부가 식별 — 문의 종류(ONE_TO_ONE · PRODUCT) · 스레드 메시지 id(카드 위치) · "
                    + "BASIC_INFO_TAB 의 탭 키(소문자 — account · settlement · 변경 요청 유형 소문자)", nullable = true)
            String sub,
            @Schema(description = "주문 id — ORDER_GROUP 일 때만(앱 주문 상세 경로용). 그 밖은 null", nullable = true)
            Long orderId) {
    }
}
