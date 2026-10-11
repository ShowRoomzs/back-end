package showroomz.global.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 포트원 파트너 정산(Platform) 전용 설정(44_포트원_파트너정산_연동_BE_설계서.md 6-3). 상점 · base URL 은 결제의 {@code portone.*}
 * 를 그대로 쓰고, 여기에는 Platform 에서만 쓰는 값만 둔다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "portone.platform")
public class PortOnePlatformProperties {

    /** 지급 권한이 있는 Secret 을 결제용과 분리하고 싶을 때 — 비우면 {@code portone.api-secret}. 로그에 남기지 않는다. */
    private String apiSecret;

    private int readTimeoutMillis = 10_000;

    /** 테스트 모드({@code PG_TEST})의 파트너 · 정산건 id 접두 — 운영 id 와 섞여 보이지 않게. */
    private String partnerIdPrefixTest = "t-";

    /** 기동 시 {@code getPlatformSetting} 1회 호출(활성화 · deductWht=false 검증). 포트원 장애로 앱이 못 뜨는 것이 싫으면 끈다. */
    private boolean startupCheck = true;

    /** 수취자 통장에 찍히는 문구 — 콘솔 기본값과 같게 둔다(정산건 메모와 별개). */
    private String withdrawalMemo = "SHOWROOMZ 정산";
}
