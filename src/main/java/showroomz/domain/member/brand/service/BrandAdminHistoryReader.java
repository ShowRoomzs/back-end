package showroomz.domain.member.brand.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.changerequest.entity.ChangeRequest;
import showroomz.domain.changerequest.repository.ChangeRequestRepository;
import showroomz.domain.changerequest.type.ChangeRequestStatus;
import showroomz.domain.changerequest.type.ChangeRequestType;
import showroomz.domain.history.entity.SellerApplicationHistory;
import showroomz.domain.history.repository.SellerApplicationHistoryRepository;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.brand.entity.BrandAdminHistory;
import showroomz.domain.member.brand.repository.BrandAdminHistoryRepository;
import showroomz.domain.member.brand.type.BrandAdminActorType;
import showroomz.domain.member.brand.type.BrandAdminEventType;
import showroomz.domain.member.seller.entity.Seller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 브랜드 처리 이력 읽기(52 설계서 5-2) — 세 원천을 합쳐 최신순으로 낸다: 이 모듈의 저장 이력 · 가입 / 입점 승인(셀러 · 신청 이력) · 변경 요청
 * (접수 · 승인 · 반려 — 브랜드가 철회한 건은 운영자 조치의 근거가 아니라 뺀다).
 *
 * <p>메모리 병합이다 — 한 브랜드의 사건은 많아야 수백 건이고, 원천마다 페이지하면 순서가 어긋난다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandAdminHistoryReader {

    private static final String DEFAULT_OPERATOR = "운영자";

    private final BrandAdminHistoryRepository historyRepository;
    private final SellerApplicationHistoryRepository applicationHistoryRepository;
    private final ChangeRequestRepository changeRequestRepository;
    private final SellerRepository sellerRepository;

    public record Item(BrandAdminEventType type, BrandAdminActorType actorType, String actorName, String detail,
                       String refType, Long refId, LocalDateTime occurredAt) {
    }

    /** 최신순 전체. */
    public List<Item> read(Market market) {
        Seller seller = market.getSeller();
        List<Item> items = new ArrayList<>();
        for (BrandAdminHistory h : historyRepository.findByMarketIdOrderByCreatedAtDescIdDesc(market.getId())) {
            items.add(new Item(h.getEventType(), h.getActorType(), actorName(h.getActorType(), h.getActorName()),
                    h.getDetail(), h.getRefType(), h.getRefId(), h.getCreatedAt()));
        }

        List<SellerApplicationHistory> approvals = applicationHistoryRepository
                .findBySellerIdOrderByCreatedAtAsc(seller.getId()).stream()
                .filter(h -> h.getNewStatus() == SellerStatus.APPROVED)
                .toList();
        List<ChangeRequest> requests = changeRequestRepository.findByMarket_IdOrderByRequestedAtDesc(market.getId());

        Set<Long> operatorIds = new HashSet<>();
        approvals.forEach(h -> operatorIds.add(h.getProcessedBy()));
        requests.forEach(r -> operatorIds.add(r.getProcessedBy()));
        Map<Long, String> names = operatorNames(operatorIds);

        if (seller.getCreatedAt() != null) {
            items.add(new Item(BrandAdminEventType.SIGNED_UP, BrandAdminActorType.BRAND, BrandAdminActorType.BRAND.getLabel(),
                    null, null, null, seller.getCreatedAt()));
        }
        for (SellerApplicationHistory approval : approvals) {
            items.add(new Item(BrandAdminEventType.ONBOARDING_APPROVED, BrandAdminActorType.ADMIN,
                    names.getOrDefault(approval.getProcessedBy(), DEFAULT_OPERATOR), null, null, null,
                    approval.getCreatedAt()));
        }
        for (ChangeRequest request : requests) {
            if (request.getStatus() == ChangeRequestStatus.CANCELED) {
                continue;
            }
            String label = typeLabel(request.getType());
            items.add(new Item(BrandAdminEventType.CHANGE_REQUEST_RECEIVED, BrandAdminActorType.BRAND,
                    BrandAdminActorType.BRAND.getLabel(), label, "BRAND_CHANGE_REQUEST", request.getId(),
                    request.getRequestedAt()));
            if (request.getProcessedAt() != null && (request.getStatus() == ChangeRequestStatus.APPROVED
                    || request.getStatus() == ChangeRequestStatus.REJECTED)) {
                items.add(new Item(request.getStatus() == ChangeRequestStatus.APPROVED
                        ? BrandAdminEventType.CHANGE_REQUEST_APPROVED : BrandAdminEventType.CHANGE_REQUEST_REJECTED,
                        BrandAdminActorType.ADMIN, names.getOrDefault(request.getProcessedBy(), DEFAULT_OPERATOR),
                        label, "BRAND_CHANGE_REQUEST", request.getId(), request.getProcessedAt()));
            }
        }
        items.sort(Comparator.comparing(Item::occurredAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return items;
    }

    /** 입점 승인일 — 마지막 승인 이력 · 없으면 셀러 처리일. */
    public LocalDateTime approvedAt(Seller seller) {
        return applicationHistoryRepository.findBySellerIdOrderByCreatedAtAsc(seller.getId()).stream()
                .filter(h -> h.getNewStatus() == SellerStatus.APPROVED)
                .map(SellerApplicationHistory::getCreatedAt)
                .filter(Objects::nonNull)
                .reduce((first, second) -> second)
                .orElse(seller.getProcessedAt());
    }

    /** 브랜드 행만 들어온다(market 조건 조회) — 인플루언서 유형이 들어오면 조회 조건이 틀린 것이다(16 설계서 6-1). */
    public static String typeLabel(ChangeRequestType type) {
        return switch (type) {
            case BUSINESS_INFO -> "사업자 정보 변경 요청";
            case SETTLEMENT_ACCOUNT -> "정산 계좌 변경 요청";
            case TO_BUSINESS, TO_INDIVIDUAL -> throw new IllegalStateException("브랜드 이력에 인플루언서 요청 유형: " + type);
        };
    }

    private Map<Long, String> operatorNames(Set<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        List<Long> filtered = ids.stream().filter(Objects::nonNull).toList();
        if (filtered.isEmpty()) {
            return names;
        }
        sellerRepository.findAllById(filtered).forEach(s ->
                names.put(s.getId(), s.getName() == null ? DEFAULT_OPERATOR : s.getName()));
        return names;
    }

    private static String actorName(BrandAdminActorType type, String snapshot) {
        if (snapshot != null) {
            return snapshot;
        }
        return type.getLabel();
    }
}
