package showroomz.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import showroomz.api.common.attachment.service.MessageAttachmentService;
import showroomz.api.common.attachment.service.MessageAttachmentService.PurgeChunk;
import showroomz.domain.message.type.AttachmentStatus;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * §4-6 고아 첨부 정리 — Presigned 직접 업로드 구조에서는 "presign만 받고 안 올린 것", "올렸는데
 * 전송하지 않은 것", "위조 판정으로 REJECTED된 것"이 아무도 지우지 않으면 S3에 무한히 쌓인다.
 *
 * <p>배포가 단일 인스턴스(docker-compose의 app 서비스 1개)라 ShedLock 같은 분산 락은 두지 않는다.
 * 다중 인스턴스로 가면 이 전제가 깨지므로 그때 락을 붙여야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageAttachmentCleanupScheduler {

    /** presign만 발급받고 업로드 통지가 없는 행 — 업로드 자체가 실패했거나 사용자가 이탈한 경우. */
    private static final Duration PENDING_TTL = Duration.ofHours(24);
    /** 업로드는 끝났지만 메시지에 붙지 않은 행 — 첨부만 올려두고 전송하지 않은 경우. */
    private static final Duration UNSENT_TTL = Duration.ofDays(7);
    /** HeadObject 불일치로 걸러진 행 — 반복 위조 계정 추적 근거로 잠시 남겼다가 지운다(§4-2). */
    private static final Duration REJECTED_RETENTION = Duration.ofDays(30);

    private static final int CHUNK_SIZE = 200;
    /** 1회 실행 상한 — 운영 JVM 힙이 256MB라 한 번에 무한정 훑지 않는다. 남은 건 다음 회차가 처리한다. */
    private static final int MAX_SCANNED_PER_RUN = 1000;

    private final MessageAttachmentService messageAttachmentService;

    /**
     * `zone`을 명시하는 이유 — application.yml의 `time-zone: UTC`는 Jackson 직렬화 설정이라
     * 스케줄러 표현식에는 적용되지 않는다. 명시하지 않으면 JVM 기본 시간대에 좌우된다.
     */
    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void cleanUpOrphanAttachments() {
        LocalDateTime now = LocalDateTime.now();
        int deleted = 0;
        deleted += purge(AttachmentStatus.PENDING, now.minus(PENDING_TTL));
        deleted += purge(AttachmentStatus.UPLOADED, now.minus(UNSENT_TTL));
        deleted += purge(AttachmentStatus.REJECTED, now.minus(REJECTED_RETENTION));

        if (deleted > 0) {
            log.info("고아 첨부 정리 완료 - 삭제 {}건", deleted);
        }
    }

    /**
     * id 커서로 앞으로만 진행한다 — S3 삭제 실패로 남겨둔 행을 다시 집으면 같은 회차에서 무한 반복된다.
     */
    private int purge(AttachmentStatus status, LocalDateTime threshold) {
        long cursor = 0L;
        int scanned = 0;
        int deleted = 0;

        while (scanned < MAX_SCANNED_PER_RUN) {
            PurgeChunk chunk = messageAttachmentService.purgeOrphanChunk(
                    status, threshold, cursor, CHUNK_SIZE);
            if (chunk.scanned() == 0) {
                break;
            }
            scanned += chunk.scanned();
            deleted += chunk.deleted();
            cursor = chunk.lastId();

            if (chunk.scanned() < CHUNK_SIZE) {
                break;
            }
        }
        return deleted;
    }
}
