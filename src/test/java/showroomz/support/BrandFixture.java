package showroomz.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.bank.entity.Bank;
import showroomz.domain.bank.repository.BankRepository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.market.type.MarketStatus;
import showroomz.domain.member.seller.entity.Seller;

import java.time.LocalDateTime;

/**
 * 브랜드 기본정보 시나리오의 출발 상태를 만든다. 가입 플로우를 다시 타지 않고 직접 적재하는 이유는,
 * 이 테스트들의 관심사가 "이미 입점한 브랜드의 정보 조회·수정·변경 요청"이기 때문이다.
 *
 * <p>모든 필드를 실데이터에 가깝게 채운다 — 사업자 정보 탭 응답(A-1)과 어드민 대조표(A-8)가
 * 비어 있는 값을 그냥 통과시켜 버리면 테스트가 아무것도 검증하지 못한다.
 */
public class BrandFixture {

    public static final String RAW_PASSWORD = "Passw0rd!";

    private final SellerRepository sellerRepository;
    private final MarketRepository marketRepository;
    private final BankRepository bankRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    BrandFixture(SellerRepository sellerRepository, MarketRepository marketRepository,
                 BankRepository bankRepository, PasswordEncoder passwordEncoder, JdbcTemplate jdbcTemplate) {
        this.sellerRepository = sellerRepository;
        this.marketRepository = marketRepository;
        this.bankRepository = bankRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 입점 승인이 끝난 브랜드 1곳(SELLER + MARKET). */
    public Brand createBrand(String email, String marketName) {
        LocalDateTime now = LocalDateTime.now();

        Seller seller = new Seller(email, passwordEncoder.encode(RAW_PASSWORD), "김담당", "010-1111-2222", now);
        seller.setStatus(SellerStatus.APPROVED);
        seller.setRoleType(RoleType.SELLER);
        seller.setBusinessType("일반과세자");
        seller.setRepresentativeName("김대표");
        seller.setRepresentativeContact("010-1111-2222");
        seller.setCompanyName("주식회사 " + marketName);
        seller.setBusinessRegistrationNumber("123-45-67890");
        seller.setBusinessCondition("도소매업");
        seller.setBusinessAddress("서울특별시 강남구 테헤란로 123");
        seller.setDetailAddress("4층 401호");
        seller.setTaxEmail("tax@" + marketName + ".com");
        seller.setMailOrderRegNumber("2026-강남-01234");
        seller.setBusinessLicenseImageUrl("https://cdn.showroomz.test/docs/license.jpg");
        seller.setMailOrderRegImageUrl("https://cdn.showroomz.test/docs/mail-order.png");
        seller.setBankbookImageUrl("https://cdn.showroomz.test/docs/bankbook.pdf");
        seller.setBankName("신한은행");
        seller.setAccountHolder("주식회사 " + marketName);
        seller.setAccountNumber("110123456789");
        sellerRepository.save(seller);

        Market market = new Market(seller, marketName, "02-1234-5678");
        market.setStatus(MarketStatus.ACTIVE);
        market.setBrandSiteUrl("https://" + marketName + ".com");
        market.setShippingRecipientName("김담당");
        market.setShippingContact("010-1111-2222");
        market.setShippingAddress("서울특별시 강남구 테헤란로 123");
        market.setShippingDetailAddress("1층 물류센터");
        marketRepository.save(market);

        return new Brand(seller, market);
    }

    /** 변경 요청을 심사하는 운영자. 어드민도 SELLER 테이블에 ROLE_ADMIN으로 적재된다. */
    public Seller createAdmin(String email, String name) {
        LocalDateTime now = LocalDateTime.now();
        Seller admin = new Seller(email, passwordEncoder.encode(RAW_PASSWORD), name, "010-0000-0000", now);
        admin.setRoleType(RoleType.ADMIN);
        admin.setStatus(SellerStatus.APPROVED);
        return sellerRepository.save(admin);
    }

    public Bank createBank(String code, String name) {
        return bankRepository.save(Bank.builder().code(code).name(name).displayOrder(1).build());
    }

    /**
     * 경과 시간·SLA 배지·목록 정렬은 {@code requested_at} 기준이고, 그 값은 엔티티 생성 시각으로 고정된다.
     * 시간을 되감을 방법이 애플리케이션에 없으므로 테스트에서만 SQL로 소급한다.
     */
    public void backdateRequestedAt(Long requestId, LocalDateTime requestedAt) {
        jdbcTemplate.update("UPDATE brand_change_request SET requested_at = ? WHERE request_id = ?",
                requestedAt, requestId);
    }

    /** 이메일 변경 월 1회 제한(§15-5)의 경계를 만들기 위한 소급. */
    public void backdateEmailChangedAt(Long sellerId, LocalDateTime emailChangedAt) {
        jdbcTemplate.update("UPDATE seller SET email_changed_at = ? WHERE seller_id = ?", emailChangedAt, sellerId);
    }

    public record Brand(Seller seller, Market market) {

        public String email() {
            return seller.getEmail();
        }

        public Long marketId() {
            return market.getId();
        }
    }
}
