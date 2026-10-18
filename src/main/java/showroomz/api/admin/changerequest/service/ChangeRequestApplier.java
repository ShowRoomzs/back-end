package showroomz.api.admin.changerequest.service;

import showroomz.domain.changerequest.entity.ChangeRequest;
import showroomz.domain.changerequest.type.ChangeRequestRejectReason;
import showroomz.domain.changerequest.type.ChangeRequesterType;

import java.time.LocalDateTime;

/**
 * 변경 요청 승인 반영 · 결과 통지의 요청자별 전략(§16-4 · 16 설계서 3-6 · 3-7 · 정합 계획서 R3-2). 전체 승인 또는 전체 반려뿐 — 부분 승인은
 * 없다. 서비스가 상태 검사 · 승인/반려 기록 · 동시 처리 충돌을 맡고, 여기서는 회원 값 반영과 통지만 다룬다.
 */
public interface ChangeRequestApplier {

    ChangeRequesterType requesterType();

    /**
     * 승인 반영 — 요청 행의 값을 회원(셀러 · 마켓 / 인플루언서)에 옮긴다. 반영 전제(브랜드명 중복 · 탈퇴 · 방향)가 깨졌으면 예외다.
     * 요청 행의 항목 · 암호문을 읽으므로 {@code request.approve} 보다 먼저 부른다.
     */
    void apply(ChangeRequest request, LocalDateTime now);

    /** 승인 결과 통지 — 승인 · 충돌 확인(flush) 뒤에 부른다. */
    void notifyApproved(ChangeRequest request);

    /** 반려 결과 통지 — 반려 · 충돌 확인 뒤에 부른다. 사유 문장은 가공 없이 싣는다(§16-5). */
    void notifyRejected(ChangeRequest request, ChangeRequestRejectReason reason, String detail);
}
