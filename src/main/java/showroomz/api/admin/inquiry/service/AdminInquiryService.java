package showroomz.api.admin.inquiry.service;

import showroomz.domain.notification.type.NotificationType;
import showroomz.domain.notification.type.NotificationTargetType;
import showroomz.domain.notification.type.NotificationImageKind;
import showroomz.domain.notification.type.NotificationActorType;
import showroomz.domain.notification.service.NotificationPublisher;
import showroomz.domain.notification.service.NotificationCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.inquiry.InquiryElapsedFormatter;
import showroomz.api.admin.inquiry.dto.AdminInquiryDto;
import showroomz.api.admin.inquiry.repository.AdminInquiryQueryRepository;
import showroomz.api.admin.inquiry.type.AdminInquiryStatusFilter;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.domain.inquiry.entity.OneToOneInquiry;
import showroomz.domain.inquiry.repository.OneToOneInquiryRepository;
import showroomz.domain.inquiry.support.OneToOneInquiryNumber;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.cs.type.CsCategory;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.domain.member.user.entity.Users;
import showroomz.global.dto.PaginationInfo;
import showroomz.global.config.properties.AdminProperties;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminInquiryService {

    private static final String UNKNOWN_OPERATOR = "운영자";

    private final OneToOneInquiryRepository inquiryRepository;
    /** 답변 기한 — 시안(어드민 00 · 21) 24시간. 홈 「기한 초과」와 같은 값이다(1017 설계서 6-0 ⑤). */
    private final AdminProperties adminProperties;
    private final NotificationPublisher notificationPublisher;
    private final AdminInquiryQueryRepository inquiryQueryRepository;
    private final SellerRepository sellerRepository;

    /** @param userId 작성한 소비자로 좁힌다 — 소비자 상세에서 넘어온 진입(56 C1). 탭 건수도 같은 소비자 기준이다 */
    public AdminInquiryDto.ListResponse getList(AdminInquiryStatusFilter statusFilter, CsCategory type,
                                                String keyword, Long userId, Pageable pageable) {
        String normalizedKeyword = normalize(keyword);
        Page<OneToOneInquiry> page = inquiryQueryRepository.search(statusFilter, type, normalizedKeyword, userId,
                pageable);

        LocalDateTime now = LocalDateTime.now();
        List<AdminInquiryDto.ListItem> content = page.getContent().stream()
                .map(inquiry -> toListItem(inquiry, now))
                .toList();

        return AdminInquiryDto.ListResponse.builder()
                .content(content)
                .pageInfo(new PaginationInfo(page))
                .statusCounts(buildStatusCounts(type, normalizedKeyword, userId))
                .build();
    }

    public AdminInquiryDto.SummaryResponse getSummary() {
        return AdminInquiryDto.SummaryResponse.builder()
                .unansweredCount(inquiryRepository.countByStatus(InquiryStatus.WAITING))
                .build();
    }

    public List<AdminInquiryDto.TypeOption> getTypeOptions() {
        return Arrays.stream(CsCategory.values())
                .map(type -> AdminInquiryDto.TypeOption.builder()
                        .code(type)
                        .label(type.getDescription())
                        .build())
                .toList();
    }

    public AdminInquiryDto.DetailResponse getDetail(Long inquiryId, AdminInquiryStatusFilter statusFilter,
                                                    CsCategory type, String keyword, Long userId) {
        OneToOneInquiry inquiry = getInquiry(inquiryId);
        Users user = inquiry.getUser();
        LocalDateTime now = LocalDateTime.now();

        List<Long> orderedIds = inquiryQueryRepository.findOrderedIds(statusFilter, type, normalize(keyword), userId);
        int index = orderedIds.indexOf(inquiryId);
        Long prevId = index > 0 ? orderedIds.get(index - 1) : null;
        Long nextId = (index >= 0 && index < orderedIds.size() - 1) ? orderedIds.get(index + 1) : null;

        String operatorName = inquiry.isAnswered() ? resolveOperatorName(inquiry.getAnsweredBy()) : null;

        return AdminInquiryDto.DetailResponse.builder()
                .inquiryId(inquiry.getId())
                .inquiryNumber(inquiryNumber(inquiry))
                .type(inquiry.getType())
                .typeName(inquiry.typeName())
                .status(inquiry.getStatus())
                .slaExceeded(isSlaExceeded(inquiry, now))
                .userId(user.getId())
                .userName(writerName(user))
                .orderId(inquiry.getOrderId())
                .claimId(inquiry.getClaimId())
                .sanctionAppeal(inquiry.isSanctionAppeal())
                .sanctionId(inquiry.getSanctionId())
                .refundTaskId(inquiry.getRefundTaskId())
                .createdAt(inquiry.getCreatedAt())
                .answeredAt(inquiry.getAnsweredAt())
                .elapsedText(elapsedText(inquiry, now))
                .elapsedLabel(inquiry.isAnswered() ? "응답 소요" : "미답변 경과")
                .operatorName(operatorName)
                .thread(buildThread(inquiry, operatorName))
                .history(buildHistory(inquiry, operatorName))
                .prevInquiryId(prevId)
                .nextInquiryId(nextId)
                .build();
    }

    /** 답변 등록 (§17-4) — 1회만 가능하며 등록 즉시 답변완료로 전환된다. */
    @Transactional
    public AdminInquiryDto.AnswerResponse registerAnswer(Long inquiryId, Long operatorId,
                                                         AdminInquiryDto.AnswerRequest request) {
        OneToOneInquiry inquiry = getInquiry(inquiryId);

        if (inquiry.isAnswered()) {
            throw new BusinessException(ErrorCode.INQUIRY_ALREADY_ANSWERED);
        }

        inquiry.registerAnswer(request.getContent().trim(), operatorId);
        // 소비자 알림 「문의하신 내용에 답변이 등록됐어요」(C11) — 1:1 문의 상세로 간다.
        notificationPublisher.publish(NotificationCommand.toUser(inquiry.getUser().getId(), NotificationType.INQUIRY_ANSWERED)
                .actorType(NotificationActorType.OPERATOR).title("문의하신 내용에 답변이 등록됐어요").emphasis("답변")
                .imageKind(NotificationImageKind.ICON)
                .targetType(NotificationTargetType.INQUIRY).targetId(inquiry.getId()).targetSub("ONE_TO_ONE").build());

        return AdminInquiryDto.AnswerResponse.builder()
                .inquiryId(inquiry.getId())
                .inquiryNumber(inquiryNumber(inquiry))
                .status(inquiry.getStatus())
                .answeredAt(inquiry.getAnsweredAt())
                .operatorName(resolveOperatorName(operatorId))
                .unansweredCount(inquiryRepository.countByStatus(InquiryStatus.WAITING))
                .build();
    }

    private AdminInquiryDto.ListItem toListItem(OneToOneInquiry inquiry, LocalDateTime now) {
        return AdminInquiryDto.ListItem.builder()
                .inquiryId(inquiry.getId())
                .type(inquiry.getType())
                .typeName(inquiry.typeName())
                .content(inquiry.getContent())
                .writerName(writerName(inquiry.getUser()))
                .createdAt(inquiry.getCreatedAt())
                .answeredAt(inquiry.getAnsweredAt())
                .elapsedText(elapsedText(inquiry, now))
                .slaExceeded(isSlaExceeded(inquiry, now))
                .status(inquiry.getStatus())
                .sanctionAppeal(inquiry.isSanctionAppeal())
                .build();
    }

    private AdminInquiryDto.StatusCounts buildStatusCounts(CsCategory type, String keyword, Long userId) {
        Map<InquiryStatus, Long> counts = inquiryQueryRepository.countByStatus(type, keyword, userId);
        long waiting = counts.getOrDefault(InquiryStatus.WAITING, 0L);
        long answered = counts.getOrDefault(InquiryStatus.ANSWERED, 0L);

        return AdminInquiryDto.StatusCounts.builder()
                .waiting(waiting)
                .answered(answered)
                .all(waiting + answered)
                .build();
    }

    private List<AdminInquiryDto.ThreadMessage> buildThread(OneToOneInquiry inquiry, String operatorName) {
        List<AdminInquiryDto.ThreadMessage> thread = new ArrayList<>();
        thread.add(AdminInquiryDto.ThreadMessage.builder()
                .role("USER")
                .authorName(writerName(inquiry.getUser()))
                .sentAt(inquiry.getCreatedAt())
                .content(inquiry.getContent())
                .imageUrls(List.copyOf(inquiry.getImageUrls()))
                .build());

        if (inquiry.isAnswered()) {
            thread.add(AdminInquiryDto.ThreadMessage.builder()
                    .role("OPERATOR")
                    .authorName(operatorName)
                    .sentAt(inquiry.getAnsweredAt())
                    .content(inquiry.getAnswerContent())
                    .imageUrls(List.of())
                    .build());
        }
        return thread;
    }

    /** 처리 이력 — 시간 역순 (§17-3) */
    private List<AdminInquiryDto.HistoryEvent> buildHistory(OneToOneInquiry inquiry, String operatorName) {
        List<AdminInquiryDto.HistoryEvent> history = new ArrayList<>();

        if (inquiry.isAnswered()) {
            history.add(AdminInquiryDto.HistoryEvent.builder()
                    .event("ANSWERED")
                    .occurredAt(inquiry.getAnsweredAt())
                    .actorLabel("운영자(" + operatorName + ")")
                    .build());
        }

        history.add(AdminInquiryDto.HistoryEvent.builder()
                .event("RECEIVED")
                .occurredAt(inquiry.getCreatedAt())
                .actorLabel("소비자(" + writerName(inquiry.getUser()) + ")")
                .build());

        return history;
    }

    /** 경과는 모든 건에 값이 있다 — 미답변이면 현재까지, 답변 건이면 접수→답변 소요 (§17-6) */
    private String elapsedText(OneToOneInquiry inquiry, LocalDateTime now) {
        LocalDateTime end = inquiry.isAnswered() ? inquiry.getAnsweredAt() : now;
        return InquiryElapsedFormatter.format(inquiry.getCreatedAt(), end);
    }

    private boolean isSlaExceeded(OneToOneInquiry inquiry, LocalDateTime now) {
        if (inquiry.isAnswered()) {
            return false;
        }
        return Duration.between(inquiry.getCreatedAt(), now)
                .compareTo(Duration.ofHours(adminProperties.getInquirySlaHours())) > 0;
    }

    /** INQ-YYYYMMDD-NNN — 규칙은 {@link OneToOneInquiryNumber} 한 곳(소비자 상세 「최근 문의」와 같은 번호 · 55 계획서 M3-2) */
    private String inquiryNumber(OneToOneInquiry inquiry) {
        return OneToOneInquiryNumber.of(inquiry, inquiryRepository);
    }

    private String resolveOperatorName(Long operatorId) {
        if (operatorId == null) {
            return UNKNOWN_OPERATOR;
        }
        return sellerRepository.findById(operatorId).map(Seller::getName).orElse(UNKNOWN_OPERATOR);
    }

    private String writerName(Users user) {
        if (user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        return user.getNickname();
    }

    private String normalize(String keyword) {
        return (keyword == null || keyword.isBlank()) ? null : keyword.trim();
    }

    private OneToOneInquiry getInquiry(Long inquiryId) {
        return inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_DATA));
    }
}
