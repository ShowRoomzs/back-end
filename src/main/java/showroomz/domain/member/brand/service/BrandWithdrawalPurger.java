package showroomz.domain.member.brand.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.auth.refreshToken.SellerRefreshToken;
import showroomz.api.seller.auth.refreshToken.SellerRefreshTokenRepository;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.domain.message.type.ThreadKind;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.global.utils.BusinessRegistrationNumberHasher;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 브랜드 탈퇴 승인의 파기 · 차단(§51-5 · 52 설계서 4-3 ⑥⑦). 보존 · 파기 범위는 법률 쟁점 G 종속이라 <b>목록을 이 클래스 한 곳에 모은다</b> —
 * 자문 결과가 바뀌면 여기만 고친다.
 *
 * <ul>
 *   <li>파기 — 담당자명 · 연락처 · 로그인 이메일 · 사이트 · 사업자 정보 · 정산 정보 · 세금계산서 이메일 · 서류 이미지</li>
 *   <li>보존 — 브랜드명 · 회원번호 · 가입일 · 입점 승인일 · 배송 · 반품 정책 전부(남은 반품의 비용 기준) · CS 번호 · 주문 · 정산 · 계약 · 이력 · 메모</li>
 *   <li>차단 — 로그인(이메일 자리표시자 · 비밀번호 폐기 · 리프레시 토큰 삭제) · 상품 전건 미진열 · 작성중 계약 삭제 · 남은 연결 전건 종료</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class BrandWithdrawalPurger {

    private final SellerRefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProductRepository productRepository;
    private final ContractRepository contractRepository;
    private final ConnectionRepository connectionRepository;
    private final MessageThreadRepository threadRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void purge(Seller seller, Market market, LocalDateTime now) {
        // 이메일이 리프레시 토큰의 키다 — 바꾸기 전에 지운다.
        SellerRefreshToken token = refreshTokenRepository.findByAdminEmail(seller.getEmail());
        if (token != null) {
            refreshTokenRepository.delete(token);
        }
        seller.purgeOnWithdrawal(BusinessRegistrationNumberHasher.hash(seller.getBusinessRegistrationNumber()),
                placeholderEmail(seller.getId()), passwordEncoder.encode(UUID.randomUUID().toString()), now);
        market.setBrandSiteUrl(null);

        for (Connection pair : connectionRepository.findConnectedPairsByMarketId(market.getId())) {
            pair.markDisconnected();
            threadRepository.findByConnectionAndKind(pair, ThreadKind.CONNECTION).ifPresent(thread -> thread.dormant());
        }
        productRepository.hideAllByMarket(market.getId());
        contractRepository.softDeleteDraftsOfMarket(market.getId(), now);
    }

    /** 로그인 불가 · 충돌 없는 자리표시자 — {@code .invalid}는 예약 TLD 라 메일이 나가지 않는다. */
    public static String placeholderEmail(Long sellerId) {
        return "withdrawn-" + sellerId + "@withdrawn.showroomz.invalid";
    }
}
