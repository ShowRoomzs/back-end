package showroomz.api.admin.groupbuy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.changerequest.ChangeRequestElapsedFormatter;
import showroomz.api.admin.groupbuy.dto.AdminGroupBuyDetailResponse;
import showroomz.api.admin.groupbuy.dto.AdminGroupBuyDetailResponse.*;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractItem;
import showroomz.domain.groupbuy.entity.GroupBuy;
import showroomz.domain.groupbuy.entity.GroupBuyAdminSuspension;
import showroomz.domain.groupbuy.entity.GroupBuyChangeRequest;
import showroomz.domain.groupbuy.entity.GroupBuyExtensionRequest;
import showroomz.domain.groupbuy.entity.GroupBuyFulfillmentCheck;
import showroomz.domain.groupbuy.entity.GroupBuyHistory;
import showroomz.domain.groupbuy.entity.GroupBuyPost;
import showroomz.domain.groupbuy.entity.GroupBuyPostRevision;
import showroomz.domain.groupbuy.repository.GroupBuyAppealAttachmentRepository;
import showroomz.domain.groupbuy.repository.GroupBuyHistoryRepository;
import showroomz.domain.groupbuy.repository.GroupBuyPostRevisionRepository;
import showroomz.domain.groupbuy.service.GroupBuyCommandService;
import showroomz.domain.groupbuy.service.GroupBuyDisclosure;
import showroomz.domain.groupbuy.service.GroupBuyFacts;
import showroomz.domain.groupbuy.service.GroupBuyFactsLoader;
import showroomz.domain.groupbuy.service.GroupBuyFixedFeeText;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader;
import showroomz.domain.groupbuy.service.port.GroupBuySalesReader.GroupBuySales;
import showroomz.domain.groupbuy.service.port.GroupBuySettlementGateway;
import showroomz.domain.groupbuy.service.port.GroupBuySettlementGateway.SettlementStage;
import showroomz.domain.groupbuy.service.port.GroupBuyThreadGateway;
import showroomz.domain.groupbuy.type.AdminSuspensionKind;
import showroomz.domain.groupbuy.type.ChangeRequestType;
import showroomz.domain.groupbuy.type.ExtensionRejectReason;
import showroomz.domain.groupbuy.type.FulfillmentSide;
import showroomz.domain.groupbuy.type.GroupBuyActorType;
import showroomz.domain.groupbuy.type.GroupBuyAttachmentStatus;
import showroomz.domain.groupbuy.type.GroupBuyCloseType;
import showroomz.domain.groupbuy.type.GroupBuyPostHideReason;
import showroomz.domain.groupbuy.type.GroupBuyPostRejectReason;
import showroomz.domain.groupbuy.type.GroupBuyPostReviewStatus;
import showroomz.domain.groupbuy.type.GroupBuyPostRevisionKind;
import showroomz.domain.groupbuy.type.GroupBuyPostStatus;
import showroomz.domain.groupbuy.type.GroupBuyStatus;
import showroomz.domain.groupbuy.type.GroupBuyTone;
import showroomz.domain.inquiry.repository.ProductInquiryRepository;
import showroomz.domain.inquiry.type.ProductInquiryType;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.global.config.properties.GroupBuyProperties;
import showroomz.global.utils.BusinessCalendar;
import showroomz.global.utils.RewardCalculator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 어드민 공구 상세 조립(32 설계 4절). 버튼 판정은 {@link AdminGroupBuyPermissionPolicy}와 같은 사실 스냅샷을 본다.
 *
 * <p>어드민에만 있는 것 — 최소 물량 · 상대 요청 메모 · 소명 전량과 첨부 · 수정 횟수·최신 판본 · 운영자 실명 · 이력 전량.
 * 어드민에 없는 것 — 「내가 받는 금액」 · 고정 지급비 지급 상태(조회 대상조차 아니다) · 낙관적 락 버전.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminGroupBuyDetailAssembler {

    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);
    private static final DateTimeFormatter POST_NUMBER_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String DEFAULT_OPERATOR_NAME = "운영자";

    private final GroupBuyFactsLoader factsLoader;
    private final GroupBuyHistoryRepository historyRepository;
    private final GroupBuyPostRevisionRepository revisionRepository;
    private final GroupBuyAppealAttachmentRepository appealAttachmentRepository;
    private final GroupBuySalesReader salesReader;
    private final GroupBuySettlementGateway settlementGateway;
    private final GroupBuyThreadGateway threadGateway;
    private final ProductInquiryRepository productInquiryRepository;
    private final SellerRepository sellerRepository;
    private final AdminGroupBuyPermissionPolicy permissionPolicy;
    private final BusinessCalendar businessCalendar;
    private final GroupBuyProperties properties;

    public AdminGroupBuyDetailResponse assemble(GroupBuy groupBuy, Navigation navigation) {
        LocalDateTime now = LocalDateTime.now();
        GroupBuyFacts facts = factsLoader.load(groupBuy);
        Contract contract = groupBuy.getContract();
        List<GroupBuyPostRevision> revisions = facts.post() == null ? List.of()
                : revisionRepository.findByGroupBuyPostPostIdOrderByRevisionNoAsc(facts.post().getPostId());
        List<GroupBuyHistory> history = historyRepository.findByGroupBuyIdOrderByOccurredAtAscIdAsc(groupBuy.getId());
        Names names = names(facts);
        GroupBuySales sales = salesReader.readSales(groupBuy.getId()).orElse(null);

        return new AdminGroupBuyDetailResponse(
                summary(groupBuy, contract),
                timeline(groupBuy, contract, now),
                new Brand(groupBuy.getMarket().getId(), groupBuy.getMarket().getMarketName(),
                        threadGateway.findPairThreadId(groupBuy).orElse(null)),
                new CreatorRef(groupBuy.getCreator().getId(), groupBuy.getCreator().getShowroomName(),
                        groupBuy.getCreator().getAccountId()),
                new ContractRef(contract.getId(), contract.getContractNumber(), contract.getConcludedAt()),
                items(contract),
                fixedFee(contract),
                readiness(groupBuy, facts.post(), names),
                openReview(groupBuy, facts.post(), now),
                post(groupBuy, facts.post(), revisions, names, now),
                sales(groupBuy, contract, sales),
                facts.pendingChangeRequest().map(request -> activeRequest(groupBuy, request, sales)).orElse(null),
                extension(groupBuy, facts.extension()),
                adminSuspension(facts, sales, names, now),
                afterEnd(groupBuy, facts, contract, sales, now),
                closure(groupBuy, facts, sales, names),
                permissionPolicy.evaluate(facts, now),
                history(groupBuy, history, revisions),
                navigation);
    }

    private Summary summary(GroupBuy groupBuy, Contract contract) {
        GroupBuyStatus status = groupBuy.getStatus();
        GroupBuyCloseType closeType = groupBuy.getCloseType();
        return new Summary(groupBuy.getId(), groupBuy.getGroupBuyNumber(), contract.getTitle(),
                status, status.getLabel(), status.getTone(),
                groupBuy.getCreatedAt(), groupBuy.getReadyAt(), groupBuy.getOpenedAt(), groupBuy.getEndedAt(),
                closeType, closeType == null ? null : closeType.getLabel(), groupBuy.getSettledAt());
    }

    /** 파트너 상세와 같은 양끝 포함 일자 계산 · 서버 now 기준. */
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

    private List<Item> items(Contract contract) {
        return contract.getItems().stream()
                .map(item -> new Item(item.getProductId(), item.getProductName(), item.getRegularPrice(),
                        item.getGroupBuyPrice(), item.getRewardRate(),
                        RewardCalculator.calcUnitReward(item.getGroupBuyPrice(), item.getRewardRate()),
                        item.getMinQuantity()))
                .toList();
    }

    private FixedFee fixedFee(Contract contract) {
        return new FixedFee(contract.getFixedFeeAmount(), contract.getFixedFeeTrigger(),
                contract.getFixedFeeTrigger() == null ? null : contract.getFixedFeeTrigger().getLabel(),
                GroupBuyFixedFeeText.of(contract));
    }

    // ── B1 준비 게이트 · 오픈 승인 ─────────────────────────────────────────────

    /** 어드민 관점의 게이트 상태 — 운영자 차례(오픈 승인 대기)만 MY_TURN · 경고 톤이다(32 설계 4-3). */
    private Readiness readiness(GroupBuy groupBuy, GroupBuyPost post, Names names) {
        GroupBuyStatus status = groupBuy.getStatus();
        if (status != GroupBuyStatus.PREPARING && status != GroupBuyStatus.READY) {
            return null;
        }
        boolean stockDone = groupBuy.isStockConfirmed();
        GroupBuyPostReviewStatus review = post == null ? null : post.getReviewStatus();
        boolean postDone = review == GroupBuyPostReviewStatus.PENDING || review == GroupBuyPostReviewStatus.APPROVED;
        boolean approved = review == GroupBuyPostReviewStatus.APPROVED;

        return new Readiness(List.of(
                gate(GateKey.STOCK_CONFIRMED, GroupBuyActorType.SELLER, stockDone,
                        stockDone ? groupBuy.getStockConfirmedAt() : null,
                        stockDone ? groupBuy.getMarket().getMarketName() : null,
                        stockDone ? GateState.DONE : GateState.WAITING),
                gate(GateKey.POST_SUBMITTED, GroupBuyActorType.CREATOR, postDone,
                        postDone ? post.getSubmittedAt() : null,
                        postDone ? groupBuy.getCreator().getShowroomName() : null,
                        postDone ? GateState.DONE
                                : review == GroupBuyPostReviewStatus.REJECTED ? GateState.REJECTED : GateState.WAITING),
                gate(GateKey.OPEN_APPROVED, GroupBuyActorType.ADMIN, approved,
                        approved ? post.getReviewedAt() : null,
                        approved ? names.of(post.getReviewedBy()) : null,
                        approved ? GateState.DONE
                                : review == GroupBuyPostReviewStatus.PENDING ? GateState.MY_TURN : GateState.WAITING)));
    }

    private static Gate gate(GateKey key, GroupBuyActorType actor, boolean done, LocalDateTime doneAt,
                             String doneByName, GateState state) {
        return new Gate(key, actor, done, doneAt, doneByName, state,
                state == GateState.MY_TURN ? GroupBuyTone.WARNING : done ? GroupBuyTone.SUCCESS : GroupBuyTone.NEUTRAL);
    }

    /**
     * 「SLA 영업일 3일 · 기한 08.19 (D-2)」 — 제출일은 세지 않는다(31 설계 4-4와 같은 달력). 기한은 표시이지 판정이
     * 아니다 — 초과해도 자동 처리하지 않는다.
     */
    private OpenReview openReview(GroupBuy groupBuy, GroupBuyPost post, LocalDateTime now) {
        if (groupBuy.getStatus() != GroupBuyStatus.PREPARING || post == null || !post.isPendingReview()
                || post.getSubmittedAt() == null) {
            return null;
        }
        int sla = properties.getOpenReview().getSlaBusinessDays();
        LocalDateTime dueAt = openReviewDueAt(post.getSubmittedAt(), sla);
        return new OpenReview(post.getSubmittedAt(), sla, dueAt,
                ChronoUnit.DAYS.between(now.toLocalDate(), dueAt.toLocalDate()),
                groupBuy.getStartAt(), now.isAfter(dueAt));
    }

    /** 오픈 승인 기한 — 요약의 최단 기한과 같은 식이다. */
    public LocalDateTime openReviewDueAt(LocalDateTime submittedAt, int slaBusinessDays) {
        return businessCalendar.addBusinessDays(submittedAt.toLocalDate(), slaBusinessDays).atTime(END_OF_DAY);
    }

    // ── 게시물 ────────────────────────────────────────────────────────────────

    /** 원문 전체 · 판본 · 숨김(32 설계 4-4). 종결 후에도 원문을 내린다 — 4개 종결 화면 모두 원문을 보여준다. */
    private Post post(GroupBuy groupBuy, GroupBuyPost post, List<GroupBuyPostRevision> revisions, Names names,
                      LocalDateTime now) {
        GroupBuyPostStatus status = GroupBuyPostStatus.of(post, groupBuy);
        String disclosure = GroupBuyDisclosure.text(groupBuy.getMarket().getMarketName());
        GroupBuyCloseType closedBy = groupBuy.getStatus().isTerminal() ? groupBuy.getCloseType() : null;
        if (post == null) {
            return new Post(status, status.getLabel(), status.getTone(), null, null, null, disclosure,
                    null, null, null, null, 0, null, null, null, closedBy);
        }
        long editCount = revisions.stream().filter(r -> r.getKind() == GroupBuyPostRevisionKind.EDITED).count();
        Integer latestRevisionNo = revisions.isEmpty() ? null : revisions.get(revisions.size() - 1).getRevisionNo();

        Rejection rejection = null;
        if (post.getReviewStatus() != GroupBuyPostReviewStatus.APPROVED && post.getRejectReasonCode() != null) {
            GroupBuyPostRejectReason reason = GroupBuyPostRejectReason.parse(post.getRejectReasonCode());
            rejection = new Rejection(post.getRejectReasonCode(),
                    reason == null ? null : reason.getLabel(),
                    reason == null ? null : reason.getAxis().name(),
                    reason == null ? null : reason.getAxis().getLabel(),
                    post.getRejectReasonDetail(), post.getReviewedAt(), names.of(post.getReviewedBy()));
        }

        Hidden hidden = null;
        if (post.isHidden()) {
            GroupBuyPostHideReason reason = GroupBuyPostHideReason.parse(post.getHiddenReasonCode());
            // 포트가 비면 null — 거짓 0은 「숨김이 판매를 멈췄다」는 사실 주장이 된다(0-7).
            Long ordersSinceHidden = groupBuy.getStatus().isSelling()
                    ? salesReader.countOrdersSince(groupBuy.getId(), post.getHiddenAt()).orElse(null) : null;
            hidden = new Hidden(post.getHiddenReasonCode(), reason == null ? null : reason.getLabel(),
                    post.getHiddenReasonDetail(), post.getHiddenAt(), names.of(post.getHiddenBy()),
                    GroupBuy.daysInclusive(post.getHiddenAt(), now), post.getHiddenRevisionNo(), ordersSinceHidden);
        }

        return new Post(status, status.getLabel(), status.getTone(),
                postNumber(post), post.getTitle(), post.getContent(), disclosure,
                post.getSubmittedAt(), post.getReviewedAt(),
                post.getReviewedAt() == null ? null : names.of(post.getReviewedBy()),
                post.getLastEditedAt(), editCount, latestRevisionNo, rejection, hidden, closedBy);
    }

    /** 「POST-GB-20260802-104」 — 게시물 번호 체계가 없어 표시 전용으로 파생한다. 저장하지 않는다(13-2 #16). */
    private static String postNumber(GroupBuyPost post) {
        if (post.getSubmittedAt() == null) {
            return null;
        }
        return "POST-GB-%s-%d".formatted(post.getSubmittedAt().format(POST_NUMBER_DATE), post.getPostId());
    }

    // ── 판매 · 판단 근거 ────────────────────────────────────────────────────────

    /** 진행중·중단 예정 = LIVE · 정산완료 = SETTLED. 종료·중단은 null — 시안에 4칸 그리드가 없다(4-5). */
    private Sales sales(GroupBuy groupBuy, Contract contract, GroupBuySales sales) {
        SalesBasis basis = switch (groupBuy.getStatus()) {
            case IN_PROGRESS, SUSPENSION_SCHEDULED -> SalesBasis.LIVE;
            case SETTLED -> SalesBasis.SETTLED;
            default -> null;
        };
        if (basis == null || sales == null) {
            return null;
        }
        Map<Long, ContractItem> itemsByProduct = contract.getItems().stream()
                .filter(item -> item.getProductId() != null)
                .collect(Collectors.toMap(ContractItem::getProductId, Function.identity(), (a, b) -> a));
        long rewardAmount = sales.itemQuantities().stream()
                .mapToLong(quantity -> {
                    ContractItem item = itemsByProduct.get(quantity.productId());
                    Long unit = item == null ? null
                            : RewardCalculator.calcUnitReward(item.getGroupBuyPrice(), item.getRewardRate());
                    return unit == null ? 0L : unit * quantity.quantity();
                })
                .sum();
        return new Sales(basis, sales.orderCount(), sales.amount(), rewardAmount,
                sales.itemQuantities().stream().map(q -> new ItemQuantity(q.productId(), q.quantity())).toList());
    }

    private ActiveRequest activeRequest(GroupBuy groupBuy, GroupBuyChangeRequest request, GroupBuySales sales) {
        return new ActiveRequest(
                request.getId(),
                request.getRequestType(),
                request.getRequestType().getLabel(),
                request.getRequesterType(),
                request.getRequesterType() == GroupBuyActorType.CREATOR
                        ? groupBuy.getCreator().getShowroomName() : groupBuy.getMarket().getMarketName(),
                request.getReasonCode(),
                request.reasonLabel(),
                // 운영자에게 쓴 글이다 — 수신자가 바로 운영자라 양측 요청 메모를 모두 내린다(31 설계 4-6).
                request.getMemo(),
                request.getStatusAtRequest(),
                request.getRequestedAt(),
                ChangeRequestElapsedFormatter.format(request.getRequestedAt()),
                request.getRequestType() == ChangeRequestType.SUSPEND
                        ? suspendBasis(groupBuy, request, sales)
                        : earlyCloseBasis(groupBuy, request, sales));
    }

    /**
     * B3 — 요청 시점 스냅샷과 현재값의 차 · CS 문의. 1:1 문의 쪽을 모르면 {@code total = null} — 상품 문의만 세서 내리면
     * 「12건」이 「3건」으로 줄어 보이고 하자 주장의 반증으로 읽힌다(4-5).
     */
    private DecisionBasis suspendBasis(GroupBuy groupBuy, GroupBuyChangeRequest request, GroupBuySales sales) {
        Integer ordersAtRequest = request.getSalesOrderCountAtRequest();
        Integer ordersNow = sales == null ? null : sales.orderCount();
        Integer sinceRequest = ordersAtRequest == null || ordersNow == null ? null : ordersNow - ordersAtRequest;
        Long total = salesReader.countOneToOneInquiriesSince(groupBuy.getId(), request.getRequestedAt())
                .map(oneToOne -> oneToOne + countProductInquiries(groupBuy, request.getRequestedAt(), null))
                .orElse(null);
        return new DecisionBasis(ordersAtRequest, ordersNow, sinceRequest, quantityOf(sales),
                sales == null ? null : sales.amount(), new Inquiries(total, null),
                null, null, null, null, null);
    }

    /**
     * B4 — 판매 수량 / 준비 물량(계약 최소 물량 합 · 실제 재고가 아니다) · 요청 후 재입고 문의. 품절 문의는 상품 문의
     * 테이블만으로 셀 수 있어 판매 포트 없이도 값이 나온다.
     */
    private DecisionBasis earlyCloseBasis(GroupBuy groupBuy, GroupBuyChangeRequest request, GroupBuySales sales) {
        Integer quantityNow = quantityOf(sales);
        List<Integer> minQuantities = groupBuy.getContract().getItems().stream().map(ContractItem::getMinQuantity).toList();
        Integer prepared = minQuantities.stream().anyMatch(Objects::isNull) ? null
                : minQuantities.stream().mapToInt(Integer::intValue).sum();
        Integer rate = quantityNow == null || prepared == null || prepared == 0 ? null : quantityNow * 100 / prepared;
        return new DecisionBasis(null, sales == null ? null : sales.orderCount(), null, quantityNow,
                sales == null ? null : sales.amount(), null, prepared, rate,
                countProductInquiries(groupBuy, request.getRequestedAt(), ProductInquiryType.RESTOCK),
                groupBuy.getEndAt(), true);
    }

    private long countProductInquiries(GroupBuy groupBuy, LocalDateTime since, ProductInquiryType type) {
        List<Long> productIds = contractProductIds(groupBuy);
        if (productIds.isEmpty()) {
            return 0L;
        }
        return type == null
                ? productInquiryRepository.countByProductIdsSince(productIds, since)
                : productInquiryRepository.countByProductIdsAndTypeSince(productIds, type, since);
    }

    private static List<Long> contractProductIds(GroupBuy groupBuy) {
        return groupBuy.getContract().getItems().stream()
                .map(ContractItem::getProductId).filter(Objects::nonNull).distinct().toList();
    }

    private static Integer quantityOf(GroupBuySales sales) {
        return sales == null ? null
                : sales.itemQuantities().stream().mapToInt(GroupBuySalesReader.ItemQuantity::quantity).sum();
    }

    // ── 연장 · 직권 중단 ────────────────────────────────────────────────────────

    /** 조회 카드 — 어드민은 판정하지 않는다(§32-4). */
    private Extension extension(GroupBuy groupBuy, GroupBuyExtensionRequest request) {
        if (request == null) {
            return null;
        }
        ExtensionRejectReason rejectReason = parseExtensionReject(request.getRejectReasonCode());
        return new Extension(request.getStatus(), request.getExtensionDays(), request.getReason(),
                request.getBeforeEndAt(), request.getAfterEndAt(),
                GroupBuy.daysInclusive(groupBuy.getStartAt(), request.getBeforeEndAt()),
                GroupBuy.daysInclusive(groupBuy.getStartAt(), request.getAfterEndAt()),
                request.getRequestedAt(), groupBuy.getEndAt(), request.getRespondedAt(), request.getResponseActorType(),
                request.getRejectReasonCode(), rejectReason == null ? null : rejectReason.getLabel(),
                request.getRejectMemo());
    }

    private static ExtensionRejectReason parseExtensionReject(String code) {
        try {
            return code == null ? null : ExtensionRejectReason.valueOf(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 진행 중 통지(B2c). 첨부 URL은 싣지 않는다 — 유효기간이 있어 화면을 오래 열어 둔 운영자가 만료된 링크를 누른다.
     * 목록만 내리고 클릭 시 발급한다(4-6).
     */
    private AdminSuspension adminSuspension(GroupBuyFacts facts, GroupBuySales sales, Names names, LocalDateTime now) {
        GroupBuyAdminSuspension notice = facts.activeNotice().orElse(null);
        if (notice == null) {
            return null;
        }
        SalesSinceNotice sinceNotice = sales == null || notice.getSalesOrderCountAtNotice() == null
                || notice.getSalesAmountAtNotice() == null ? null
                : new SalesSinceNotice(sales.orderCount() - notice.getSalesOrderCountAtNotice(),
                        sales.amount() - notice.getSalesAmountAtNotice());
        Appeal appeal = null;
        if (notice.isAppealSubmitted()) {
            List<AppealAttachment> attachments = appealAttachmentRepository
                    .findByAdminSuspensionIdAndStatusOrderByIdAsc(notice.getId(), GroupBuyAttachmentStatus.UPLOADED)
                    .stream()
                    .map(a -> new AppealAttachment(a.getId(), a.getOriginalName(), a.getContentType(), a.getSizeBytes()))
                    .toList();
            appeal = new Appeal(notice.getAppealContent(), notice.getAppealSubmittedAt(),
                    names.sellerName(notice.getAppealSubmittedBy()), attachments);
        }
        return new AdminSuspension(notice.getId(), notice.getKind(), notice.getReasonClause(), notice.basisLabel(),
                notice.getNoticeBody(), notice.getNoticedAt(), names.of(notice.getNoticedBy()),
                (int) ChronoUnit.DAYS.between(notice.getNoticedAt().toLocalDate(), now.toLocalDate()),
                notice.getExecuteScheduledAt(), notice.getAppealDeadlineAt(), notice.getNoticeRevisionNo(),
                sinceNotice, appeal,
                notice.getAppealDeadlineAt() != null && now.isAfter(notice.getAppealDeadlineAt()));
    }

    // ── 종료 이후 ────────────────────────────────────────────────────────────

    private AfterEnd afterEnd(GroupBuy groupBuy, GroupBuyFacts facts, Contract contract, GroupBuySales sales,
                              LocalDateTime now) {
        GroupBuyStatus status = groupBuy.getStatus();
        if (!status.isTerminal()) {
            return null;
        }
        boolean ended = status == GroupBuyStatus.ENDED || status == GroupBuyStatus.SETTLED;
        OrderClosure orderClosure = salesReader.readClosure(groupBuy.getId())
                .map(closure -> new OrderClosure(closure.totalCount(), closure.closedCount(), closure.unclosedCount(),
                        closure.unclosedStages() == null ? List.of() : closure.unclosedStages().stream()
                                .map(stage -> new UnclosedStage(stage.stage(), stage.label(), stage.count()))
                                .toList()))
                .orElse(null);
        OpenIssue openIssue = facts.openIssue() == null ? null : new OpenIssue(
                facts.openIssue().getId(), facts.openIssue().getIssueType(),
                facts.openIssue().getIssueType().getLabel(), facts.openIssue().getOpenerType(),
                facts.openIssue().getOpenedAt(), facts.openIssue().getThreadId());
        return new AfterEnd(
                ended ? fulfillment(groupBuy, facts, contract, now) : null,
                ended ? settlement(groupBuy, facts, contract, sales, now) : null,
                orderClosure,
                openIssue);
    }

    /**
     * 미이행 행은 그대로 UNFULFILLED다 — 이행 확인은 불가역이다(제20조②). 「합의로 해소」 표기는 FE가
     * {@code UNFULFILLED + agreedAt} 조합으로 만든다(4-8 ④).
     */
    private Fulfillment fulfillment(GroupBuy groupBuy, GroupBuyFacts facts, Contract contract, LocalDateTime now) {
        List<GroupBuyFulfillmentCheck> checks = facts.fulfillmentChecks();
        Long threadId = checks.stream().map(GroupBuyFulfillmentCheck::getThreadId)
                .filter(Objects::nonNull).findFirst().orElse(null);
        List<FulfillmentDuty> contentDuties = new ArrayList<>(List.of(FulfillmentDuty.SHOWROOM_POST));
        if (positive(contract.getContentFeedCount())) {
            contentDuties.add(FulfillmentDuty.FEED);
        }
        if (positive(contract.getContentReelsCount())) {
            contentDuties.add(FulfillmentDuty.REELS);
        }
        if (positive(contract.getContentStoryCount())) {
            contentDuties.add(FulfillmentDuty.STORY);
        }
        FulfillmentTargets targets = new FulfillmentTargets(
                new Target(contentDuties, new ContentCounts(contract.getContentFeedCount(),
                        contract.getContentReelsCount(), contract.getContentStoryCount())),
                new Target(List.of(FulfillmentDuty.ORDER_DELIVERY), null));
        LocalDateTime dueAt = groupBuy.getFulfillmentDueAt();
        return new Fulfillment(
                facts.fulfillmentCheck(FulfillmentSide.SELLER)
                        .map(check -> toCheck(check, groupBuy.getMarket().getMarketName())).orElse(null),
                facts.fulfillmentCheck(FulfillmentSide.CREATOR)
                        .map(check -> toCheck(check, groupBuy.getCreator().getShowroomName())).orElse(null),
                targets,
                dueAt,
                dueAt != null && now.isAfter(dueAt),
                properties.getFulfillment().isAutoConfirmOnTimeout(),
                threadId,
                groupBuy.getFulfillmentAgreedAt(),
                groupBuy.getFulfillmentResolutionNote(),
                groupBuy.getFulfillmentResolvedAt(),
                GroupBuyCommandService.isSettlementOnHold(groupBuy, checks));
    }

    private static boolean positive(Integer value) {
        return value != null && value > 0;
    }

    private static FulfillmentCheck toCheck(GroupBuyFulfillmentCheck check, String checkerName) {
        return new FulfillmentCheck(check.getResult(), check.getReason(), check.getCheckedAt(),
                check.isAutoConfirmed() ? null : checkerName, check.isAutoConfirmed());
    }

    /**
     * 정산 상태머신은 읽기만 한다(4-8 ⑤). 포트가 없으면 ENDED에서 WAITING으로 파생하고 {@code stageSource = DERIVED}다.
     * 리워드는 확정 대기 — 잠정 금액을 쓰지 않는다(⑥).
     */
    private Settlement settlement(GroupBuy groupBuy, GroupBuyFacts facts, Contract contract, GroupBuySales sales,
                                  LocalDateTime now) {
        SettlementStage stage;
        String source;
        if (groupBuy.getStatus() == GroupBuyStatus.SETTLED) {
            stage = SettlementStage.TRANSFERRED;
            source = AdminGroupBuyDetailResponse.STAGE_SOURCE_DERIVED;
        } else {
            var fromPort = settlementGateway.readStage(groupBuy.getId());
            stage = fromPort.orElse(SettlementStage.WAITING);
            source = fromPort.isPresent()
                    ? AdminGroupBuyDetailResponse.STAGE_SOURCE_PORT : AdminGroupBuyDetailResponse.STAGE_SOURCE_DERIVED;
        }
        Watch watch = null;
        if (groupBuy.getStatus() == GroupBuyStatus.ENDED && groupBuy.getEndedAt() != null) {
            int watchDays = properties.getSettlement().getWatchDays();
            watch = new Watch(groupBuy.getEndedAt().toLocalDate().plusDays(watchDays),
                    ChronoUnit.DAYS.between(groupBuy.getEndedAt().toLocalDate(), now.toLocalDate()),
                    !now.isBefore(groupBuy.getEndedAt().plusDays(watchDays)));
        }
        List<BigDecimal> rewardRates = contract.getItems().stream()
                .map(ContractItem::getRewardRate).filter(Objects::nonNull)
                .map(BigDecimal::stripTrailingZeros).distinct().toList();
        return new Settlement(stage, source, permissionPolicy.settlementBlockers(facts), watch,
                new Preview(sales == null ? null : sales.amount(), rewardRates, null));
    }

    /** B6 · 조기 마감 종결(4-9). 액션이 없다 — 판매 리워드 문구는 규칙 고지라 FE 상수다. */
    private Closure closure(GroupBuy groupBuy, GroupBuyFacts facts, GroupBuySales sales, Names names) {
        GroupBuyCloseType closeType = groupBuy.getCloseType();
        if (!groupBuy.getStatus().isTerminal() || closeType == null || closeType == GroupBuyCloseType.COMPLETED) {
            return null;
        }
        Integer accepted = closeType == GroupBuyCloseType.SUSPENDED && sales != null ? sales.orderCount() : null;
        GroupBuyChangeRequest request = facts.closingChangeRequest().orElse(null);
        if (request != null) {
            return new Closure(closeType, closeType.getLabel(), groupBuy.getEndedAt(), ClosureSource.REQUEST,
                    request.reasonLabel(), request.getMemo(),
                    new Requester(request.getRequesterType(),
                            request.getRequesterType() == GroupBuyActorType.CREATOR
                                    ? groupBuy.getCreator().getShowroomName() : groupBuy.getMarket().getMarketName(),
                            request.getRequestedAt()),
                    names.of(request.getDecidedBy()), request.getDecisionReason(), accepted, null);
        }
        GroupBuyAdminSuspension suspension = facts.closingAdminSuspension().orElse(null);
        if (suspension != null) {
            boolean emergency = suspension.getKind() == AdminSuspensionKind.EMERGENCY;
            return new Closure(closeType, closeType.getLabel(), groupBuy.getEndedAt(),
                    emergency ? ClosureSource.ADMIN_EMERGENCY : ClosureSource.ADMIN_NOTICE,
                    suspension.basisLabel(), suspension.getNoticeBody(), null,
                    names.of(suspension.getExecutedBy()), suspension.getExecutionNote(), accepted,
                    new AdminBasis(suspension.getKind(), suspension.getReasonClause(), suspension.getEmergencyReason(),
                            suspension.basisLabel(), suspension.getNoticeBody(), suspension.getExecutionNote()));
        }
        return new Closure(closeType, closeType.getLabel(), groupBuy.getEndedAt(), null, null, null, null,
                null, null, accepted, null);
    }

    // ── 이력 ────────────────────────────────────────────────────────────────

    /**
     * 이벤트 전량 + 게시물 수정 합성 행 · 최신순(4-11). 게시물 수정은 이력에 쓰지 않고 리비전에서 읽을 때 합성한다 —
     * 같은 사실을 두 테이블에 쓰지 않는다. {@code synthetic}으로 저장된 이벤트가 아님을 밝힌다.
     */
    private List<HistoryEntry> history(GroupBuy groupBuy, List<GroupBuyHistory> stored,
                                       List<GroupBuyPostRevision> revisions) {
        List<HistoryEntry> entries = new ArrayList<>();
        List<GroupBuyHistory> newestFirst = new ArrayList<>(stored);
        newestFirst.sort(Comparator.comparing(GroupBuyHistory::getOccurredAt)
                .thenComparing(GroupBuyHistory::getId).reversed());
        for (GroupBuyHistory entry : newestFirst) {
            entries.add(new HistoryEntry(entry.getEventType().name(), entry.getActorType(),
                    entry.getActorDisplayName(), entry.getDetail(), entry.getOccurredAt(), false, null));
        }
        int round = 0;
        List<HistoryEntry> edits = new ArrayList<>();
        for (GroupBuyPostRevision revision : revisions) {
            if (revision.getKind() != GroupBuyPostRevisionKind.EDITED) {
                continue;
            }
            round++;
            edits.add(new HistoryEntry("POST_EDITED", GroupBuyActorType.CREATOR,
                    groupBuy.getCreator().getShowroomName(), round + "회차 · 재승인 없음",
                    revision.getCreatedAt(), true, revision.getRevisionNo()));
        }
        return Stream.concat(entries.stream(), edits.stream())
                .sorted(Comparator.comparing(HistoryEntry::occurredAt).reversed())
                .toList();
    }

    // ── 처리자 실명 ─────────────────────────────────────────────────────────────

    /** 상세에 나오는 처리자(운영자·소명 제출자) 이름을 한 번에 읽는다. */
    private Names names(GroupBuyFacts facts) {
        Set<Long> ids = new HashSet<>();
        GroupBuyPost post = facts.post();
        if (post != null) {
            ids.add(post.getReviewedBy());
            ids.add(post.getHiddenBy());
        }
        for (GroupBuyAdminSuspension suspension : facts.adminSuspensions()) {
            ids.add(suspension.getNoticedBy());
            ids.add(suspension.getExecutedBy());
            ids.add(suspension.getAppealSubmittedBy());
        }
        facts.changeRequests().forEach(request -> ids.add(request.getDecidedBy()));
        ids.remove(null);
        Map<Long, String> byId = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Seller seller : sellerRepository.findAllById(ids)) {
                byId.put(seller.getId(), seller.getName());
            }
        }
        return new Names(byId);
    }

    private record Names(Map<Long, String> byId) {

        /** 운영자 — 이름이 없으면 「운영자」. */
        String of(Long operatorId) {
            if (operatorId == null) {
                return null;
            }
            String name = byId.get(operatorId);
            return name == null ? DEFAULT_OPERATOR_NAME : name;
        }

        /** 소명 제출자 셀러 계정 — 이름을 모르면 null. */
        String sellerName(Long sellerId) {
            return sellerId == null ? null : byId.get(sellerId);
        }
    }
}
