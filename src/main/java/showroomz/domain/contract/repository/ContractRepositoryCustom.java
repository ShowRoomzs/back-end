package showroomz.domain.contract.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.ContractSortType;
import showroomz.domain.contract.type.CreatorContractSortType;
import showroomz.domain.contract.type.CreatorContractTab;
import showroomz.domain.contract.type.ContractTab;

import java.time.LocalDate;

public interface ContractRepositoryCustom {

    /**
     * 파트너 계약 목록(설계서 4-1).
     *
     * @param keyword 공구명 + 계약 상대 표시명
     * @param startDate/endDate 공구 기간이 걸치는 구간
     */
    Page<Contract> searchForSeller(Long marketId,
                                   ContractTab tab,
                                   String keyword,
                                   LocalDate startDate,
                                   LocalDate endDate,
                                   ContractSortType sort,
                                   Pageable pageable);

    /**
     * 스튜디오 계약 목록(§27 설계서 2-1).
     *
     * <p><b>가시성 판정이 이 메서드 안에 있다</b> — 판정 없는 {@code findByCreatorId} 류를
     * 두지 않는 것이 설계서 1-1의 요구다. 한 곳만 빠져도 브랜드가 보내지도 않은 계약이 나간다.
     *
     * @param keyword 공구명 + 브랜드명(market 조인 — 계약에 브랜드명 스냅샷 컬럼이 없다)
     */
    Page<Contract> searchForCreator(Long creatorId,
                                    CreatorContractTab tab,
                                    String keyword,
                                    CreatorContractSortType sort,
                                    Pageable pageable);

    /**
     * 상세 헤더의 [‹ 이전] [다음 ›] — 현재 목록의 정렬·필터 <b>안에서의</b> 이웃이다(설계서 2-5).
     *
     * <p>목록 전체를 다시 조회하지 않는다. 정렬 키 기준으로 앞뒤 1건씩만 뽑는다.
     *
     * @param forward true면 목록 순서상 <b>뒤</b>(다음), false면 <b>앞</b>(이전)
     * @return 이웃 계약 ID · 없으면 {@code null}
     */
    Long findNeighborForCreator(Long creatorId,
                                Contract current,
                                CreatorContractTab tab,
                                String keyword,
                                CreatorContractSortType sort,
                                boolean forward);
}
