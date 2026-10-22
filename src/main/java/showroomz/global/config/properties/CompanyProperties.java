package showroomz.global.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 운영자(통신판매중개자) 사업자 정보 — 1021 설계서 10-1 · 결정 노트 기-35.
 *
 * <p>소비자 앱 하단(C 마이 · C4 쇼룸 · C7 상품 상세 · 디자인 시스템 Footer) · 세금계산서 공급받는자 · 원천징수영수증 발행자가
 * <b>한 출처</b>를 쓴다. 정산의 {@code settlement.platform.*} 는 yml 에서 이 값을 참조한다(기존 환경변수는 그대로 덮어쓴다).
 * 빈 값은 「아직 정하지 않음」 — 응답에 그대로 내리고 FE 가 「[확인 필요]」 · 「신고 예정」으로 그린다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "company")
public class CompanyProperties {

    /** 상호 — 「(주)」 없음(기-35). */
    private String name;
    private String representative;
    private String registrationNumber;
    private String address;
    /** 통신판매업 신고번호 — 비면 「신고 예정 [출시 전 확정]」. */
    private String mailOrderReportNumber;
    /** 세금계산서 수신 이메일 — [확인 필요]. */
    private String taxEmail;
    private String copyright = "© 2026 Showroomz";
    private PrivacyOfficer privacyOfficer = new PrivacyOfficer();
    private CustomerService cs = new CustomerService();

    @Getter
    @Setter
    public static class PrivacyOfficer {
        private String name;
        private String phone;
    }

    /** 고객센터 — 시안 값(1500-0000 · help@)은 예시라 비워 둔다([확인 필요]). */
    @Getter
    @Setter
    public static class CustomerService {
        private String phone;
        private String email;
        private String hours;
    }
}
