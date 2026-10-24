package showroomz.domain.member.common.service;

import showroomz.domain.member.common.type.MemberType;

import java.util.List;

/**
 * 상대방 보류 · 재개 알림 포트(1017 설계서 8-7) — 정지된 당사자의 <b>상대</b>에게 「상대 계정 사정으로 체결 · 오픈이 보류 / 재개」를 알린다.
 * 정지 사실 · 사유는 문장에 넣지 않는다. 호출은 정지 서비스 트랜잭션 안이고, 구현은 커밋 뒤에 보낸다.
 */
public interface CounterpartyHoldNotifier {

    /** {@code type}/{@code memberId}가 정지됐다 — 그 당사자와 진행 중인 계약 · 오픈 전 공구의 상대에게. */
    void holdStarted(MemberType type, Long memberId);

    /** 해제 — {@code resumedContractIds}(보류가 완전히 끝난 계약)와 오픈 전 공구의 상대에게. */
    void holdResumed(MemberType type, Long memberId, List<Long> resumedContractIds);
}
