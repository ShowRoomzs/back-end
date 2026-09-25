package showroomz.global.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공구 운영 정책값 — 전부 {@code [근거 대기]}·{@code [미정]}이라 설정값으로 뺀다(설계서 7-2).
 *
 * <p>미확정 구간의 기본값은 <b>당사자에게 불리한 간주를 하지 않는 쪽</b>이다 — 무응답 자동 이행은 꺼져 있고
 * ({@link Fulfillment#autoConfirmOnTimeout} = false), 백필 러너도 꺼져 있다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "groupbuy")
public class GroupBuyProperties {

    /** 수명주기 스케줄러(오픈·종료·자동 이행) 가동 여부. 통합 테스트는 끄고 서비스를 직접 부른다. */
    private boolean schedulerEnabled = true;

    private Extension extension = new Extension();
    private Fulfillment fulfillment = new Fulfillment();
    private Appeal appeal = new Appeal();
    private Backfill backfill = new Backfill();

    @Getter
    @Setter
    public static class Extension {
        /** 연장 후 총 공구 일수 상한 — 계약 H4와 같은 양끝 포함 계산(§33-1 #6). */
        private int maxTotalDays = 30;
        /** 공구 종료 N시간 전까지만 연장을 요청할 수 있다(C1). */
        private int requestCutoffHours = 12;
    }

    @Getter
    @Setter
    public static class Fulfillment {
        /** 이행 확인 기한 — 종료 시각 + N일로 종료 전이 때 확정·저장한다(§33-1 #5 미정 · 시안 머리말 3일). */
        private int dueDays = 3;
        /**
         * 기한 경과 시 무응답 측을 이행으로 간주할지. §33-3 D-1 「약관 근거 없음」으로 <b>기본 꺼짐</b>이다.
         * 꺼진 동안 기한은 표시값이고 확인은 계속 받는다(설계서 1-9).
         */
        private boolean autoConfirmOnTimeout = false;
    }

    @Getter
    @Setter
    public static class Appeal {
        /** 소명 증빙 개수 상한 — 기획에 없어 5개로 잠정(설계서 7-2 #11). */
        private int maxAttachments = 5;
        /** 소명 증빙 파일 크기 상한 — C9 「10MB 이하」. */
        private long maxAttachmentBytes = 10L * 1024 * 1024;
    }

    @Getter
    @Setter
    public static class Backfill {
        /** 체결완료인데 공구가 없는 계약을 기동 시 1회 생성한다(설계서 2-3). 멱등이지만 기본은 꺼둔다. */
        private boolean enabled = false;
    }
}
