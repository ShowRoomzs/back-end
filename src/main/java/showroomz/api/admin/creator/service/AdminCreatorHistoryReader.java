package showroomz.api.admin.creator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.api.admin.creator.dto.AdminCreatorDto.HistoryItem;
import showroomz.api.admin.creator.repository.AdminCreatorQueryRepository;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.entity.CreatorAdminHistory;
import showroomz.domain.member.creator.entity.CreatorApplication;
import showroomz.domain.member.creator.repository.CreatorAdminHistoryRepository;
import showroomz.domain.member.creator.type.CreatorAdminActorType;
import showroomz.domain.member.creator.type.CreatorAdminEventType;
import showroomz.domain.member.creator.type.CreatorApplicationStatus;
import showroomz.domain.member.seller.entity.Seller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 처리 이력 읽기(53 설계서 3-6) — 저장 이력 + 파생 2종(가입 신청 · 입점 승인 = {@code creator_application})을 합쳐 최신순으로 자른다.
 * 한 인플루언서의 사건은 많아야 수백 건이라 메모리 병합이다(원천마다 페이지하면 순서가 어긋난다). 레일의 「최신 3건」도 같은 리더다.
 */
@Component
@RequiredArgsConstructor
public class AdminCreatorHistoryReader {

    private static final String DEFAULT_OPERATOR = "운영자";

    private final CreatorAdminHistoryRepository histories;
    private final AdminCreatorQueryRepository queries;
    private final SellerRepository sellers;

    public List<HistoryItem> read(Creator creator) {
        List<HistoryItem> items = new ArrayList<>();
        for (CreatorAdminHistory h : histories.findByCreatorIdOrderByCreatedAtDescIdDesc(creator.getId())) {
            items.add(new HistoryItem(h.getId(), h.getEventType(), h.getEventType().getLabel(), h.getEventType().getTone(),
                    h.getDetail(), h.getActorType(), actorName(h.getActorType(), h.getActorName()), h.getRefType(),
                    h.getRefId(), h.getCreatedAt()));
        }
        for (CreatorApplication application : queries.findApplications(creator.getUser().getId())) {
            items.add(derived(CreatorAdminEventType.SIGNED_UP, CreatorAdminActorType.CREATOR, null,
                    application.getCreatedAt()));
            if (application.getStatus() == CreatorApplicationStatus.APPROVED && application.getProcessedAt() != null) {
                items.add(derived(CreatorAdminEventType.ONBOARDING_APPROVED, CreatorAdminActorType.ADMIN,
                        operatorNameByEmail(application.getProcessorEmail()), application.getProcessedAt()));
            }
        }
        items.sort(Comparator.comparing(HistoryItem::occurredAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(HistoryItem::historyId, Comparator.nullsLast(Comparator.reverseOrder())));
        return items;
    }

    private static HistoryItem derived(CreatorAdminEventType type, CreatorAdminActorType actor, String actorName,
                                       java.time.LocalDateTime at) {
        return new HistoryItem(null, type, type.getLabel(), type.getTone(), null, actor, actorName(actor, actorName),
                null, null, at);
    }

    private static String actorName(CreatorAdminActorType actorType, String snapshot) {
        return switch (actorType) {
            case ADMIN -> snapshot == null ? DEFAULT_OPERATOR : snapshot;
            case CREATOR -> CreatorAdminActorType.CREATOR.getLabel();
            case SYSTEM -> CreatorAdminActorType.SYSTEM.getLabel();
        };
    }

    private String operatorNameByEmail(String email) {
        if (email == null) {
            return DEFAULT_OPERATOR;
        }
        return sellers.findByEmail(email).map(Seller::getName).filter(name -> !name.isBlank()).orElse(DEFAULT_OPERATOR);
    }
}
