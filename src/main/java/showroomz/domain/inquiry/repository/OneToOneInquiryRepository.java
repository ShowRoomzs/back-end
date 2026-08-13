package showroomz.domain.inquiry.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.inquiry.entity.OneToOneInquiry;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.member.user.entity.Users;

import java.time.LocalDateTime;

public interface OneToOneInquiryRepository extends JpaRepository<OneToOneInquiry, Long> {

    long countByUser_Id(Long userId);

    // 내 문의 내역 조회 (페이징)
    Page<OneToOneInquiry> findByUserOrderByCreatedAtDesc(Users user, Pageable pageable);

    /** GNB 배지용 미답변(접수) 건수 (§17-7) */
    long countByStatus(InquiryStatus status);

    /** 문의번호 INQ-YYYYMMDD-NNN의 일자 내 순번 (§17-3) */
    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndIdLessThanEqual(
            LocalDateTime dayStart, LocalDateTime nextDayStart, Long inquiryId);
}
