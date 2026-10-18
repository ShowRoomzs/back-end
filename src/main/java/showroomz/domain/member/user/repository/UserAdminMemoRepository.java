package showroomz.domain.member.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.member.user.entity.UserAdminMemo;

import java.util.Optional;

public interface UserAdminMemoRepository extends JpaRepository<UserAdminMemo, Long> {

    Page<UserAdminMemo> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    Optional<UserAdminMemo> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
}
