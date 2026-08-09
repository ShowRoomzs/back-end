package showroomz.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.auth.token.AuthTokenProvider;
import showroomz.api.seller.auth.refreshToken.SellerRefreshTokenRepository;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.domain.bank.repository.BankRepository;
import showroomz.domain.changerequest.repository.BrandChangeRequestRepository;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.global.service.MailService;

import java.util.Date;
import java.util.function.Supplier;

/** 통합 테스트 공통 배선 — MockMvc·토큰 발급·DB 정리·메일 발송 검증 지점. */
@IntegrationTest
public abstract class IntegrationTestSupport {

    protected static final String RAW_PASSWORD = BrandFixture.RAW_PASSWORD;

    private static final long TOKEN_VALID_MILLIS = 30 * 60 * 1000L;

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected TransactionTemplate transactionTemplate;

    @Autowired
    protected SellerRepository sellerRepository;
    @Autowired
    protected MarketRepository marketRepository;
    @Autowired
    protected BrandChangeRequestRepository changeRequestRepository;
    @Autowired
    protected SellerRefreshTokenRepository sellerRefreshTokenRepository;
    @Autowired
    protected BankRepository bankRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AuthTokenProvider tokenProvider;

    /**
     * 메일은 실제로 보내지 않되 <b>호출 여부와 인자는 검증한다</b> — 승인·반려·이메일 변경 통지는
     * 기획상 기능의 일부(§16-5)여서 "조용히 안 보내는" 회귀를 잡아야 한다.
     */
    @MockitoBean
    protected MailService mailService;

    protected BrandFixture fixture;
    private DatabaseCleaner databaseCleaner;
    protected ChangeRequestSteps changeRequests;

    @BeforeEach
    void setUpSupport() {
        fixture = new BrandFixture(sellerRepository, marketRepository, bankRepository, passwordEncoder, jdbcTemplate);
        databaseCleaner = new DatabaseCleaner(jdbcTemplate);
        changeRequests = new ChangeRequestSteps(mockMvc, objectMapper);
    }

    @AfterEach
    void cleanUpDatabase() {
        databaseCleaner.clear();
    }

    /** 실제 로그인 토큰과 같은 방식으로 서명한다 — 인증 필터·권한 규칙까지 함께 검증하려는 의도다. */
    protected String sellerToken(Seller seller) {
        return bearerToken(seller.getEmail(), RoleType.SELLER, seller.getId());
    }

    protected String adminToken(Seller admin) {
        return bearerToken(admin.getEmail(), RoleType.ADMIN, admin.getId());
    }

    protected String bearerToken(String email, RoleType roleType, Long userId) {
        Date expiry = new Date(System.currentTimeMillis() + TOKEN_VALID_MILLIS);
        return "Bearer " + tokenProvider.createAuthToken(email, roleType.getCode(), userId, expiry).getToken();
    }

    protected String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("테스트 요청 본문 직렬화 실패", e);
        }
    }

    /** 지연 로딩이 필요한 검증(예: 요청 → 마켓 → 판매자)을 위한 읽기 트랜잭션 경계. */
    protected <T> T inTransaction(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }
}
