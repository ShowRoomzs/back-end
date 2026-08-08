package showroomz.domain.message.repository;

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

import java.util.List;

@Repository
public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, Long> {

    List<MessageAttachment> findAllByIdIn(List<Long> ids);

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
