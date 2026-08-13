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

    /** 문의 내역 탭 건수 — 상태별 (C12 [답변 대기만] 필터·탭 배지) */
    long countByUser_IdAndStatus(Long userId, InquiryStatus status);

    // 내 문의 내역 조회 (페이징)
    Page<OneToOneInquiry> findByUserOrderByCreatedAtDesc(Users user, Pageable pageable);

    /** 내 문의 내역 조회 — [답변 대기만] 필터 적용 (C12) */
    Page<OneToOneInquiry> findByUserAndStatusOrderByCreatedAtDesc(Users user, InquiryStatus status, Pageable pageable);

    /** GNB 배지용 미답변(접수) 건수 (§17-7) */
    long countByStatus(InquiryStatus status);

    /** 문의번호 INQ-YYYYMMDD-NNN의 일자 내 순번 (§17-3) */
    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndIdLessThanEqual(
            LocalDateTime dayStart, LocalDateTime nextDayStart, Long inquiryId);
}
