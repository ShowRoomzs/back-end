package showroomz.domain.contract.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import showroomz.domain.contract.entity.ContractHistory;

import java.util.List;

/**
 * append-only. 수정 메서드를 두지 않는다(설계서 1-6) —
 * 리포지토리에 경로가 없으면 실수로 이력을 고칠 수도 없다.
 *
 * <p>삭제는 계약 행이 통째로 사라지는 <b>작성중 초안 삭제</b> 하나뿐이다
 * ({@link #deleteByContractId}). 그 외에는 지우는 경로가 없다.
 */
public interface ContractHistoryRepository extends JpaRepository<ContractHistory, Long> {

    List<ContractHistory> findByContractIdOrderByOccurredAtAscIdAsc(Long contractId);

    /**
     * 작성중 초안 삭제에 딸린 정리(설계서 4-2). append-only의 <b>예외가 아니라 범위 밖</b>이다 —
     * 계약 행 자체가 사라지는 유일한 경로이고, 남겨두면 가리킬 계약이 없는 이력이 된다.
     * 이력은 계약을 FK로 참조하므로 계약만 지우면 커밋이 깨진다.
     *
     * <p>삭제는 {@code DRAFT}에서만 허용되므로(설계서 4-2) 이 경로로 사라지는 이력은
     * 아직 아무에게도 나가지 않은 브랜드 내부 기록뿐이다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ContractHistory h WHERE h.contract.id = :contractId")
    void deleteByContractId(@Param("contractId") Long contractId);

    /**
     * 스튜디오에 내리는 이력 — <b>화이트리스트 7종</b>(§27 설계서 6-4).
     *
     * <p>이벤트 목록을 파라미터로 받지 않고 쿼리에 박는다. 서비스에서 전량 조회 후 걸러내면
     * 걸러내기 전 목록이 메모리에 존재하고, 다음 사람이 디버깅용 직렬화를 한 줄 넣는 순간 유출된다.
     * 집합을 파라미터로 열어 두면 호출자가 다른 집합을 넘길 수 있다는 점도 같은 문제다.
     *
     * <p>빠진 것과 그 이유:
     * <ul>
     *   <li>{@code CREATED} · {@code REVIEW_REQUESTED} · {@code REVIEW_REQUEST_CANCELED} — 발송 전 브랜드 내부 행위</li>
     *   <li>{@code REVIEW_APPROVED} · {@code REVIEW_REJECTED} — 운영자–브랜드 간.
     *       승인 사실은 {@code SIGNATURE_SENT} 문구에 흡수된다</li>
     *   <li>{@code SIGNATURE_UPDATED} — 운영자의 저장 1회마다 쌓이는 감사 기록이다. 한쪽만 서명한 저장에도
     *       남아서 「양측 서명 완료 확인」으로 그리면 거짓이 되고, detail에 변경 내역 원문이 담긴다.
     *       스튜디오의 「양측 서명 완료 확인」은 {@code BOTH_SIGNED_CONFIRMED}다</li>
     *   <li>{@code RESEND_REQUESTED} — 브랜드가 요청한 건이 섞인다</li>
     *   <li>{@code FIXED_FEE_PAID} — 브랜드의 기록 행위. 상세의 {@code paymentState}로만 보인다</li>
     *   <li>{@code GROUP_BUY_CREATED} · 문서 이벤트 — 브랜드·운영자 소관</li>
     * </ul>
     */
    @Query("SELECT h FROM ContractHistory h WHERE h.contract.id = :contractId AND h.eventType IN ("
            + "  showroomz.domain.contract.type.ContractEventType.SIGNATURE_SENT,"
            + "  showroomz.domain.contract.type.ContractEventType.BRAND_SIGNED,"
            + "  showroomz.domain.contract.type.ContractEventType.CREATOR_SIGNED,"
            + "  showroomz.domain.contract.type.ContractEventType.BOTH_SIGNED_CONFIRMED,"
            + "  showroomz.domain.contract.type.ContractEventType.CONCLUDED,"
            + "  showroomz.domain.contract.type.ContractEventType.DECLINED,"
            + "  showroomz.domain.contract.type.ContractEventType.EXPIRED,"
            + "  showroomz.domain.contract.type.ContractEventType.CANCELED) "
            + "ORDER BY h.occurredAt ASC, h.id ASC")
    List<ContractHistory> findVisibleToCreator(@Param("contractId") Long contractId);
}
