package showroomz.api.seller.performance.service;

import com.querydsl.core.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.groupbuy.service.GroupBuyAccessGuard;
import showroomz.api.seller.performance.dto.SellerPerformanceDto;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.performance.repository.GroupBuyCostRepository;
import showroomz.domain.performance.repository.PerformanceGroupBuyQueryRepository;
import showroomz.domain.performance.service.GroupBuyPerformanceReader;
import showroomz.domain.performance.service.PerformanceFacts;
import showroomz.domain.performance.type.PerformanceSort;
import showroomz.domain.performance.type.PerformanceStatus;
import showroomz.domain.settlement.entity.Settlement;
import showroomz.domain.settlement.repository.SettlementRepository;
import showroomz.global.dto.PagingRequest;
import showroomz.global.dto.PaginationInfo;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 파트너센터 성과 관리(12) 조회(48 파트너 설계서 3절) — 목록 · KPI 요약 · 상세. 계산은 {@link GroupBuyPerformanceReader}(스튜디오 공용)가
 * 하고, 여기서는 모집단 · 소유 검사 · 페이지 · 원가를 붙인다.
 *
 * <p>내 브랜드 공구가 아니거나 모집단 밖(진행 중 · 중단 · 판매 0건)이면 <b>404</b> {@code GROUP_BUY_NOT_FOUND} — 존재를 알리지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerPerformanceQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final GroupBuyAccessGuard accessGuard;
    private final GroupBuyRepository groupBuyRepository;
    private final PerformanceGroupBuyQueryRepository queryRepository;
    private final SettlementRepository settlementRepository;
    private final GroupBuyCostRepository costRepository;
    private final GroupBuyPerformanceReader reader;
    private final SellerPerformanceAssembler assembler;

    // ------------------------------------------------------------------ 목록(3-2)

    public SellerPerformanceDto.ListPage getList(String sellerEmail, Set<PerformanceStatus> statuses, String keyword,
                                                 PerformanceSort sort, PagingRequest paging) {
        Market market = accessGuard.resolve(sellerEmail).market();
        Page<GroupBuy> page = queryRepository.searchForMarket(market.getId(), statuses, keyword,
                sort == null ? PerformanceSort.ENDED_DESC : sort, pageable(paging));
        Map<Long, PerformanceFacts> rows = reader.readRows(page.getContent());
        List<SellerPerformanceDto.ListItem> items = page.getContent().stream()
                .map(groupBuy -> assembler.listItem(groupBuy, rows.get(groupBuy.getId()))).toList();
        return new SellerPerformanceDto.ListPage(items, new PaginationInfo(page),
                period(queryRepository.periodForMarket(market.getId(), statuses, keyword)));
    }

    private static SellerPerformanceDto.ListPeriod period(Tuple row) {
        if (row == null) {
            return null;
        }
        LocalDateTime from = row.get(0, LocalDateTime.class);
        LocalDateTime to = row.get(1, LocalDateTime.class);
        return from == null || to == null ? null
                : new SellerPerformanceDto.ListPeriod(from.toLocalDate(), to.toLocalDate());
    }

    // ------------------------------------------------------------------ 요약(3-3)

    /** 모집단 전체 기준 — 검색어 · 상태 칩과 무관. KPI 는 지급 완료 공구만 합산한다(§47-1). */
    public SellerPerformanceDto.Summary getSummary(String sellerEmail) {
        Market market = accessGuard.resolve(sellerEmail).market();
        List<GroupBuy> listed = queryRepository.findAllListedForMarket(market.getId());
        Map<Long, Settlement> settlements = listed.isEmpty() ? Map.of()
                : settlementRepository.findByGroupBuyIdIn(listed.stream().map(GroupBuy::getId).toList()).stream()
                .collect(Collectors.toMap(Settlement::getGroupBuyId, Function.identity()));
        Map<PerformanceStatus, Long> counts = SellerPerformanceAssembler.emptyCounts();
        for (GroupBuy groupBuy : listed) {
            Settlement s = settlements.get(groupBuy.getId());
            counts.merge(PerformanceStatus.of(s == null ? null : s.getStatus()), 1L, Long::sum);
        }
        List<GroupBuy> paid = listed.stream().filter(groupBuy -> {
            Settlement s = settlements.get(groupBuy.getId());
            return s != null && PerformanceStatus.of(s.getStatus()) == PerformanceStatus.PAID;
        }).toList();
        return assembler.summary(reader.readRows(paid).values(), counts);
    }

    // ------------------------------------------------------------------ 상세(3-4)

    public SellerPerformanceDto.Detail getDetail(String sellerEmail, Long groupBuyId) {
        Market market = accessGuard.resolve(sellerEmail).market();
        GroupBuy groupBuy = loadListed(groupBuyId, market);
        return assembler.detail(groupBuy, reader.read(groupBuy),
                costRepository.findWithItemsByGroupBuyId(groupBuyId).orElse(null));
    }

    /** 내 브랜드 · 모집단 안의 공구 — 아니면 404(남의 공구 · 진행 중 · 중단 · 판매 0건의 존재를 알리지 않는다). */
    GroupBuy loadListed(Long groupBuyId, Market market) {
        GroupBuy groupBuy = groupBuyRepository.findDetailById(groupBuyId)
                .filter(g -> g.isOwnedBy(market.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND));
        if (!reader.isListed(groupBuy)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND);
        }
        return groupBuy;
    }

    private static Pageable pageable(PagingRequest paging) {
        PagingRequest request = paging == null ? new PagingRequest() : paging;
        if (request.getSize() < 1 || request.getSize() > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "size 는 1 ~ 100 이어야 합니다.");
        }
        return PageRequest.of(Math.max(0, request.getPage() - 1), request.getSize());
    }
}
