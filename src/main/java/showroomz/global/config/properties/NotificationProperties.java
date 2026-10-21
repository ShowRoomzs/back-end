package showroomz.global.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 알림함 · 소비자 푸시 정책값(1017 설계서 1-6 · 2-4). 시안이 정하지 않은 시각은 가정값이다(12절 Q6).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    /** 광고성 푸시 야간 미발송 시작 · 끝(시 · KST) — C15 「밤 9시~아침 8시에는 보내지 않아요」. 거래 알림은 제한 없음. */
    private int quietStartHour = 21;
    private int quietEndHour = 8;

    /** 팔로우 쇼룸 새 게시물 하루 묶음 발송 시각 — 야간 직전에 하루치가 모인다. */
    private int digestHour = 20;

    /** 스케줄러(지연 푸시 · 보관 · 마감 D-1 · 구매확정 D-1 · 서명 기한 D-1) — 테스트는 끄고 서비스를 직접 부른다. */
    private boolean schedulerEnabled = true;

    /** 지연 푸시 한 번에 보내는 건수. */
    private int outboxBatchSize = 200;
}
