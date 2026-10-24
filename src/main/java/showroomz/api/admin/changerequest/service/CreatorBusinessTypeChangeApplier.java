package showroomz.api.admin.changerequest.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import showroomz.domain.changerequest.entity.ChangeRequest;
import showroomz.domain.changerequest.entity.ChangeRequestItem;
import showroomz.domain.changerequest.type.ChangeRequestField;
import showroomz.domain.changerequest.type.ChangeRequestRejectReason;
import showroomz.domain.changerequest.type.ChangeRequesterType;
import showroomz.domain.member.common.service.MemberNotifier;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorRepository;
import showroomz.domain.settlement.service.AfterCommit;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * 승인된 인플루언서 사업자 구분 변경을 {@link Creator}에 반영한다(§16-2a · 16 설계서 3-6 · 50 설계서 7-5 · 8-3). 정산에는 아무것도 하지
 * 않는다 — 적용 시점(「다음 정산부터」)은 정산 생성 시점의 구분 스냅샷이 만든다. 바꾸는 값 · 남기는 값은 {@code Creator} 메서드가 정한다.
 */
@Component
@RequiredArgsConstructor
public class CreatorBusinessTypeChangeApplier implements ChangeRequestApplier {

    private final CreatorRepository creatorRepository;
    private final MemberNotifier notifier;
    private final CreatorAccountChangeApplier accountChangeApplier;

    @Override
    public ChangeRequesterType requesterType() {
        return ChangeRequesterType.CREATOR;
    }

    /** 요청 행의 암호문을 읽으므로 {@code request.approve} 보다 먼저 부른다. */
    @Override
    public void apply(ChangeRequest request, LocalDateTime now) {
        if (request.getType() == showroomz.domain.changerequest.type.ChangeRequestType.CREATOR_ACCOUNT) {
            accountChangeApplier.apply(request, now);   // 정산 계좌 변경(1021 기-22)
            return;
        }
        Creator creator = creatorRepository.findForUpdate(request.getCreator().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
        if (creator.isWithdrawn()) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWN);
        }
        // 접수 뒤 다른 경로로 구분이 바뀌었다 — 전제가 다른 건을 승인하지 않는다.
        if (creator.getBusinessType() != request.getType().sourceBusinessType()) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_BUSINESS_TYPE_MISMATCH);
        }
        // [임시 가드 · 구현 계획서 S5 → S6] 토스 셀러는 사업자번호 · 유형을 바꿀 수 없다(50 설계서 8-5). 셀러가 연결된 인플루언서를 승인하면
        // 다음 셀러 동기화가 거절되어 지급이 멈춘다 — PG 재등록(토스 답변 대기)이 들어오기 전까지 막는다.
        if (creator.getPgPartnerId() != null && !creator.getPgPartnerId().isBlank()) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_PG_RESYNC_PENDING);
        }
        switch (request.getType()) {
            case TO_BUSINESS -> creator.convertToBusiness(
                    requestedValue(request, ChangeRequestField.CREATOR_BUSINESS_REG_NUMBER), request.getEvidenceFileUrl());
            case TO_INDIVIDUAL -> creator.convertToIndividual(request.getResidentNumberEnc(),
                    requestedValue(request, ChangeRequestField.CREATOR_RESIDENT_NUMBER), now);
            default -> throw new IllegalArgumentException("사업자 구분 변경 유형이 아니다: " + request.getType());
        }
        // 예금주 규칙(50 설계서 1-4) — 예금주 = 본인 인증 실명이라 개인사업자(대표자 = 본인) · 비사업자 모두 유효하다. 상호 명의 계좌를 받기
        // 전(§55 B-9)에는 위반이 생기지 않으므로 「변경 필요(T10)」로 옮기지 않는다.
        // 처리 이력의 「사업자 구분 변경 승인」은 요청 행에서 파생한다(정합 계획서 R1-8). PG 셀러 재등록은 토스 답변 대기(구현 계획서 S6).
    }

    /** 결과 알림(업무 이메일) — 커밋 뒤. */
    @Override
    public void notifyApproved(ChangeRequest request) {
        if (request.getType() == showroomz.domain.changerequest.type.ChangeRequestType.CREATOR_ACCOUNT) {
            accountChangeApplier.notifyApproved(request);
            return;
        }
        Creator creator = request.getCreator();
        Long creatorId = creator.getId();
        String businessEmail = creator.getBusinessEmail();
        String code = request.getRequestCode();
        AfterCommit.run(() -> notifier.businessTypeChangeApproved(creatorId, businessEmail, code));
    }

    /** 즉시 재요청 가능 · 결과는 스튜디오 배너(T4a · T9a)와 업무 이메일 알림이 전한다(16 설계서 3-7). */
    @Override
    public void notifyRejected(ChangeRequest request, ChangeRequestRejectReason reason, String detail) {
        if (request.getType() == showroomz.domain.changerequest.type.ChangeRequestType.CREATOR_ACCOUNT) {
            accountChangeApplier.onRejected(request, reason, LocalDateTime.now());
            return;
        }
        Creator creator = request.getCreator();
        Long creatorId = creator.getId();
        String businessEmail = creator.getBusinessEmail();
        String code = request.getRequestCode();
        String label = reason.getDescription();
        AfterCommit.run(() -> notifier.businessTypeChangeRejected(creatorId, businessEmail, code, label));
    }

    private static String requestedValue(ChangeRequest request, ChangeRequestField field) {
        return request.getItems().stream()
                .filter(item -> item.getFieldKey() == field)
                .map(ChangeRequestItem::getRequestedValue)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("요청 항목 없음: " + field));
    }
}
