package showroomz.api.admin.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import showroomz.api.common.attachment.service.MessageThreadRetentionPurgeService;
import showroomz.domain.message.entity.Message;
import showroomz.domain.message.repository.MessageRepository;
import showroomz.domain.message.type.ParticipantType;
import showroomz.domain.message.type.ThreadStatus;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 소통 스레드 보존 기간 파기(수정요청 C · 파트너 약관 제12조③) — 연결 해제 · 탈퇴 + 1년이 지난 쌍 스레드의 메시지를 지우고 스레드는 파기
 * 시각만 남긴다. 연결 중이거나 해제 1년 전이면 대상이 아니다.
 */
@DisplayName("소통 스레드 — 연결 해제 + 1년 파기")
class MessageThreadRetentionPurgeIntegrationTest extends AdminContractTestSupport {

    @Autowired private MessageThreadRetentionPurgeService purgeService;
    @Autowired private MessageRepository messages;

    @Test
    @DisplayName("연결 중 · 해제 1년 전은 그대로 · 해제 1년 경과면 메시지 삭제 · 스레드 파기 시각 · 휴면 · 미리보기 비움 · 두 번째는 대상 아님")
    void purgesOneYearAfterDisconnect() {
        messages.save(Message.create(thread, ParticipantType.SELLER, brand.seller().getId(), "c-1", "안녕하세요"));
        messages.save(Message.create(thread, ParticipantType.CREATOR, creator.getId(), "c-2", "반갑습니다"));
        LocalDateTime now = LocalDateTime.now();

        assertThat(purgeService.findIdsToPurge(now, 100)).doesNotContain(thread.getId());

        jdbc.update("UPDATE connection SET status = 'DISCONNECTED', disconnected_at = ? WHERE connection_id = "
                + "(SELECT connection_id FROM message_thread WHERE thread_id = ?)", now.minusYears(1).plusDays(1), thread.getId());
        assertThat(purgeService.findIdsToPurge(now, 100)).doesNotContain(thread.getId());

        jdbc.update("UPDATE connection SET disconnected_at = ? WHERE connection_id = "
                + "(SELECT connection_id FROM message_thread WHERE thread_id = ?)", now.minusYears(1).minusDays(1), thread.getId());
        assertThat(purgeService.findIdsToPurge(now, 100)).contains(thread.getId());
        assertThat(purgeService.purge(thread.getId(), now)).isTrue();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM message WHERE thread_id = ?", Long.class, thread.getId()))
                .isZero();
        var purged = threads.findById(thread.getId()).orElseThrow();
        assertThat(purged.getPurgedAt()).isNotNull();
        assertThat(purged.getStatus()).isEqualTo(ThreadStatus.DORMANT);
        assertThat(purged.getLastMessagePreview()).isNull();
        assertThat(purgeService.findIdsToPurge(now, 100)).doesNotContain(thread.getId());
        assertThat(purgeService.purge(thread.getId(), now)).isFalse();
    }
}
