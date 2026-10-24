package showroomz.api.seller.home.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.common.sanction.SanctionHoldView;
import showroomz.api.seller.account.service.SellerAccountStatusService;
import showroomz.api.seller.common.SellerMarketResolver;
import showroomz.api.seller.home.dto.SellerHomeDto;
import showroomz.api.seller.settlement.dto.SellerSettlementDto;
import showroomz.api.seller.settlement.service.SellerSettlementQueryService;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.repository.GroupBuyPostRepository;
import showroomz.domain.groupbuy.repository.GroupBuyRepository;
import showroomz.domain.groupbuy.type.GroupBuyPostStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.market.entity.Market;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 파트너센터 홈 1회 조립(1017 설계서 4-1) — 진입 시 한 번 부른다. 실시간 카드 갱신은 {@code /home/live}, 달력 월 이동은
 * {@code /home/calendar} 가 따로 맡는다. 정지 상태(H4)는 {@code account} · 각 행의 {@code hold} 로만 보인다(홈이 따로 판정하지 않는다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerHomeAssembler {

    static final int LIST_LIMIT = 5;
    /** 계약 진행 — 체결 전 진행 4상태. 작성중 · 체결완료 · 종결은 뺀다. */
    static final Set<ContractStatus> CONTRACT_IN_PROGRESS = EnumSet.of(ContractStatus.REVIEW_PENDING,
            ContractStatus.REVIEW_REJECTED, ContractStatus.SIGNING, ContractStatus.CONCLUSION_PENDING);
    static final Set<GroupBuyStatus> UPCOMING = EnumSet.of(GroupBuyStatus.PREPARING, GroupBuyStatus.READY);

    private final SellerMarketResolver marketResolver;
    private final SellerAccountStatusService accountStatusService;
    private final SellerHomeGuideService guideService;
    private final SellerPendingTaskReader pendingTaskReader;
    private final SellerHomeLiveService liveService;
    private final SellerSettlementQueryService settlementQueryService;
    private final ContractRepository contractRepository;
    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyPostRepository groupBuyPostRepository;

    public SellerHomeDto.Home getHome(String sellerEmail) {
        Market market = marketResolver.resolve(sellerEmail);
        Long marketId = market.getId();
        SellerHomeDto.PendingTasks pendingTasks = pendingTaskReader.read(marketId);
        return new SellerHomeDto.Home(
                accountStatusService.of(market),
                guideService.read(market),
                pendingTasks,
                liveService.read(marketId),
                contractsInProgress(marketId),
                upcomingGroupBuys(marketId),
                settlement(market));
    }

    private SellerHomeDto.ContractsInProgress contractsInProgress(Long marketId) {
        long count = 0;
        for (Object[] row : contractRepository.countByStatus(marketId)) {
            if (CONTRACT_IN_PROGRESS.contains((ContractStatus) row[0])) {
                count += (Long) row[1];
            }
        }
        if (count == 0) {
            return new SellerHomeDto.ContractsInProgress(0, List.of());
        }
        List<SellerHomeDto.ContractRow> items = contractRepository
                .findInProgressForMarket(marketId, CONTRACT_IN_PROGRESS, PageRequest.of(0, LIST_LIMIT)).stream()
                .map(SellerHomeAssembler::contractRow)
                .toList();
        return new SellerHomeDto.ContractsInProgress(count, items);
    }

    private static SellerHomeDto.ContractRow contractRow(Contract contract) {
        return new SellerHomeDto.ContractRow(contract.getId(), contract.getTitle(), contract.getCreator().getId(),
                contract.getCreator().getShowroomName(), contract.getStatus(), contract.getStatus().getLabel(),
                contract.getGroupBuyStartAt(), SanctionHoldView.forBrand(contract));
    }

    private SellerHomeDto.UpcomingGroupBuys upcomingGroupBuys(Long marketId) {
        List<GroupBuy> all = groupBuyRepository.findByMarketAndStatuses(marketId, UPCOMING);
        if (all.isEmpty()) {
            return new SellerHomeDto.UpcomingGroupBuys(0, List.of());
        }
        List<GroupBuy> shown = all.subList(0, Math.min(LIST_LIMIT, all.size()));
        Map<Long, GroupBuyPost> posts = groupBuyPostRepository
                .findByGroupBuyIds(shown.stream().map(GroupBuy::getId).toList()).stream()
                .collect(Collectors.toMap(p -> p.getGroupBuy().getId(), Function.identity(), (a, b) -> a));
        List<SellerHomeDto.UpcomingRow> items = shown.stream()
                .map(g -> upcomingRow(g, GroupBuyPostStatus.of(posts.get(g.getId()), g)))
                .toList();
        return new SellerHomeDto.UpcomingGroupBuys(all.size(), items);
    }

    private static SellerHomeDto.UpcomingRow upcomingRow(GroupBuy groupBuy, GroupBuyPostStatus postStatus) {
        return new SellerHomeDto.UpcomingRow(groupBuy.getId(), groupBuy.getContract().getTitle(),
                groupBuy.getCreator().getId(), groupBuy.getCreator().getShowroomName(), groupBuy.getStartAt(),
                groupBuy.getStatus(), groupBuy.getStatus().getLabel(), postStatus, postStatus.getLabel(),
                blockedReason(groupBuy, postStatus), SanctionHoldView.forBrand(groupBuy));
    }

    /** 오픈을 막는 것 — 게시물 반려(인플루언서 몫) · 최소 준비 물량 미확인(내 몫) · 운영팀 검토 중. */
    static String blockedReason(GroupBuy groupBuy, GroupBuyPostStatus postStatus) {
        if (postStatus == GroupBuyPostStatus.REJECTED) {
            return "게시물 반려 — 인플루언서 수정 대기";
        }
        if (groupBuy.getStatus() == GroupBuyStatus.PREPARING && groupBuy.getStockConfirmedAt() == null) {
            return "최소 준비 물량 확인 필요";
        }
        if (postStatus == GroupBuyPostStatus.PENDING_APPROVAL) {
            return "운영팀 검토 중";
        }
        return null;
    }

    private SellerHomeDto.Settlement settlement(Market market) {
        SellerSettlementDto.Summary summary = settlementQueryService.summarize(market);
        return new SellerHomeDto.Settlement(summary.scheduled().amount(), summary.nextPayoutDate(),
                summary.reviewingCount(), summary.adjustmentMyTurnCount());
    }
}
