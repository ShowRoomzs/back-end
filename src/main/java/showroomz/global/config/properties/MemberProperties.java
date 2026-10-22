package showroomz.global.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 회원 제재 정책값(§51 · §55 B-11 미정 항목) — 서비스에 {@code @Value} 로 흩지 않고 한 곳에 둔다(정합 계획서 R1-9 · {@link PostProperties}와
 * 같은 관례). 미확정 값의 기본은 「막지 않는 쪽」이다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "member")
public class MemberProperties {

    private Sanction sanction = new Sanction();

    @Getter
    @Setter
    public static class Sanction {
        /**
         * 인플루언서 정지 시 공구 게시물 등록(검토 요청)까지 막을지(§55 B-11) — ui-studio-13 rev.1 이 「등록 차단 · 작성 · 임시저장 허용」으로
         * 확정했다(1017 설계서 8-2). 끌 수 있게 플래그는 남긴다.
         */
        private boolean blockGroupBuyPost = true;

        /** 이의 신청 기한 — 통지 + N영업일(1021 기-02 【잠정】 · 표시용 · 접수는 막지 않는다). */
        private int appealBusinessDays = 3;

        /** 소명 검토 N영업일 — 운영자 처리 기한 · 표시만. 미정이면 null(1021 기-02 [정책값 미정]). */
        private Integer appealReviewBusinessDays;

        /** 정지 기간 종료 자동 해제 배치(매일 00:05 · 기동 시 1회). */
        private boolean autoReleaseEnabled = true;
    }
}
