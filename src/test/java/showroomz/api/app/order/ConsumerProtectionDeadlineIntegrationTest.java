package showroomz.api.app.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import showroomz.domain.inquiry.service.InquiryHandlingDeadlineService;
import showroomz.domain.member.user.service.MarketingConsentReconfirmService;
import showroomz.domain.notification.entity.Notification;
import showroomz.domain.notification.repository.NotificationRepository;
import showroomz.domain.notification.type.NotificationRecipientType;
import showroomz.domain.notification.type.NotificationType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 수정요청 C — 마케팅 수신 동의 2년 재확인 통지 · 1:1 문의 처리 경과 통지(접수 + 3영업일 미답변 · 결과 예정 + 10영업일).
 */
@DisplayName("소비자 보호 기한 — 마케팅 동의 2년 재확인 · 문의 처리 경과 통지")
class ConsumerProtectionDeadlineIntegrationTest extends OrderPaymentTestSupport {

    @Autowired private MarketingConsentReconfirmService reconfirmService;
    @Autowired private InquiryHandlingDeadlineService deadlineService;
    @Autowired private NotificationRepository notificationRepository;

    @Test
    @DisplayName("마케팅 — 동의 2년 전이면 대상 아님 · 지나면 통지 1회 · 통지일부터 다시 2년 · 미동의는 대상 아님")
    void marketingReconfirm() {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("UPDATE users SET marketing_agree = TRUE, marketing_agree_changed_at = ? WHERE user_id = ?",
                now.minusYears(2).plusDays(1), consumer.getId());
        assertThat(reconfirmService.findIdsToNotice(now, 100)).doesNotContain(consumer.getId());
        assertThat(reconfirmService.notice(consumer.getId(), now)).isFalse();

        jdbc.update("UPDATE users SET marketing_agree_changed_at = ? WHERE user_id = ?", now.minusYears(2).minusDays(1),
                consumer.getId());
        assertThat(reconfirmService.findIdsToNotice(now, 100)).contains(consumer.getId());
        assertThat(reconfirmService.notice(consumer.getId(), now)).isTrue();
        assertThat(userRows(NotificationType.MARKETING_CONSENT_RECONFIRM)).singleElement()
                .satisfies(n -> assertThat(n.getTitle()).contains("광고성 정보 수신에 동의하셨어요"));
        assertThat(reconfirmService.findIdsToNotice(now.plusDays(1), 100)).doesNotContain(consumer.getId());
        assertThat(reconfirmService.findIdsToNotice(now.plusYears(2).plusDays(1), 100)).contains(consumer.getId());

        jdbc.update("UPDATE users SET marketing_agree = FALSE WHERE user_id = ?", consumer.getId());
        assertThat(reconfirmService.findIdsToNotice(now.plusYears(3), 100)).doesNotContain(consumer.getId());
    }

    @Test
    @DisplayName("문의 — 접수 + 3영업일 전은 그대로 · 지나면 경과 통지 1회(결과 예정일 포함) · 답변된 문의는 대상 아님")
    void inquiryProgressNotice() throws Exception {
        mockMvc.perform(post("/v1/user/inquiries").header(HttpHeaders.AUTHORIZATION, consumerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("type", "DELIVERY", "content", "배송이 아직 안 왔어요"))))
                .andExpect(status().isCreated());
        Long inquiryId = jdbc.queryForObject("SELECT MAX(inquiry_id) FROM one_to_one_inquiry WHERE user_id = ?",
                Long.class, consumer.getId());
        LocalDateTime createdAt = jdbc.queryForObject("SELECT created_at FROM one_to_one_inquiry WHERE inquiry_id = ?",
                LocalDateTime.class, inquiryId);

        // 영업일 3일은 달력으로 최소 3일 · 최대 열흘 남짓이다.
        assertThat(deadlineService.noticeIfDue(inquiryId, createdAt.plusDays(2))).isFalse();
        LocalDateTime later = createdAt.plusDays(20);
        assertThat(deadlineService.findCandidateIds(later, 100)).contains(inquiryId);
        assertThat(deadlineService.noticeIfDue(inquiryId, later)).isTrue();
        assertThat(deadlineService.noticeIfDue(inquiryId, later)).isFalse();
        List<Notification> rows = userRows(NotificationType.INQUIRY_IN_PROGRESS);
        assertThat(rows).singleElement().satisfies(n -> assertThat(n.getTitle()).contains("처리 결과를 알려 드릴게요"));
        assertThat(deadlineService.findCandidateIds(later, 100)).doesNotContain(inquiryId);
    }

    private List<Notification> userRows(NotificationType type) {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getRecipientType() == NotificationRecipientType.USER && n.getType() == type
                        && n.getRecipientId().equals(consumer.getId())).toList();
    }
}
