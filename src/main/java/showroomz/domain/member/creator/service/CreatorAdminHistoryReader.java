package showroomz.domain.member.creator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.domain.changerequest.entity.ChangeRequest;
import showroomz.domain.changerequest.repository.ChangeRequestRepository;
import showroomz.domain.changerequest.type.ChangeRequestRejectReason;
import showroomz.domain.changerequest.type.ChangeRequestStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorAdminHistory;
import showroomz.domain.member.creator.entity.CreatorApplication;
import showroomz.domain.member.creator.repository.CreatorAdminHistoryRepository;
import showroomz.domain.member.creator.repository.CreatorApplicationRepository;
import showroomz.domain.member.creator.type.CreatorAdminActorType;
import showroomz.domain.member.creator.type.CreatorAdminEventType;
import showroomz.domain.member.creator.type.CreatorApplicationStatus;
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
 * 인플루언서 처리 이력 읽기(53 설계서 3-6 · 정합 계획서 R1-6 · R1-8) — 저장 이력 + 파생 사건을 합쳐 최신순으로 돌려준다. 브랜드
 * {@code BrandAdminHistoryReader}와 같은 골격 · 같은 원칙이다: <b>다른 테이블에 이미 있는 사실은 다시 쓰지 않고 읽을 때 합친다.</b>
 *
 * <ul>
 *   <li>가입 신청 · 입점 승인 — {@code creator_application}</li>
 *   <li>사업자 구분 변경 승인 · 반려 — {@code brand_change_request}(인플루언서 요청 · 처리자 · 처리 시각). 정합 계획서 결정 D-2 로 저장을 멈췄다
 *   — 이전에 저장된 행({@code ref_type = CHANGE_REQUEST})은 지우지 않고 읽기에서 뺀다(같은 사건이 두 줄이 되지 않게).</li>
 * </ul>
 *
 * <p>한 인플루언서의 사건은 많아야 수백 건이라 메모리 병합이다(원천마다 페이지하면 순서가 어긋난다). 레일의 「최신 N건」도 같은 리더다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorAdminHistoryReader {

    private static final String DEFAULT_OPERATOR = "운영자";

    private final CreatorAdminHistoryRepository histories;
    private final CreatorApplicationRepository applications;
    private final ChangeRequestRepository changeRequests;
    private final SellerRepository sellers;

    public record Item(Long historyId, CreatorAdminEventType type, CreatorAdminActorType actorType, String actorName,
                       String detail, String refType, Long refId, LocalDateTime occurredAt) {
    }

    public List<Item> read(Creator creator) {
        List<Item> items = new ArrayList<>();
        for (CreatorAdminHistory h : histories.findByCreatorIdOrderByCreatedAtDescIdDesc(creator.getId())) {
            if (CreatorAdminHistoryRecorder.REF_CHANGE_REQUEST.equals(h.getRefType())) {
                continue;
            }
            items.add(new Item(h.getId(), h.getEventType(), h.getActorType(), actorName(h.getActorType(), h.getActorName()),
                    h.getDetail(), h.getRefType(), h.getRefId(), h.getCreatedAt()));
        }

        List<CreatorApplication> applied = applications.findByUser_IdOrderByCreatedAtAscIdAsc(creator.getUser().getId());
        List<ChangeRequest> processed = changeRequests.findProcessedForCreator(creator.getId());
        Set<Long> operatorIds = new HashSet<>();
        processed.forEach(r -> operatorIds.add(r.getProcessedBy()));
        Set<String> operatorEmails = new HashSet<>();
        applied.forEach(a -> operatorEmails.add(a.getProcessorEmail()));
        Map<Long, String> namesById = namesById(operatorIds);
        Map<String, String> namesByEmail = namesByEmail(operatorEmails);

        for (CreatorApplication application : applied) {
            items.add(derived(CreatorAdminEventType.SIGNED_UP, CreatorAdminActorType.CREATOR, null, null, null, null,
                    application.getCreatedAt()));
            if (application.getStatus() == CreatorApplicationStatus.APPROVED && application.getProcessedAt() != null) {
                items.add(derived(CreatorAdminEventType.ONBOARDING_APPROVED, CreatorAdminActorType.ADMIN,
                        namesByEmail.get(application.getProcessorEmail()), null, null, null, application.getProcessedAt()));
            }
        }
        for (ChangeRequest request : processed) {
            if (request.getProcessedAt() == null) {
                continue;
            }
            boolean approved = request.getStatus() == ChangeRequestStatus.APPROVED;
            String detail = (approved ? request.getType().directionLabel() : rejectLabel(request.getRejectReason()))
                    + " · " + request.getRequestCode();
            items.add(derived(approved ? CreatorAdminEventType.BUSINESS_TYPE_CHANGE_APPROVED
                            : CreatorAdminEventType.BUSINESS_TYPE_CHANGE_REJECTED, CreatorAdminActorType.ADMIN,
                    namesById.get(request.getProcessedBy()), detail, CreatorAdminHistoryRecorder.REF_CHANGE_REQUEST,
                    request.getId(), request.getProcessedAt()));
        }

        items.sort(Comparator.comparing(Item::occurredAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Item::historyId, Comparator.nullsLast(Comparator.reverseOrder())));
        return items;
    }

    private static Item derived(CreatorAdminEventType type, CreatorAdminActorType actor, String actorName, String detail,
                                String refType, Long refId, LocalDateTime at) {
        return new Item(null, type, actor, actorName(actor, actorName), detail, refType, refId, at);
    }

    private static String actorName(CreatorAdminActorType actorType, String snapshot) {
        return switch (actorType) {
            case ADMIN -> snapshot == null || snapshot.isBlank() ? DEFAULT_OPERATOR : snapshot;
            case CREATOR, SYSTEM -> actorType.getLabel();
        };
    }

    private static String rejectLabel(String code) {
        if (code == null) {
            return "반려";
        }
        try {
            return ChangeRequestRejectReason.valueOf(code).getDescription();
        } catch (IllegalArgumentException e) {
            return code;
        }
    }

    private Map<Long, String> namesById(Set<Long> ids) {
        ids.remove(null);
        Map<Long, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            sellers.findAllById(ids).forEach(s -> names.put(s.getId(), s.getName()));
        }
        return names;
    }

    private Map<String, String> namesByEmail(Set<String> emails) {
        Map<String, String> names = new HashMap<>();
        emails.stream().filter(Objects::nonNull).forEach(email -> sellers.findByEmail(email)
                .map(Seller::getName)
                .filter(name -> !name.isBlank())
                .ifPresent(name -> names.put(email, name)));
        return names;
    }
}
