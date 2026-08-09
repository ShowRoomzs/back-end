package showroomz.domain.message.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import showroomz.domain.message.entity.Message;
import showroomz.domain.message.entity.MessageAttachment;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.type.AttachmentStatus;
import showroomz.domain.message.type.ParticipantType;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, Long> {

    List<MessageAttachment> findAllByIdIn(List<Long> ids);

    /**
     * §4-6 고아 정리 대상 — 상태와 나이만으로 뽑는다. 조건이 인덱스
     * `idx_message_attachment_status_created (status, created_at)`(V94)와 정확히 맞는다.
     *
     * <p>메시지에 붙지 않은 것만 대상이라는 조건(`message IS NULL`)은 UPLOADED 부류에만 의미가 있지만,
     * 세 부류 모두에 걸어두면 "이미 전송된 첨부는 어떤 경우에도 지우지 않는다"가 쿼리 수준에서 보장된다.
     *
     * <p>힙이 작으므로(운영 JVM 256MB) 반드시 Pageable로 끊어서 가져간다.
     *
     * <p>`id > :afterId` 커서로 앞으로만 진행한다 — 오프셋이나 "조건에 맞는 것 다시 조회"로 돌면,
     * S3 삭제가 실패해 남겨둔 행(다음 회차 재시도 대상)을 매번 다시 집어 무한 루프가 된다.
     */
    @Query("SELECT a FROM MessageAttachment a " +
           "WHERE a.status = :status AND a.message IS NULL " +
           "AND a.createdAt < :threshold AND a.id > :afterId " +
           "ORDER BY a.id ASC")
    List<MessageAttachment> findOrphanCandidates(@Param("status") AttachmentStatus status,
                                                   @Param("threshold") LocalDateTime threshold,
                                                   @Param("afterId") Long afterId,
                                                   Pageable pageable);

    List<MessageAttachment> findByMessage_IdInOrderBySortOrderAsc(List<Long> messageIds);

    /**
     * §4-5 조건부 UPDATE — 낙관적 잠금 역할. 조건에 안 맞으면(이미 다른 메시지에 붙었거나, 소유자·스레드·상태
     * 불일치) 영향 행 수가 0이 되므로, 서비스 레이어에서 반환값을 반드시 확인해 롤백 여부를 판단해야 한다.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE MessageAttachment a SET a.message = :message, a.sortOrder = :sortOrder " +
           "WHERE a.id = :id AND a.message IS NULL AND a.status = :uploadedStatus " +
           "AND a.thread = :thread AND a.uploaderType = :uploaderType AND a.uploaderId = :uploaderId")
    int attachToMessage(@Param("id") Long id,
                         @Param("message") Message message,
                         @Param("sortOrder") int sortOrder,
                         @Param("thread") MessageThread thread,
                         @Param("uploaderType") ParticipantType uploaderType,
                         @Param("uploaderId") Long uploaderId,
                         @Param("uploadedStatus") AttachmentStatus uploadedStatus);
}
