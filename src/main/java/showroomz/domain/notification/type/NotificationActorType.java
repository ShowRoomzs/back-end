package showroomz.domain.notification.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 알림 줄 머리 — 파트너 「운영팀 / 뷰티로그 / 시스템」 · 스튜디오 「브랜드 · 운영팀 · SHOWROOMZ」(1017 설계서 1-2). */
@Getter
@RequiredArgsConstructor
public enum NotificationActorType {
    OPERATOR("운영팀"),
    BRAND("브랜드"),
    CREATOR("인플루언서"),
    SYSTEM("시스템"),
    /** 정산 지급 등 플랫폼 이름으로 나가는 사건 — 스튜디오 「SHOWROOMZ」. */
    PLATFORM("SHOWROOMZ"),
    /** 소비자 알림의 쇼룸 소식. */
    SHOWROOM("쇼룸");

    private final String label;
}
