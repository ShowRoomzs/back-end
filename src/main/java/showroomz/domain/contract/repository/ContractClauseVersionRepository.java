package showroomz.domain.contract.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import showroomz.domain.contract.entity.ContractClauseVersion;
import showroomz.domain.contract.type.ContractClauseVersionStatus;

import java.util.Optional;

public interface ContractClauseVersionRepository extends JpaRepository<ContractClauseVersion, Long> {

    /** 작성 화면·조항 모달은 항상 현행 버전을 읽는다. 계약은 검토 요청 시 이 버전을 고정한다(설계서 1-6). */
    Optional<ContractClauseVersion> findFirstByStatusOrderByEffectiveDateDescIdDesc(ContractClauseVersionStatus status);
}
