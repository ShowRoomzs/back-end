package showroomz.api.seller.home.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.claim.dto.SellerClaimSummaryResponse;
import showroomz.api.seller.claim.service.SellerClaimQueryService;
import showroomz.api.seller.contract.service.SellerContractQueryService;
import showroomz.api.seller.groupbuy.service.SellerGroupBuyQueryService;
import showroomz.api.seller.home.dto.SellerHomeDto;
import showroomz.api.seller.inquiry.service.SellerInquiryService;
import showroomz.api.seller.order.dto.SellerOrderSummaryResponse;
import showroomz.api.seller.order.service.SellerOrderQueryService;
import showroomz.api.seller.settlement.service.SellerSettlementQueryService;
import showroomz.api.seller.thread.service.SellerThreadService;
import showroomz.domain.market.entity.Market;
import showroomz.domain.order.type.OrderTab;

/**
 * 처리 대기 · GNB 배지(1017 설계서 4-1 · 4-7) — 홈과 배지가 <b>같은 계산</b>을 쓴다. 숫자는 전부 메뉴별 요약이 쓰는 서비스 메서드에서
 * 가져온다(홈이 집계를 다시 만들지 않는다 — 홈 숫자와 메뉴 탭 카운트가 어긋나면 안 된다).
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerPendingTaskReader {

    private final SellerOrderQueryService orderQueryService;
    private final SellerClaimQueryService claimQueryService;
    private final SellerInquiryService inquiryService;
    private final SellerContractQueryService contractQueryService;
    private final SellerSettlementQueryService settlementQueryService;
    private final SellerGroupBuyQueryService groupBuyQueryService;
    private final SellerThreadService threadService;

    public SellerHomeDto.PendingTasks read(Long marketId) {
        SellerOrderSummaryResponse orders = orderQueryService.summarize(marketId);
        SellerClaimSummaryResponse.Kpi claims = claimQueryService.summarize(marketId).kpi();
        SellerHomeDto.Sales sales = new SellerHomeDto.Sales(
                orders.actionBar().prepareStart(),
                orders.actionBar().invoiceRegister(),
                orders.actionBar().shipOverdue(),
                orders.tabCounts().getOrDefault(OrderTab.CANCEL_REQUESTED.name(), 0L),
                orders.actionBar().deliveryIssue());
        SellerHomeDto.Claims claimTasks = new SellerHomeDto.Claims(claims.collectWait(), claims.inspection(),
                claimQueryService.countInspectOverdue(marketId), claims.reship());
        SellerHomeDto.Inquiry inquiry = new SellerHomeDto.Inquiry(inquiryService.countWaiting(marketId));
        SellerHomeDto.ContractTasks contract = new SellerHomeDto.ContractTasks(
                contractQueryService.countMySignatureNeeded(marketId), contractQueryService.countReviewRejected(marketId));
        SellerHomeDto.SettlementTasks settlement = new SellerHomeDto.SettlementTasks(
                settlementQueryService.countAdjustmentMyTurn(marketId));
        long total = salesBadge(sales, claimTasks) + inquiry.unansweredProductInquiries() + contractBadge(contract)
                + settlement.adjustmentMyTurn();
        return new SellerHomeDto.PendingTasks(sales, claimTasks, inquiry, contract, settlement, total);
    }

    public SellerHomeDto.Badges badges(Market market) {
        SellerHomeDto.PendingTasks tasks = read(market.getId());
        return new SellerHomeDto.Badges(
                threadService.countUnread(market),
                contractBadge(tasks.contract()),
                groupBuyQueryService.countActionRequired(market.getId()),
                salesBadge(tasks.sales(), tasks.claims()),
                tasks.settlement().adjustmentMyTurn(),
                tasks.inquiry().unansweredProductInquiries());
    }

    /** 판매 배지 — 반품 · 교환은 판매 관리 메뉴 안이다. 발송 기한 경과 · 검수 지연은 이미 각 칸에 든 건이라 더하지 않는다. */
    private static long salesBadge(SellerHomeDto.Sales sales, SellerHomeDto.Claims claims) {
        return sales.prepareStart() + sales.invoiceRegister() + sales.cancelRequests() + sales.deliveryIssue()
                + claims.collectWait() + claims.inspection() + claims.reship();
    }

    private static long contractBadge(SellerHomeDto.ContractTasks contract) {
        return contract.mySignatureNeeded() + contract.reviewRejected();
    }
}
