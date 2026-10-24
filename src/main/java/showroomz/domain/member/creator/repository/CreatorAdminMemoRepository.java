package showroomz.domain.member.creator.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.member.creator.entity.CreatorAdminMemo;

import java.util.Optional;

public interface CreatorAdminMemoRepository extends JpaRepository<CreatorAdminMemo, Long> {

    Page<CreatorAdminMemo> findByCreatorIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long creatorId, Pageable pageable);

    Optional<CreatorAdminMemo> findByIdAndCreatorIdAndDeletedAtIsNull(Long id, Long creatorId);
}
