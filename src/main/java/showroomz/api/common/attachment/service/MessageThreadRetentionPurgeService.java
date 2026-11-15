package showroomz.api.common.attachment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.message.entity.MessageAttachment;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageAttachmentRepository;
import showroomz.domain.message.repository.MessageRepository;
import showroomz.domain.message.repository.MessageThreadRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 소통 스레드 보존 기간 파기(수정요청 C · 파트너 약관 제12조③) — 브랜드 · 인플루언서 연결이 해제(탈퇴 포함)된 뒤 1년이 지나면 그 쌍의
 * 연결 스레드 메시지 · 첨부(S3 객체 포함)를 지운다. 스레드 행은 연결 이력의 근거라 남기고 파기 시각만 적는다. 공구 이슈 · 정산 조정
 * 스레드는 거래 기록이라 대상이 아니다. S3 삭제가 하나라도 실패하면 그 스레드는 다음 회차에 다시 시도한다(행을 남긴다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageThreadRetentionPurgeService {

    /** 보존 기간 — 연결 해제 + N년. */
    public static final int RETENTION_YEARS = 1;

    private final MessageThreadRepository threadRepository;
    private final MessageRepository messageRepository;
    private final MessageAttachmentRepository attachmentRepository;
    private final MessageAttachmentService attachmentService;

    @Transactional(readOnly = true)
    public List<Long> findIdsToPurge(LocalDateTime now, int limit) {
        return threadRepository.findIdsToPurge(now.minusYears(RETENTION_YEARS), PageRequest.of(0, limit));
    }

    @Transactional
    public boolean purge(Long threadId, LocalDateTime now) {
        MessageThread thread = threadRepository.findById(threadId).orElse(null);
        if (thread == null || thread.getPurgedAt() != null) {
            return false;
        }
        List<MessageAttachment> attachments = attachmentRepository.findByThread_Id(threadId);
        for (MessageAttachment attachment : attachments) {
            if (!attachmentService.deleteObject(attachment.getS3Key())) {
                log.warn("소통 스레드 파기 보류 — 첨부 객체 삭제 실패 - threadId: {}, attachmentId: {}", threadId,
                        attachment.getId());
                return false;
            }
        }
        attachmentRepository.deleteAll(attachments);
        attachmentRepository.flush();
        int messages = messageRepository.deleteByThreadId(threadId);
        MessageThread reloaded = threadRepository.findById(threadId).orElseThrow();
        reloaded.markPurged(now);
        log.info("소통 스레드 파기 - threadId: {}, 메시지 {}건 · 첨부 {}건", threadId, messages, attachments.size());
        return true;
    }
}
