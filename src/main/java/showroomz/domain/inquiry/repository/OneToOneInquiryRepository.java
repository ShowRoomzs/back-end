package showroomz.domain.inquiry.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.inquiry.entity.OneToOneInquiry;
import showroomz.domain.member.user.entity.Users;

public interface OneToOneInquiryRepository extends JpaRepository<OneToOneInquiry, Long> {

    // 내 문의 내역 조회 (페이징)
    Page<OneToOneInquiry> findByUserOrderByCreatedAtDesc(Users user, Pageable pageable);
}
