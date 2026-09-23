package showroomz.domain.contract.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.type.ContractSortType;
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
}
