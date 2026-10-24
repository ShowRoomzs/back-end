package showroomz.api.app.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.app.product.DTO.ProductDetailViewRequest;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.service.GroupBuyPriceResolver;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.post.service.PostAttributionService;
import showroomz.domain.product.entity.ProductDetailView;
import showroomz.domain.product.repository.ProductDetailViewRepository;
import showroomz.domain.product.type.ProductDetailViewSource;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * 상품 상세 조회 적재(49 스튜디오 성과 설계서 1-4) — 성과의 상품별 「상세 조회」와 게시물 「상품 상세 이동」의 원천.
 *
 * <p>규칙은 쇼룸 방문 · 게시물 노출과 같다 — 30분 세션 안의 재조회는 적재하지 않고({@link ProductDetailView#SESSION_MINUTES}),
 * 사람 키는 {@link PostAttributionService#viewerKeyOf}다. 공구는 상세 화면과 <b>같은 판정</b>
 * ({@link GroupBuyPriceResolver#resolveProductOffer})으로 정한다 — 판매 중 공구가 없거나 둘 이상이면 공구 밖 조회(성과 집계 밖)다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProductDetailViewService {

    private final ProductDetailViewRepository viewRepository;
    private final UserRepository userRepository;
    private final GroupBuyPriceResolver priceResolver;
    private final PostAttributionService postAttributionService;

    /**
     * @param username 로그인 조회면 로그인 아이디, 비로그인이면 null
     */
    public void recordView(String username, Long productId, ProductDetailViewRequest request) {
        Users viewer = username == null ? null : userRepository.findByUsername(username).orElse(null);
        Long userId = viewer == null ? null : viewer.getId();
        String visitorId = request == null ? null : request.getVisitorId();
        String viewerKey = PostAttributionService.viewerKeyOf(userId, visitorId);
        if (viewerKey == null) {
            // 식별자가 없으면 재조회를 접을 수 없어 조회 수가 부풀고 귀속도 성립하지 않는다.
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        LocalDateTime now = LocalDateTime.now();
        if (viewRepository.existsByProductIdAndViewerKeyAndViewedAtAfter(productId, viewerKey,
                now.minusMinutes(ProductDetailView.SESSION_MINUTES))) {
            return;
        }
        Long requestedGroupBuyId = request == null ? null : request.getGroupBuyId();
        // 없는 상품 · 공구 밖 조회도 기록은 남긴다(공구 null) — 적재 실패로 상세 화면이 멈출 이유는 없다.
        GroupBuy groupBuy = priceResolver.resolveProductOffer(requestedGroupBuyId, productId, now)
                .map(GroupBuyPriceResolver.ProductOffer::groupBuy)
                .orElse(null);
        Long creatorId = groupBuy == null ? null : groupBuy.getCreator().getId();
        Long attributedPostId = creatorId == null ? null
                : postAttributionService.resolveAttributedPostId(viewerKey, creatorId, now);
        viewRepository.save(new ProductDetailView(productId, groupBuy == null ? null : groupBuy.getId(), creatorId,
                userId, viewerKey, ProductDetailViewSource.from(request == null ? null : request.getSource()),
                attributedPostId, now));
    }
}
