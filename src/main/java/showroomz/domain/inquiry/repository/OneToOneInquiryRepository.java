package showroomz.domain.inquiry.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.inquiry.entity.OneToOneInquiry;
import showroomz.domain.inquiry.type.InquiryStatus;
import showroomz.domain.member.user.entity.Users;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OneToOneInquiryRepository extends JpaRepository<OneToOneInquiry, Long> {

    long countByUser_Id(Long userId);

    /** 어드민 주문 상세 「문의 N건」 — 이 주문을 가리키는 1:1 문의(37 설계서 8절 #11). */
    long countByOrderId(Long orderId);

    /** 문의 내역 탭 건수 — 상태별 (C12 [답변 대기만] 필터·탭 배지) */
    long countByUser_IdAndStatus(Long userId, InquiryStatus status);

    // 내 문의 내역 조회 (페이징)
    Page<OneToOneInquiry> findByUserOrderByCreatedAtDesc(Users user, Pageable pageable);

    /** 내 문의 내역 조회 — [답변 대기만] 필터 적용 (C12) */
    Page<OneToOneInquiry> findByUserAndStatusOrderByCreatedAtDesc(Users user, InquiryStatus status, Pageable pageable);

    /** 이 정지에 답변 전 소명 문의 — 동시 1건 규칙(51-6 설계서 5-1) · 앱 시트의 「기존 문의로 이동」. */
    Optional<OneToOneInquiry> findFirstBySanctionIdAndStatusOrderByIdDesc(Long sanctionId, InquiryStatus status);

    /** 이 정지의 소명 문의 — 어드민 해제 모달의 「소명 문의 번호」(§25-4). */
    List<OneToOneInquiry> findBySanctionIdOrderByCreatedAtDescIdDesc(Long sanctionId);

    /** 처리 이력 파생 행 「정지 소명 문의 접수」(7-2). */
    List<OneToOneInquiry> findByUser_IdAndSanctionIdIsNotNull(Long userId);

    /** GNB 배지용 미답변(접수) 건수 (§17-7) */
    long countByStatus(InquiryStatus status);

    /** 문의번호 INQ-YYYYMMDD-NNN의 일자 내 순번 (§17-3) */
    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndIdLessThanEqual(
            LocalDateTime dayStart, LocalDateTime nextDayStart, Long inquiryId);
}
