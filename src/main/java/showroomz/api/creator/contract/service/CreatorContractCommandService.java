package showroomz.api.creator.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.creator.contract.dto.CreatorContractDeclineRequest;
import showroomz.api.creator.contract.dto.CreatorContractDetailResponse;
import showroomz.api.creator.contract.dto.CreatorContractResendRequestResponse;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractResendRequest;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.contract.repository.ContractResendRequestRepository;
import showroomz.domain.contract.service.ContractHistoryRecorder;
import showroomz.domain.contract.service.ContractNotifier;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractEventType;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * 스튜디오 계약 쓰기 — <b>두 가지뿐</b>이다(§27 설계서 0-1 · 5).
 *
 * <p>조건 수정 · 서명 · 새 계약 작성 · 계약 취소 · 지급 완료 기록 · 재작성은
 * <b>엔드포인트를 만들지 않는다.</b> §27-4가 「화면 어디에도 조건 입력 필드를 두지 않는다」로
 * 못박았고, 없는 화면에 대응하는 API가 열려 있으면 <b>화면이 보장한 제약을 서버가 배신</b>한다.
 *
 * <p>{@code POST /contracts/{id}/sign}도 없다 — 서명은 모두싸인이 메일·문자로 보낸 링크에서
 * 일어나고 그건 우리 시스템 밖이다(§25-3 #1). S11(내 서명 완료 모달)은 서버가 띄우는 것이 아니라
 * FE가 서명 링크 복귀 시점에 자체적으로 띄운다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CreatorContractCommandService {

    private final CreatorContractReader reader;
    private final ContractRepository contractRepository;
    private final ContractResendRequestRepository resendRequestRepository;
    private final CreatorContractDetailAssembler detailAssembler;
    private final ContractHistoryRecorder historyRecorder;
    private final ContractNotifier contractNotifier;

    /**
     * 거절(S5) — {@code SIGNING} → {@code DECLINED} · {@code close_actor_type = CREATOR}.
     *
     * <p>상태 판정을 읽어서 하지 않고 <b>조건부 UPDATE의 WHERE에 건다</b>(설계서 5-1).
     * {@code creator_signed_at IS NULL}이 거기 있어야 하는 이유는, 내가 거절 모달을 열어 둔 사이에
     * 운영자가 내 서명을 체크할 수 있기 때문이다. 서명한 계약이 거절로 종결되면
     * <b>모두싸인에는 내 서명이 남고 우리 시스템은 거절인</b> 상태가 된다.
     *
     * <p>0행일 때 409를 두 갈래로 나눈다 — 이미 서명했는지, 상태가 바뀐 것인지가
     * 인플루언서에게 다른 사실이고 화면 문구도 달라야 한다.
     *
     * <p><b>연결을 끊지 않는다.</b> 시안 S7 「거절은 이 계약만 종결시킵니다 — 연결이 끊기지는
     * 않습니다」. 이 서비스는 {@code Connection}을 쓰기 대상으로 참조하지 않는다.
     */
    public CreatorContractDetailResponse decline(String creatorEmail, Long contractId,
                                                 CreatorContractDeclineRequest request) {
        Creator creator = reader.resolveCreator(creatorEmail);
        Contract contract = reader.requireReceived(creator.getId(), contractId);

        // 메모는 선택이다 — ETC여도 필수로 걸지 않는다(설계서 미결 #2, 시안을 따른다).
        String memo = request.memo() == null || request.memo().isBlank() ? null : request.memo().trim();
        LocalDateTime now = LocalDateTime.now();

        int changed = contractRepository.declineByCreator(
                contract.getId(), creator.getId(), request.reasonCode().name(), memo, now);
        if (changed == 0) {
            throw new BusinessException(declineConflictOf(contract));
        }

        // 조건부 UPDATE가 clear까지 했으므로 갱신된 행을 다시 읽어 응답을 조립한다.
        Contract declined = reader.requireReceived(creator.getId(), contractId);

        // 사유는 브랜드에게 그대로 전달된다(시안 S5 고지) — 서버가 가공하지 않는다.
        historyRecorder.recordByCreator(
                declined, ContractEventType.DECLINED, request.reasonCode().getLabel(), now);
        contractNotifier.notifySeller(declined, ContractEventType.DECLINED.name());
        contractNotifier.notifyAdmin(declined, ContractEventType.DECLINED.name());

        return detailAssembler.assemble(declined, new CreatorContractDetailResponse.Navigation(null, null));
    }

    /**
     * 0행의 원인을 읽은 스냅샷으로 되짚는다. 정확한 원인은 UPDATE 시점의 행에 있지만,
     * 두 사유 모두 「지금은 거절할 수 없다」이고 인플루언서에게 보이는 차이는 문구뿐이다.
     */
    private ErrorCode declineConflictOf(Contract contract) {
        if (contract.getCreatorSignedAt() != null) {
            return ErrorCode.CONTRACT_ALREADY_SIGNED;
        }
        // CONCLUSION_PENDING은 양측 서명이 끝난 계약이라 거절 대상이 아니다.
        return ErrorCode.CONTRACT_DECLINE_NOT_ALLOWED;
    }

    /**
     * [서명 안내 다시 받기](설계서 5-2) — <b>상태는 변하지 않는다.</b>
     * {@code contract_resend_request} 한 행이 생길 뿐이다.
     *
     * <p>허용 조건은 {@code permissions.canRequestResend}와 같다 — 내 서명이 아직 남아 있을 때만이다.
     * 이미 서명한 사람에게 재발송할 이유가 없다.
     *
     * <p><b>중복 억제</b>: 미처리 요청이 이미 있으면 새 행을 만들지 않고 기존 요청을 200으로
     * 돌려준다 — 어드민 큐에 같은 계약이 여러 줄 쌓이는 것을 막는다. 횟수 제한 정책은
     * §28-8 D #7로 미정이라 만들지 않는다.
     *
     * <p><b>실제 재발송은 우리가 하지 않는다.</b> 운영자가 모두싸인에서 한다 —
     * 응답 문구가 「재발송했습니다」가 되면 인플루언서가 오지 않을 메일을 기다린다.
     */
    public CreatorContractResendRequestResponse requestResend(String creatorEmail, Long contractId) {
        Creator creator = reader.resolveCreator(creatorEmail);
        Contract contract = reader.requireReceived(creator.getId(), contractId);

        if (contract.getStatus() != ContractStatus.SIGNING || contract.getCreatorSignedAt() != null) {
            throw new BusinessException(ErrorCode.CONTRACT_RESEND_NOT_ALLOWED);
        }

        return resendRequestRepository
                .findFirstByContractIdAndHandledAtIsNullOrderByRequestedAtDesc(contract.getId())
                .map(existing -> new CreatorContractResendRequestResponse(
                        existing.getId(), existing.getRequestedAt(), true))
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    ContractResendRequest saved = resendRequestRepository.save(ContractResendRequest.of(
                            contract, ContractActorType.CREATOR, creator.getId(), now));
                    historyRecorder.recordByCreator(
                            contract, ContractEventType.RESEND_REQUESTED, null, now);
                    contractNotifier.notifyAdmin(contract, ContractEventType.RESEND_REQUESTED.name());
                    return new CreatorContractResendRequestResponse(saved.getId(), saved.getRequestedAt(), false);
                });
    }
}
