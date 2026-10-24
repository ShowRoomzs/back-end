package showroomz.global.payment.portone.identity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import showroomz.global.payment.portone.PaymentGatewayException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 테스트 · 로컬 무자격 실행용 본인인증 게이트웨이({@code portone.enabled=false} 또는 {@code portone.identity.enabled=false} ·
 * 57 설계서 2-3). {@code FakePaymentGateway} 와 같은 「시나리오를 지정하는 테스트 더블」이다.
 *
 * <p>기본 동작은 「시작 API 가 발급한 id 를 조회하면 {@code VERIFIED}(고정 인물)」라 자격 없이 가입 · 재인증이 돈다.
 * 기본 인물의 DI 는 <b>이름이 아니라 사용자(username)마다 결정되는 값</b>이어야 중복 차단 테스트가 자연스럽다 — 그래서 서비스가
 * {@link #bind(String, String)} 로 id ↔ username 을 알려 주고, 여기서 {@code hash("fake-di:" + username)} 을 돌려준다.
 * 통합 테스트는 {@link #will(String, Scenario)} 로 READY · FAILED · 14세 미만 · 중복 · DI 누락 · 타임아웃 · 404 를 만든다.
 */
@Slf4j
@Component
@ConditionalOnExpression("!${portone.enabled:true} or !${portone.identity.enabled:true}")
public class FakeIdentityGateway implements PortOneIdentityGateway {

    public static final String STORE_ID = "store-fake";
    public static final String CHANNEL_KEY = "channel-key-fake-identity";

    /** 임시 데이터 시절(PASS 연동 전 `IdentityVerificationService`)의 고정 인물 — 기존 테스트가 기대하는 값 그대로다. */
    public static final String DEFAULT_NAME = "홍길동";
    public static final String DEFAULT_BIRTH_DATE = "1998-04-12";
    public static final String DEFAULT_GENDER = "FEMALE";
    public static final String DEFAULT_PHONE = "01000000000";
    public static final String DEFAULT_OPERATOR = "SKT";
    public static final String FAILED_PG_CODE = "CS24";

    public enum Scenario {
        /** 기본 — VERIFIED · 고정 인물 · DI 는 username 결정값 */
        VERIFIED,
        /** 창만 닫혔다 — 포트원은 아직 READY */
        READY,
        /** 포트원 FAILED(pgCode CS24) */
        FAILED,
        /** 생년월일 = 오늘 − 13년 */
        UNDER_14,
        /** DI 가 비어서 온다(설정 오류) */
        NO_DI,
        /** 통신 실패(결과 모름) */
        TIMEOUT,
        /** 포트원이 모르는 id */
        NOT_FOUND
    }

    private final Map<String, Scenario> scenarios = new ConcurrentHashMap<>();
    private final Map<String, String> usernames = new ConcurrentHashMap<>();
    /** 시나리오로 지정한 DI(중복 테스트용) — 없으면 username 결정값. */
    private final Map<String, String> diOverrides = new ConcurrentHashMap<>();
    private final AtomicInteger lookupCalls = new AtomicInteger();

    // ------------------------------------------------------------------ 시나리오

    public void reset() {
        scenarios.clear();
        usernames.clear();
        diOverrides.clear();
        lookupCalls.set(0);
    }

    public void will(String identityVerificationId, Scenario scenario) {
        scenarios.put(identityVerificationId, scenario);
    }

    /** VERIFIED 인데 DI 를 지정한다 — 다른 사용자가 같은 DI 로 인증하는 중복 시나리오. */
    public void willReturnDi(String identityVerificationId, String di) {
        scenarios.put(identityVerificationId, Scenario.VERIFIED);
        diOverrides.put(identityVerificationId, di);
    }

    /** 서비스가 시작 때 부른다 — 기본 인물의 DI 를 사용자마다 다르게 만들기 위한 연결. */
    public void bind(String identityVerificationId, String username) {
        usernames.put(identityVerificationId, username);
    }

    public int lookupCalls() {
        return lookupCalls.get();
    }

    /** 테스트가 「같은 사람」의 DI 를 미리 알고 싶을 때. */
    public static String defaultDiOf(String username) {
        return sha256("fake-di:" + username);
    }

    public static String defaultCiOf(String username) {
        return sha256("fake-ci:" + username);
    }

    // ------------------------------------------------------------------ 게이트웨이

    @Override
    public String storeId() {
        return STORE_ID;
    }

    @Override
    public String channelKey() {
        return CHANNEL_KEY;
    }

    @Override
    public Optional<IdentityVerificationResult> find(String id) {
        lookupCalls.incrementAndGet();
        Scenario scenario = scenarios.getOrDefault(id, Scenario.VERIFIED);
        String username = usernames.getOrDefault(id, id);
        return switch (scenario) {
            case NOT_FOUND -> Optional.empty();
            case TIMEOUT -> throw new PaymentGatewayException("포트원 본인인증 조회 타임아웃(fake)");
            case READY -> Optional.of(IdentityVerificationResult.ready(id));
            case FAILED -> Optional.of(IdentityVerificationResult.failed(id,
                    new IdentityVerificationResult.Failure("PG_PROVIDER", FAILED_PG_CODE, "정보조회가 올바르지 않습니다."), "pg-" + id));
            case UNDER_14 -> Optional.of(verified(id, LocalDate.now().minusYears(13).toString(), defaultDiOf(username), username));
            case NO_DI -> Optional.of(verified(id, DEFAULT_BIRTH_DATE, null, username));
            case VERIFIED -> Optional.of(verified(id, DEFAULT_BIRTH_DATE, diOverrides.getOrDefault(id, defaultDiOf(username)), username));
        };
    }

    private static IdentityVerificationResult verified(String id, String birthDate, String di, String username) {
        IdentityVerificationResult.VerifiedCustomer customer = new IdentityVerificationResult.VerifiedCustomer(
                DEFAULT_NAME, birthDate, DEFAULT_GENDER, DEFAULT_PHONE, DEFAULT_OPERATOR, false, defaultCiOf(username), di);
        return IdentityVerificationResult.verified(id, customer, "pg-" + id, LocalDateTime.now());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
