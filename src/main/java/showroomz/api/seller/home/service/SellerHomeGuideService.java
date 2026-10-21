package showroomz.api.seller.home.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.home.dto.SellerHomeDto;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 첫 공구 가이드(1017 설계서 4-1 guide · 4-4) — 「시스템이 자동 판정 · 체크를 사람이 누르지 않는다」. 단계 판정은 매번 계산하고
 * 숨김 시각만 저장한다({@code market.home_guide_hidden_at}). 첫 공구가 오픈되면 숨김과 무관하게 사라진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerHomeGuideService {

    static final String BRAND_INFO = "BRAND_INFO";
    static final String PRODUCT = "PRODUCT";
    static final String CONNECTION = "CONNECTION";
    static final String CONTRACT = "CONTRACT";
    static final String OPEN = "OPEN";
    static final String ORDERS = "ORDERS";

    private final ProductRepository productRepository;
    private final ConnectionRepository connectionRepository;
    private final ContractRepository contractRepository;
    private final GroupBuyRepository groupBuyRepository;
    private final MarketRepository marketRepository;

    public SellerHomeDto.Guide read(Market market) {
        Long marketId = market.getId();
        long connected = connectionRepository.countConnectedPairsByMarket(marketId);
        boolean opened = groupBuyRepository.existsByMarket_IdAndOpenedAtIsNotNull(marketId);

        Map<String, Boolean> done = new LinkedHashMap<>();
        done.put(BRAND_INFO, true); // 홈에 들어온 셀러는 입점 승인을 마쳤다
        done.put(PRODUCT, productRepository.countSellingByMarket(marketId)
                + productRepository.countSoldOutByMarket(marketId) >= 1);
        done.put(CONNECTION, connected >= 1);
        done.put(CONTRACT, hasConcluded(marketId));
        done.put(OPEN, opened);
        done.put(ORDERS, opened); // 오픈 뒤에는 주문이 저절로 들어온다

        List<SellerHomeDto.GuideStep> steps = new ArrayList<>();
        String currentStep = null;
        int completed = 0;
        for (Map.Entry<String, Boolean> step : done.entrySet()) {
            steps.add(new SellerHomeDto.GuideStep(step.getKey(), step.getValue()));
            if (step.getValue()) {
                completed++;
            } else if (currentStep == null) {
                currentStep = step.getKey();
            }
        }
        String connectedName = connected == 0 ? null
                : connectionRepository.findLatestConnectedPairsByMarket(marketId, PageRequest.of(0, 1)).stream()
                        .findFirst().map(Connection::getCreator).map(c -> c.getShowroomName()).orElse(null);
        boolean hidden = market.getHomeGuideHiddenAt() != null;
        return new SellerHomeDto.Guide(!hidden && !opened, hidden, completed, currentStep, steps, connected,
                connectedName, market.getShippingLeadDays());
    }

    /** [가이드 숨기기] — 다시 켜는 UI 는 없지만 {@code hidden=false} 도 받는다. */
    @Transactional
    public SellerHomeDto.Guide updateHidden(Long marketId, boolean hidden) {
        Market market = marketRepository.findById(marketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));
        market.updateHomeGuideHidden(hidden, LocalDateTime.now());
        return read(market);
    }

    private boolean hasConcluded(Long marketId) {
        for (Object[] row : contractRepository.countByStatus(marketId)) {
            if (row[0] == ContractStatus.CONCLUDED && (Long) row[1] > 0) {
                return true;
            }
        }
        return false;
    }
}
