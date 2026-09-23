package showroomz.api.admin.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.global.error.exception.*;

@Component
@RequiredArgsConstructor
public class AdminContractAccess {
    private final ContractRepository contracts;
    private final SellerRepository sellers;

    public Contract read(Long id) {
        return visible(contracts.findDetailById(id).orElseThrow(() -> new BusinessException(ErrorCode.CONTRACT_NOT_FOUND)));
    }

    public Contract lock(Long id) {
        return visible(contracts.findForAdminUpdate(id).orElseThrow(() -> new BusinessException(ErrorCode.CONTRACT_NOT_FOUND)));
    }

    private Contract visible(Contract contract) {
        if (contract.getStatus() == ContractStatus.DRAFT) throw new BusinessException(ErrorCode.CONTRACT_NOT_FOUND);
        return contract;
    }

    public String operatorName(Long operatorId) {
        if (operatorId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS);
        return sellers.findById(operatorId).filter(s -> s.getRoleType() == RoleType.ADMIN)
                .map(s -> s.getName() == null ? "운영자" : s.getName())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED_ACCESS));
    }

    public void requireConclusionPermission(Long operatorId) {
        // 최고관리자 한정 정책 확정 시 이 지점에서 역할을 추가로 검사한다.
        operatorName(operatorId);
    }

    public void requireExpiryPermission(Long operatorId) {
        operatorName(operatorId);
    }
}
