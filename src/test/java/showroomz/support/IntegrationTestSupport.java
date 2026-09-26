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
import showroomz.api.admin.contract.service.ContractPdfRenderer;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.app.auth.token.AuthTokenProvider;
import showroomz.api.seller.auth.refreshToken.SellerRefreshTokenRepository;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.domain.bank.repository.BankRepository;
import showroomz.domain.changerequest.repository.BrandChangeRequestRepository;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.service.MailService;

import java.util.Date;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

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

    /**
     * 계약서 PDF 렌더러 — 통합 테스트는 실제 Chromium을 띄우지 않는다. 검토 요청이 제출본을 만들므로
     * 여기서 막지 않으면 Chromium 설치 여부·S3 자격 증명에 따라 결과가 달라진다(실제 렌더링은 contract-pdf 태그).
     *
     * <p>기본은 <b>실패</b>다 — 생성 실패가 검토 요청을 막지 않는다는 규칙을 모든 검토 요청이 함께 밟는다.
     * 성공 경로가 필요한 테스트는 {@code doReturn(...).when(renderer)}로 바꾼다({@code when(...)}은 이 스텁을 호출해 던진다).
     * 계약 테스트마다 따로 목을 선언하면 테스트 컨텍스트가 갈라져 캐시가 늘어난다 — 그래서 여기 한 곳에 둔다.
     */
    @MockitoBean
    protected ContractPdfRenderer renderer;

    protected BrandFixture fixture;
    private DatabaseCleaner databaseCleaner;
    protected ChangeRequestSteps changeRequests;

    @BeforeEach
    void setUpSupport() {
        doThrow(new BusinessException(ErrorCode.CONTRACT_PDF_GENERATION_FAILED))
                .when(renderer).render(anyString(), anyString());
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
