package showroomz.domain.inquiry.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.inquiry.entity.OneToOneInquiry;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.notification.service.NotificationCommand;
import showroomz.domain.notification.service.NotificationPublisher;
import showroomz.domain.notification.type.NotificationActorType;
import showroomz.domain.notification.type.NotificationImageKind;
import showroomz.domain.notification.type.NotificationTargetType;
import showroomz.domain.notification.type.NotificationType;
import showroomz.domain.order.service.BusinessDayCalculator;
import showroomz.global.utils.KstDates;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 소비자 불만 · 분쟁 처리 기한(수정요청 C · 소비자분쟁해결기준 · 전자상거래 표준약관) — 1:1 문의 접수 + 3영업일 안에 답하지 못하면 처리
 * 경과와 결과 예정일(접수 + 10영업일)을 소비자에게 알린다. 결과 통지는 답변 등록 알림이다. 계정 이용 정지 소명은 별도 절차라 뺀다.
 */
@Service
@RequiredArgsConstructor
public class InquiryHandlingDeadlineService {

    /** 처리 경과 통지 — 접수 + N영업일 미답변. */
    public static final int PROGRESS_NOTICE_BUSINESS_DAYS = 3;
    /** 결과 통지 기한 — 접수 + N영업일. */
    public static final int RESULT_DUE_BUSINESS_DAYS = 10;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM.dd");

    private final EntityManager entityManager;
    private final BusinessDayCalculator businessDayCalculator;
    private final NotificationPublisher notificationPublisher;

    public LocalDateTime progressNoticeDueAt(OneToOneInquiry inquiry) {
        return inquiry.getCreatedAt() == null ? null
                : businessDayCalculator.dueAt(inquiry.getCreatedAt(), PROGRESS_NOTICE_BUSINESS_DAYS);
    }

    public LocalDateTime resultDueAt(OneToOneInquiry inquiry) {
        return inquiry.getCreatedAt() == null ? null
                : businessDayCalculator.dueAt(inquiry.getCreatedAt(), RESULT_DUE_BUSINESS_DAYS);
    }

    /** 후보 — 미답변 ∧ 통지 전 ∧ 소명 아님 ∧ 접수가 3영업일 전일 수 있다. 최종 판정은 {@link #noticeIfDue}. */
    @Transactional(readOnly = true)
    public List<Long> findCandidateIds(LocalDateTime now, int limit) {
        LocalDateTime before = businessDayCalculator.dueElapsedCandidateBefore(now, PROGRESS_NOTICE_BUSINESS_DAYS);
        return entityManager.createQuery("SELECT i.id FROM OneToOneInquiry i WHERE i.status = :waiting "
                        + "AND i.progressNoticedAt IS NULL AND i.sanctionId IS NULL AND i.createdAt < :before "
                        + "ORDER BY i.id ASC", Long.class)
                .setParameter("waiting", InquiryStatus.WAITING)
                .setParameter("before", before)
                .setMaxResults(limit)
                .getResultList();
    }

    @Transactional
    public boolean noticeIfDue(Long inquiryId, LocalDateTime now) {
        OneToOneInquiry inquiry = entityManager.find(OneToOneInquiry.class, inquiryId);
        if (inquiry == null || inquiry.getStatus() != InquiryStatus.WAITING || inquiry.getProgressNoticedAt() != null
                || inquiry.isSanctionAppeal()) {
            return false;
        }
        LocalDateTime due = progressNoticeDueAt(inquiry);
        if (due == null || !now.isAfter(due)) {
            return false;
        }
        notificationPublisher.publish(NotificationCommand.toUser(inquiry.getUser().getId(), NotificationType.INQUIRY_IN_PROGRESS)
                .actorType(NotificationActorType.OPERATOR)
                .title("문의하신 내용을 확인하고 있어요 · " + KstDates.toKst(resultDueAt(inquiry)).format(DAY)
                        + "까지 처리 결과를 알려 드릴게요")
                .emphasis("처리 중")
                .imageKind(NotificationImageKind.ICON)
                .targetType(NotificationTargetType.INQUIRY).targetId(inquiry.getId()).targetSub("ONE_TO_ONE").build());
        inquiry.markProgressNoticed(now);
        return true;
    }
}
