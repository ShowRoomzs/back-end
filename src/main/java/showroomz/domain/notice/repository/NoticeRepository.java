package showroomz.domain.notice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.notice.entity.Notice;
import showroomz.domain.notice.type.NoticeStatus;

import java.util.Optional;

public interface NoticeRepository extends JpaRepository<Notice, Long>, NoticeRepositoryCustom {

    Page<Notice> findAllByStatus(NoticeStatus status, Pageable pageable);

    Optional<Notice> findByIdAndStatus(Long id, NoticeStatus status);
}
