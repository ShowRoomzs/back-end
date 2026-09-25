package showroomz.api.creator.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.creator.groupbuy.dto.CreatorGroupBuyDetailResponse;
import showroomz.api.creator.groupbuy.dto.CreatorGroupBuyDetailResponse.*;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractItem;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyAdminSuspension;
import showroomz.domain.groupbuy.entity.GroupBuyChangeRequest;
import showroomz.domain.groupbuy.entity.GroupBuyExtensionRequest;
import showroomz.domain.groupbuy.entity.GroupBuyFulfillmentCheck;
import showroomz.domain.groupbuy.entity.GroupBuyHistory;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.repository.GroupBuyHistoryRepository;
import showroomz.domain.groupbuy.service.GroupBuyCommandService;
import showroomz.domain.groupbuy.service.GroupBuyDisclosure;
import showroomz.domain.groupbuy.service.GroupBuyFacts;
import showroomz.domain.groupbuy.service.GroupBuyFactsLoader;
import showroomz.domain.groupbuy.service.GroupBuyFixedFeeText;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader.GroupBuySales;
import showroomz.domain.groupbuy.service.port.GroupBuySettlementReader;
import showroomz.domain.groupbuy.service.port.GroupBuyThreadGateway;
import showroomz.domain.groupbuy.type.AdminSuspensionKind;
import showroomz.domain.groupbuy.type.ExtensionRejectReason;
import showroomz.domain.groupbuy.type.FulfillmentSide;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyPostStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.groupbuy.type.GroupBuyTone;
import showroomz.global.config.properties.GroupBuyProperties;
import showroomz.global.utils.BusinessCalendar;
import showroomz.global.utils.RewardCalculator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 스튜디오 공구 상세 조립(31 설계 4절). 실행 API도 처리 후 이 조립기로 상세를 돌려준다.
 *
 * <p>분기 값({@code viewPhase})을 만들지 않는다 — FE가 {@code status} · {@code post.status} · {@code extension} ·
 * {@code activeRequest}로 고른다. 대신 <b>무엇을 내리지 않을지</b>가 이 클래스의 본체다(31 설계 0-5 · 6-1).
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorGroupBuyDetailAssembler {

    private final GroupBuyFactsLoader factsLoader;
    private final GroupBuyHistoryRepository historyRepository;
    private final GroupBuySalesReader salesReader;
    private final GroupBuySettlementReader settlementReader;
    private final GroupBuyThreadGateway threadGateway;
    private final CreatorGroupBuyPermissionPolicy permissionPolicy;
    private final BusinessCalendar businessCalendar;
    private final GroupBuyProperties properties;

    public CreatorGroupBuyDetailResponse assemble(GroupBuy groupBuy, Navigation navigation) {
        LocalDateTime now = LocalDateTime.now();
        GroupBuyFacts facts = factsLoader.load(groupBuy);
        Contract contract = groupBuy.getContract();
        Long pairThreadId = threadGateway.findPairThreadId(groupBuy).orElse(null);
        GroupBuySales sales = salesReader.readSales(groupBuy.getId()).orElse(null);
        Long myReward = sales == null ? null : myReward(contract, sales);

        return new CreatorGroupBuyDetailResponse(
                summary(groupBuy, contract),
                timeline(groupBuy, contract, now),
                new Brand(groupBuy.getMarket().getId(), groupBuy.getMarket().getMarketName(), pairThreadId),
                new ContractRef(contract.getId(), contract.getContractNumber(), contract.getConcludedAt(),
                        contract.getContentDueDate()),
                items(contract),
                fixedFee(contract),
                payout(groupBuy, contract, myReward, pairThreadId),
                readiness(groupBuy, facts.post(), now),
                post(groupBuy, facts.post(), now),
                sales(groupBuy, facts, sales, myReward),
                orderClosure(groupBuy),
                extension(groupBuy, facts.extension()),
                facts.pendingChangeRequest().map(this::activeRequest).orElse(null),
                adminSuspension(groupBuy, facts, now),
                closure(groupBuy, facts),
                settlement(groupBuy, contract),
                afterEnd(groupBuy, contract, facts),
                permissionPolicy.evaluate(facts, pairThreadId != null, now),
                history(groupBuy, facts),
                navigation == null ? new Navigation(null, null) : navigation);
    }

    private Summary summary(GroupBuy groupBuy, Contract contract) {
        GroupBuyStatus status = groupBuy.getStatus();
        return new Summary(
                groupBuy.getId(),
                groupBuy.getGroupBuyNumber(),
                contract.getTitle(),
                status,
                status.getLabel(),
                status.getTone(),
                groupBuy.getCreatedAt(),
                groupBuy.getReadyAt(),
                groupBuy.getOpenedAt(),
                groupBuy.getEndedAt(),
                groupBuy.getCloseType(),
                groupBuy.getCloseType() == null ? null : groupBuy.getCloseType().getLabel(),
                groupBuy.getSettledAt());
    }

    /** 파트너와 같은 계산 — 양끝 포함 일자 · 서버 now 기준(30 설계 4-4). */
    private Timeline timeline(GroupBuy groupBuy, Contract contract, LocalDateTime now) {
        LocalDateTime startAt = groupBuy.getStartAt();
        LocalDateTime endAt = groupBuy.getEndAt();
        LocalDateTime cap = groupBuy.getEndedAt() != null ? groupBuy.getEndedAt() : endAt;
        int totalDays = groupBuy.totalDays();

        int elapsedDays = now.isBefore(startAt) ? 0
                : Math.min(GroupBuy.daysInclusive(startAt, now.isBefore(cap) ? now : cap), totalDays);
        Integer daysUntilStart = now.isBefore(startAt)
                ? (int) ChronoUnit.DAYS.between(now.toLocalDate(), startAt.toLocalDate()) : null;
        Integer daysUntilEnd = groupBuy.getStatus().isSelling() && now.isBefore(endAt)
                ? (int) ChronoUnit.DAYS.between(now.toLocalDate(), endAt.toLocalDate()) : null;
        boolean startOverdue = groupBuy.getStatus() == GroupBuyStatus.PREPARING && !now.isBefore(startAt);

        return new Timeline(startAt, endAt, contract.getGroupBuyEndAt(), totalDays, elapsedDays,
                daysUntilStart, daysUntilEnd, startOverdue);
    }

    /** 정가·최소 준비 물량은 싣지 않는다 — 시안에 없고 물량은 브랜드 소관이다(B1). */
    private List<Item> items(Contract contract) {
        return contract.getItems().stream()
                .map(item -> new Item(
                        item.getProductId(),
                        item.getProductName(),
                        item.getGroupBuyPrice(),
                        item.getRewardRate(),
                        RewardCalculator.calcUnitReward(item.getGroupBuyPrice(), item.getRewardRate())))
                .toList();
    }

    private FixedFee fixedFee(Contract contract) {
        return new FixedFee(
                contract.getFixedFeeAmount(),
                contract.getFixedFeeTrigger(),
                contract.getFixedFeeTrigger() == null ? null : contract.getFixedFeeTrigger().getLabel(),
                GroupBuyFixedFeeText.of(contract));
    }

    /**
     * 「내가 받는 금액」 — 종결 3종은 null이다. 시안 B7~B12에 이 카드가 없고, 종결 후 금액은 KPI와 정산 블록이 말한다.
     * 공제 계산은 하지 않는다(정산 관리 소관). 미보증 고지 문장은 FE 상수이고 서버는 분기 값만 준다.
     */
    private Payout payout(GroupBuy groupBuy, Contract contract, Long myReward, Long pairThreadId) {
        if (groupBuy.getStatus().isTerminal()) {
            return null;
        }
        SalesReward salesReward = groupBuy.getStatus().isSelling() && myReward != null
                ? new SalesReward(myReward, SalesBasis.LIVE) : null;
        return new Payout(contract.getFixedFeeAmount(), salesReward, false, new DisputeChannel(pairThreadId));
    }

    /**
     * 게이트 3개를 <b>내 관점으로</b> 판정한다(31 설계 4-3) — 「내 차례인 줄만 경고 톤」. 반려되면 ②가 다시 내 차례다 —
     * 제출 여부({@code submitted_at})로 판정하면 반려 뒤에도 ✓가 남는다. ①의 수량은 내리지 않는다.
     */
    private Readiness readiness(GroupBuy groupBuy, GroupBuyPost post, LocalDateTime now) {
        GroupBuyStatus status = groupBuy.getStatus();
        if (status != GroupBuyStatus.PREPARING && status != GroupBuyStatus.READY) {
            return null;
        }
        GroupBuyPostReviewStatus review = post == null ? null : post.getReviewStatus();
        boolean myPostDone = review == GroupBuyPostReviewStatus.PENDING || review == GroupBuyPostReviewStatus.APPROVED;
        GateState reviewState = review == GroupBuyPostReviewStatus.APPROVED ? GateState.DONE
                : review == GroupBuyPostReviewStatus.PENDING ? GateState.IN_REVIEW
                : GateState.WAITING;

        List<Gate> gates = List.of(
                gate(GateKey.STOCK_CONFIRMED, GroupBuyActorType.SELLER,
                        groupBuy.isStockConfirmed() ? GateState.DONE : GateState.WAITING, groupBuy.getStockConfirmedAt()),
                gate(GateKey.POST_SUBMITTED, GroupBuyActorType.CREATOR,
                        myPostDone ? GateState.DONE : GateState.MY_TURN, myPostDone ? post.getSubmittedAt() : null),
                gate(GateKey.OPEN_APPROVED, GroupBuyActorType.ADMIN,
                        reviewState, reviewState == GateState.DONE ? post.getReviewedAt() : null));

        int sla = properties.getOpenReview().getSlaBusinessDays();
        // 승인이 시작일 전날까지 나야 한다 — 제출 후 SLA 영업일 안에 끝나는 가장 늦은 제출일(31 설계 4-4).
        LocalDate deadline = businessCalendar.latestStartToFinishBefore(groupBuy.getStartAt().toLocalDate(), sla);
        boolean overdue = !myPostDone && now.toLocalDate().isAfter(deadline);
        return new Readiness(gates, deadline, overdue, sla);
    }

    private static Gate gate(GateKey key, GroupBuyActorType actorType, GateState state, LocalDateTime doneAt) {
        return new Gate(key, actorType, state,
                state == GateState.MY_TURN ? GroupBuyTone.WARNING : GroupBuyTone.NEUTRAL,
                state == GateState.DONE ? doneAt : null);
    }

    /** 대가관계 표시는 게시물이 없어도 내린다 — C3·C4 모달 미리보기가 쓴다. */
    private Post post(GroupBuy groupBuy, GroupBuyPost post, LocalDateTime now) {
        GroupBuyPostStatus status = GroupBuyPostStatus.of(post, groupBuy);
        String disclosure = GroupBuyDisclosure.text(groupBuy.getMarket().getMarketName());
        if (post == null) {
            return new Post(status, status.getLabel(), status.getTone(), null, null, disclosure, true,
                    null, null, null, null, null, null, null, null);
        }
        boolean terminal = groupBuy.getStatus().isTerminal();
        LocalDate expectedReviewDate = post.isPendingReview() && post.getSubmittedAt() != null
                ? businessCalendar.addBusinessDays(post.getSubmittedAt().toLocalDate(),
                        properties.getOpenReview().getSlaBusinessDays())
                : null;
        Rejection rejection = post.getReviewStatus() == GroupBuyPostReviewStatus.REJECTED
                ? new Rejection(post.getRejectReasonCode(), post.getRejectReasonDetail(), post.getReviewedAt())
                : null;
        Hidden hidden = !terminal && post.isHidden()
                ? new Hidden(post.getHiddenReasonCode(), post.getHiddenReasonDetail(), post.getHiddenAt(),
                        GroupBuy.daysInclusive(post.getHiddenAt(), now))
                : null;
        return new Post(
                status,
                status.getLabel(),
                status.getTone(),
                post.getTitle(),
                post.getContent(),
                disclosure,
                true,
                post.getSubmittedAt(),
                expectedReviewDate,
                post.getReviewedAt(),
                groupBuy.getOpenedAt(),
                terminal ? groupBuy.getEndedAt() : null,
                post.getLastEditedAt(),
                rejection,
                hidden);
    }

    /**
     * 종료(ENDED)에서도 <b>잠정치로 내린다</b> — 파트너(§30-4 「종료 화면에 KPI 없음」)와 반대 결정이다. 스튜디오 B7은
     * 「판매 실적(잠정) · 확정 시 변동」을 그렸고, {@code basis = PROVISIONAL}로 잠정임을 못박는다(31 설계 4-5 · 10-1 #4).
     */
    private Sales sales(GroupBuy groupBuy, GroupBuyFacts facts, GroupBuySales sales, Long myReward) {
        SalesBasis basis = switch (groupBuy.getStatus()) {
            case IN_PROGRESS, SUSPENSION_SCHEDULED -> SalesBasis.LIVE;
            case ENDED -> SalesBasis.PROVISIONAL;
            case SETTLED -> SalesBasis.SETTLED;
            case SUSPENDED -> SalesBasis.AT_SUSPENSION;
            default -> null;
        };
        if (basis == null || sales == null) {
            return null;
        }
        // 「숨김 이후 0건」은 사실 주장이다 — 판매 모듈이 모르면 0이 아니라 null이다(31 설계 8-1).
        Long ordersSinceHidden = facts.isPostHidden() && groupBuy.getStatus().isSelling()
                ? salesReader.countOrdersSince(groupBuy.getId(), facts.post().getHiddenAt()).orElse(null)
                : null;
        List<ItemQuantity> quantities = sales.itemQuantities().stream()
                .map(quantity -> new ItemQuantity(quantity.productId(), quantity.quantity()))
                .toList();
        return new Sales(basis, sales.orderCount(), quantities, sales.amount(), myReward, ordersSinceHidden);
    }

    /** 항목별 수량 × 개당 리워드의 합 — 파트너 KPI와 같은 계산이다. */
    private static long myReward(Contract contract, GroupBuySales sales) {
        Map<Long, ContractItem> itemsByProduct = contract.getItems().stream()
                .filter(item -> item.getProductId() != null)
                .collect(Collectors.toMap(ContractItem::getProductId, Function.identity(), (a, b) -> a));
        return sales.itemQuantities().stream()
                .mapToLong(quantity -> {
                    ContractItem item = itemsByProduct.get(quantity.productId());
                    Long unit = item == null ? null
                            : RewardCalculator.calcUnitReward(item.getGroupBuyPrice(), item.getRewardRate());
                    return unit == null ? 0L : unit * quantity.quantity();
                })
                .sum();
    }

    private OrderClosure orderClosure(GroupBuy groupBuy) {
        GroupBuyStatus status = groupBuy.getStatus();
        if (!status.isSelling() && status != GroupBuyStatus.ENDED && status != GroupBuyStatus.SUSPENDED) {
            return null;
        }
        return salesReader.readClosure(groupBuy.getId())
                .map(closure -> new OrderClosure(closure.totalCount(), closure.closedCount(),
                        new Unclosed(closure.unclosedCount(), closure.awaitingShipment(), closure.inReturnOrExchange())))
                .orElse(null);
    }

    private Extension extension(GroupBuy groupBuy, GroupBuyExtensionRequest request) {
        if (request == null) {
            return null;
        }
        return new Extension(
                request.getStatus(),
                request.getExtensionDays(),
                request.getReason(),
                request.getBeforeEndAt(),
                request.getAfterEndAt(),
                GroupBuy.daysInclusive(groupBuy.getStartAt(), request.getBeforeEndAt()),
                GroupBuy.daysInclusive(groupBuy.getStartAt(), request.getAfterEndAt()),
                request.getRequestedAt(),
                // 응답 기한 = 현재 종료 시각(§29-6). 무응답이면 변경 없이 종결된다.
                request.isPending() ? groupBuy.getEndAt() : null,
                request.getRespondedAt(),
                request.getResponseActorType(),
                request.getRejectReasonCode(),
                extensionRejectLabel(request.getRejectReasonCode()),
                request.getRejectMemo());
    }

    private static String extensionRejectLabel(String code) {
        if (code == null) {
            return null;
        }
        try {
            return ExtensionRejectReason.valueOf(code).getLabel();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 상대(브랜드)의 요청은 메모를 내리지 않는다 — 메모는 운영자에게 쓴 글이다(31 설계 4-6). 조기 마감 요청도 내린다 —
     * 남은 판매일을 줄이는 요청이라 알아야 한다(시안에 수신 화면은 없다 · 10-1 #10).
     */
    private ActiveRequest activeRequest(GroupBuyChangeRequest request) {
        boolean mine = request.isRequestedBy(GroupBuyActorType.CREATOR);
        return new ActiveRequest(
                request.getRequestType(),
                request.getRequesterType(),
                mine,
                request.getReasonCode(),
                request.reasonLabel(),
                mine ? request.getMemo() : null,
                request.getRequestedAt());
    }

    /** 중단 예정일 때 진행 중 통지만 — 소명 기한·소명 내용·첨부는 브랜드↔운영자 절차라 싣지 않는다. */
    private AdminSuspension adminSuspension(GroupBuy groupBuy, GroupBuyFacts facts, LocalDateTime now) {
        if (groupBuy.getStatus() != GroupBuyStatus.SUSPENSION_SCHEDULED) {
            return null;
        }
        return facts.activeNotice()
                .map(notice -> new AdminSuspension(
                        notice.getKind(),
                        notice.getReasonClause(),
                        notice.getNoticeBody(),
                        notice.getNoticedAt(),
                        notice.getExecuteScheduledAt(),
                        notice.getExecuteScheduledAt() == null ? null
                                : businessCalendar.businessDaysBetween(now.toLocalDate(),
                                        notice.getExecuteScheduledAt().toLocalDate())))
                .orElse(null);
    }

    private Closure closure(GroupBuy groupBuy, GroupBuyFacts facts) {
        if (!groupBuy.getStatus().isTerminal()) {
            return null;
        }
        GroupBuyChangeRequest request = facts.closingChangeRequest().orElse(null);
        GroupBuyAdminSuspension suspension = request == null ? facts.closingAdminSuspension().orElse(null) : null;

        ClosureSource source = request != null ? ClosureSource.REQUEST
                : suspension == null ? null
                : suspension.getKind() == AdminSuspensionKind.EMERGENCY ? ClosureSource.ADMIN_EMERGENCY
                : ClosureSource.ADMIN_NOTICE;
        Requester requester = request == null ? null : new Requester(
                request.getRequesterType(),
                request.isRequestedBy(GroupBuyActorType.CREATOR),
                request.isRequestedBy(GroupBuyActorType.CREATOR)
                        ? groupBuy.getCreator().getShowroomName() : groupBuy.getMarket().getMarketName(),
                request.getReasonCode(),
                request.reasonLabel(),
                request.getRequestedAt());
        AdminBasis adminBasis = suspension == null ? null : new AdminBasis(
                suspension.getKind(),
                suspension.getReasonClause(),
                suspension.getEmergencyReason(),
                suspension.getNoticeBody());
        return new Closure(
                groupBuy.getCloseType(),
                groupBuy.getEndedAt(),
                source,
                requester,
                request == null ? null : request.getDecisionReason(),
                request != null ? request.getDecidedAt() : suspension == null ? null : suspension.getExecutedAt(),
                adminBasis);
    }

    /**
     * 확정 리워드는 정산 모듈 값을 <b>그대로</b> 받는다. 공제 전 합계의 고정 지급비는 브랜드 직접 지급이라 실제로 받았는지와
     * 무관한 계약 금액이다 — 이름의 「공제 전」에 그 뜻을 싣는다.
     */
    private Settlement settlement(GroupBuy groupBuy, Contract contract) {
        if (groupBuy.getStatus() != GroupBuyStatus.SETTLED) {
            return null;
        }
        Long confirmedReward = settlementReader.readConfirmedReward(groupBuy.getId()).orElse(null);
        Integer fixedFee = contract.getFixedFeeAmount();
        Long total = confirmedReward == null ? null : confirmedReward + (fixedFee == null ? 0L : fixedFee);
        return new Settlement(groupBuy.getSettledAt(), fixedFee, confirmedReward, total);
    }

    /**
     * 확인의 방향이 파트너와 반대다 — {@code mine}은 내가 브랜드를 확인한 결과(CREATOR 행), {@code theirs}는 브랜드가 나를
     * 확인한 결과(SELLER 행). <b>고정 지급비 지급 사실은 싣지 않는다</b> — 이 모달은 인플루언서가 「브랜드가 지급했는가」를
     * 판단하는 자리라 브랜드 신고로 답을 미리 채우면 확인이 검증력을 잃는다(31 설계 4-7).
     */
    private AfterEnd afterEnd(GroupBuy groupBuy, Contract contract, GroupBuyFacts facts) {
        GroupBuyStatus status = groupBuy.getStatus();
        if (status != GroupBuyStatus.ENDED && status != GroupBuyStatus.SETTLED) {
            return null;
        }
        List<GroupBuyFulfillmentCheck> checks = facts.fulfillmentChecks();
        Long threadId = checks.stream()
                .map(GroupBuyFulfillmentCheck::getThreadId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        return new AfterEnd(new Fulfillment(
                facts.fulfillmentCheck(FulfillmentSide.CREATOR).map(this::toCheck).orElse(null),
                facts.fulfillmentCheck(FulfillmentSide.SELLER).map(this::toCheck).orElse(null),
                brandTarget(contract),
                creatorTarget(contract),
                groupBuy.getFulfillmentDueAt(),
                // 스위치가 꺼진 채 「놔두면 이행」 문구가 나가면 인플루언서가 문제를 제기하지 않는다 — FE가 이 값으로 문구를 고른다.
                properties.getFulfillment().isAutoConfirmOnTimeout(),
                GroupBuyCommandService.isSettlementOnHold(groupBuy, checks),
                threadId,
                groupBuy.getFulfillmentResolvedAt()));
    }

    private FulfillmentCheck toCheck(GroupBuyFulfillmentCheck check) {
        return new FulfillmentCheck(check.getResult(), check.getReason(), check.getCheckedAt(), check.isAutoConfirmed());
    }

    private static Target brandTarget(Contract contract) {
        List<Duty> duties = new ArrayList<>();
        duties.add(Duty.ORDER_DELIVERY);
        if (contract.hasFixedFee()) {
            duties.add(Duty.FIXED_FEE_PAYMENT);
        }
        return new Target(Party.BRAND, duties, null);
    }

    /** B7 「쇼룸 공구 게시물 · 인스타그램 피드 1 · 릴스 1 · 스토리 3」 — 0인 의무는 빼고 수는 계약에서 읽는다. */
    private static Target creatorTarget(Contract contract) {
        int feed = nullToZero(contract.getContentFeedCount());
        int reels = nullToZero(contract.getContentReelsCount());
        int story = nullToZero(contract.getContentStoryCount());
        List<Duty> duties = new ArrayList<>();
        duties.add(Duty.SHOWROOM_POST);
        if (feed > 0) {
            duties.add(Duty.FEED);
        }
        if (reels > 0) {
            duties.add(Duty.REELS);
        }
        if (story > 0) {
            duties.add(Duty.STORY);
        }
        return new Target(Party.CREATOR, duties, new ContentCounts(feed, reels, story));
    }

    /** 화이트리스트는 쿼리에서 거르고, detail은 이벤트별 정책으로 정한다(31 설계 6-2). */
    private List<HistoryEntry> history(GroupBuy groupBuy, GroupBuyFacts facts) {
        Map<Long, GroupBuyChangeRequest> requests = facts.changeRequests().stream()
                .collect(Collectors.toMap(GroupBuyChangeRequest::getId, Function.identity(), (a, b) -> a));
        return historyRepository.findByGroupBuyIdAndEventTypeInOrderByOccurredAtAscIdAsc(
                        groupBuy.getId(), CreatorGroupBuyHistoryPolicy.visibleEvents())
                .stream()
                .map(entry -> new HistoryEntry(
                        entry.getEventType(),
                        entry.getActorType(),
                        entry.getActorType() == GroupBuyActorType.ADMIN ? null : entry.getActorDisplayName(),
                        detailOf(entry, requests),
                        entry.getOccurredAt()))
                .toList();
    }

    private static String detailOf(GroupBuyHistory entry, Map<Long, GroupBuyChangeRequest> requests) {
        return switch (CreatorGroupBuyHistoryPolicy.ruleOf(entry.getEventType())) {
            case KEEP -> entry.getDetail();
            case DROP -> null;
            // 요청 행이 없으면(ref_id 도입 전 이력) 라벨도 없다 — 원문을 잘라 쓰지 않는다.
            case REASON_LABEL -> entry.getRefId() == null ? null
                    : requests.containsKey(entry.getRefId()) ? requests.get(entry.getRefId()).reasonLabel() : null;
        };
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
