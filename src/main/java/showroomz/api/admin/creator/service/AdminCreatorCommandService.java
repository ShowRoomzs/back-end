package showroomz.api.admin.creator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.common.AdminOperatorResolver;
import showroomz.api.admin.creator.dto.AdminCreatorDto.Detail;
import showroomz.api.admin.creator.dto.AdminCreatorDto.RejectWithdrawalRequest;
import showroomz.api.admin.creator.dto.AdminCreatorDto.ReleaseRequest;
import showroomz.api.admin.creator.dto.AdminCreatorDto.SuspendRequest;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.service.CreatorSanctionService;
import showroomz.domain.member.creator.service.CreatorWithdrawalService;

import java.time.LocalDateTime;

/**
 * 어드민 인플루언서 조치 4종(§51 · 53 설계서 3-9 · 3-10 · 4절) — 정지 · 해제 · 탈퇴 승인 · 반려. 운영자 이름을 확인해(ADMIN 이 아니면
 * 401) 도메인 서비스({@link CreatorSanctionService} · {@link CreatorWithdrawalService})에 넘기고, 같은 트랜잭션에서 갱신된 상세 전체를
 * 조립한다. 규칙 · 순서 · 카드 · 알림은 도메인 서비스가 든다 — 브랜드 {@code AdminBrandCommandService}와 같은 계층 구조다(정합 계획서 R1-6).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminCreatorCommandService {

    private final CreatorSanctionService sanctionService;
    private final CreatorWithdrawalService withdrawalService;
    private final AdminCreatorQueryService queryService;
    private final AdminOperatorResolver operators;

    public Detail suspend(Long creatorId, Long operatorId, SuspendRequest request) {
        String operatorName = operators.operatorName(operatorId);
        LocalDateTime now = LocalDateTime.now();
        sanctionService.suspend(creatorId, request.reason(), request.reasonDetail(), operatorId, operatorName, now);
        return queryService.assemble(queryService.load(creatorId), operatorId, now);
    }

    public Detail release(Long creatorId, Long operatorId, ReleaseRequest request) {
        String operatorName = operators.operatorName(operatorId);
        LocalDateTime now = LocalDateTime.now();
        sanctionService.release(creatorId, request == null ? null : request.reason(),
                request == null ? null : request.note(), operatorId, operatorName, now);
        return queryService.assemble(queryService.load(creatorId), operatorId, now);
    }

    public Detail approveWithdrawal(Long creatorId, Long requestId, Long operatorId) {
        String operatorName = operators.operatorName(operatorId);
        LocalDateTime now = LocalDateTime.now();
        Creator creator = withdrawalService.approve(creatorId, requestId, operatorId, operatorName, now);
        return queryService.assemble(creator, operatorId, now);
    }

    public Detail rejectWithdrawal(Long creatorId, Long requestId, Long operatorId, RejectWithdrawalRequest body) {
        String operatorName = operators.operatorName(operatorId);
        LocalDateTime now = LocalDateTime.now();
        Creator creator = withdrawalService.reject(creatorId, requestId, body.reason(), body.note(), operatorId,
                operatorName, now);
        return queryService.assemble(creator, operatorId, now);
    }
}
