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
         * 인플루언서 정지 시 공구 게시물 제출까지 막을지(§55 B-11) — 켜면 정지된 인플루언서의 준비완료 공구가 게시물 게이트에서 막힌다. 기본 꺼짐.
         */
        private boolean blockGroupBuyPost = false;
    }
}
