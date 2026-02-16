package showroomz.domain.notice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.notice.entity.Notice;

import java.util.Optional;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    Page<Notice> findAllByIsVisibleTrue(Pageable pageable);

    Optional<Notice> findByIdAndIsVisibleTrue(Long id);
}
