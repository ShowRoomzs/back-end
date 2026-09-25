package showroomz.domain.groupbuy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.groupbuy.entity.GroupBuyAppealAttachment;
import showroomz.domain.groupbuy.type.GroupBuyAttachmentStatus;

import java.util.List;

public interface GroupBuyAppealAttachmentRepository extends JpaRepository<GroupBuyAppealAttachment, Long> {

    List<GroupBuyAppealAttachment> findByAdminSuspensionIdOrderByIdAsc(Long adminSuspensionId);

    List<GroupBuyAppealAttachment> findByAdminSuspensionIdAndStatusOrderByIdAsc(Long adminSuspensionId,
                                                                               GroupBuyAttachmentStatus status);

    long countByAdminSuspensionIdAndStatusNot(Long adminSuspensionId, GroupBuyAttachmentStatus status);
}
