package showroomz.api.admin.brand.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import showroomz.api.admin.brand.dto.AdminBrandDto;
import showroomz.api.admin.common.AdminOperatorResolver;
import showroomz.domain.member.brand.service.BrandSanctionService;
import showroomz.domain.member.brand.service.BrandWithdrawalService;

import java.time.LocalDateTime;

/**
 * 어드민 브랜드 조치 4종(52 설계서 3 · 4절) — 정지 · 해제 · 탈퇴 승인 · 반려. 운영자 이름을 확인해(ADMIN 이 아니면 401) 도메인 서비스에
 * 넘긴다. 각 조치는 도메인 서비스의 트랜잭션 하나다 — 응답은 커밋 뒤 다시 읽은 레일이다.
 */
@Service
@RequiredArgsConstructor
public class AdminBrandCommandService {

    private final AdminOperatorResolver operatorResolver;
    private final BrandSanctionService sanctionService;
    private final BrandWithdrawalService withdrawalService;
    private final AdminBrandQueryService queries;

    public AdminBrandDto.Rail suspend(Long marketId, AdminBrandDto.SuspendRequest request, Long operatorId) {
        String operatorName = operatorResolver.operatorName(operatorId);
        sanctionService.suspend(marketId, request.reason(), request.reasonDetail(), operatorId, operatorName,
                LocalDateTime.now());
        return queries.rail(marketId);
    }

    public AdminBrandDto.Rail release(Long marketId, AdminBrandDto.ReleaseRequest request, Long operatorId) {
        String operatorName = operatorResolver.operatorName(operatorId);
        sanctionService.release(marketId, request == null ? null : request.reason(),
                request == null ? null : request.note(), operatorId, operatorName, LocalDateTime.now());
        return queries.rail(marketId);
    }

    public AdminBrandDto.Rail approveWithdrawal(Long marketId, Long requestId, Long operatorId) {
        String operatorName = operatorResolver.operatorName(operatorId);
        withdrawalService.approve(marketId, requestId, operatorId, operatorName, LocalDateTime.now());
        return queries.rail(marketId);
    }

    public AdminBrandDto.Rail rejectWithdrawal(Long marketId, Long requestId, AdminBrandDto.WithdrawalRejectRequest request,
                                               Long operatorId) {
        String operatorName = operatorResolver.operatorName(operatorId);
        withdrawalService.reject(marketId, requestId, request.reason(), request.note(), operatorId, operatorName,
                LocalDateTime.now());
        return queries.rail(marketId);
    }
}
