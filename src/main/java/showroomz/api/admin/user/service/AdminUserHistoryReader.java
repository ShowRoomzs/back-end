package showroomz.api.admin.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.api.admin.user.dto.AdminUserDto.HistoryItem;
import showroomz.api.admin.user.dto.AdminUserDto.HistorySummary;
import showroomz.domain.history.entity.UserStatusHistory;
import showroomz.domain.history.repository.UserStatusHistoryRepository;
import showroomz.domain.inquiry.entity.OneToOneInquiry;
import showroomz.domain.inquiry.repository.OneToOneInquiryRepository;
import showroomz.domain.member.user.entity.UserAdminHistory;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.repository.UserAdminHistoryRepository;
import showroomz.domain.member.user.service.UserAdminHistoryRecorder;
import showroomz.domain.member.user.type.UserAdminActorType;
import showroomz.domain.member.user.type.UserAdminEventType;
import showroomz.domain.member.user.type.UserStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 소비자 처리 이력 읽기(51-6 설계서 7-2) — 저장 이력 + 파생 4종을 합쳐 최신순으로 자른다. 한 회원의 사건은 많아야 수백 건이라 메모리
 * 병합이다(원천마다 페이지하면 순서가 어긋난다). 상세의 「최신 5건」도 같은 리더다.
 *
 * <p>{@code user_status_history}에서는 탈퇴 · 재가입만 읽는다 — 정지 · 해제 전이는 {@code user_admin_history}가 처리자 · 사유와 함께 이미
 * 갖고 있어 두 줄이 된다.
 */
@Component
@RequiredArgsConstructor
public class AdminUserHistoryReader {

    private static final String DEFAULT_OPERATOR = "운영자";

    private final UserAdminHistoryRepository histories;
    private final UserStatusHistoryRepository statusHistories;
    private final OneToOneInquiryRepository inquiries;

    public List<HistoryItem> read(Users user) {
        List<HistoryItem> items = new ArrayList<>();
        for (UserAdminHistory h : histories.findByUserIdOrderByCreatedAtDescIdDesc(user.getId())) {
            items.add(new HistoryItem(h.getId(), h.getEventType(), h.getEventType().getLabel(), h.getEventType().getTone(),
                    h.getDetail(), h.getActorType(), actorName(h.getActorType(), h.getActorName()), h.getRefType(),
                    h.getRefId(), h.getCreatedAt()));
        }
        items.add(derived(UserAdminEventType.SIGNED_UP, null, null, null, user.getCreatedAt()));
        for (UserStatusHistory transition : statusHistories.findByUser_IdOrderByCreatedAtDesc(user.getId())) {
            if (transition.getNewStatus() == UserStatus.WITHDRAWN) {
                items.add(derived(UserAdminEventType.WITHDRAWN, null, null, null, transition.getCreatedAt()));
            } else if (transition.getPreviousStatus() == UserStatus.WITHDRAWN) {
                String detail = transition.getNewStatus() == UserStatus.SUSPENDED ? "정지 상태로 복귀" : null;
                items.add(derived(UserAdminEventType.REJOINED, detail, null, null, transition.getCreatedAt()));
            }
        }
        for (OneToOneInquiry appeal : inquiries.findByUser_IdAndSanctionIdIsNotNull(user.getId())) {
            items.add(derived(UserAdminEventType.APPEAL_RECEIVED, null, UserAdminHistoryRecorder.REF_INQUIRY,
                    appeal.getId(), appeal.getCreatedAt()));
        }
        items.sort(Comparator.comparing(HistoryItem::occurredAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(HistoryItem::historyId, Comparator.nullsLast(Comparator.reverseOrder())));
        return items;
    }

    public HistorySummary summary(Users user, int limit) {
        List<HistoryItem> all = read(user);
        return new HistorySummary(all.size(), all.stream().limit(limit).toList());
    }

    private static HistoryItem derived(UserAdminEventType type, String detail, String refType, Long refId,
                                       LocalDateTime at) {
        return new HistoryItem(null, type, type.getLabel(), type.getTone(), detail, UserAdminActorType.CONSUMER,
                UserAdminActorType.CONSUMER.getLabel(), refType, refId, at);
    }

    private static String actorName(UserAdminActorType actorType, String snapshot) {
        return switch (actorType) {
            case ADMIN -> snapshot == null ? DEFAULT_OPERATOR : snapshot;
            case CONSUMER -> UserAdminActorType.CONSUMER.getLabel();
            case SYSTEM -> UserAdminActorType.SYSTEM.getLabel();
        };
    }
}
