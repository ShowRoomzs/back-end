package showroomz.domain.contract.service.port;

import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractResendRequest;
import showroomz.domain.contract.type.ContractActorType;

/**
 * 운영팀 채널 포트(36 설계 5-1) — 계약의 요청과 처리 결과를 당사자의 운영팀 1:1 채널에 시스템 카드로 잇는다.
 * 공구의 스레드 포트처럼 메시지 도메인이 구현한다. 계약은 카드가 어떻게 저장되는지 모른다.
 *
 * <p>등록은 호출자의 트랜잭션에 합류한다 — 요청(또는 취소)과 카드가 함께 커밋되거나 함께 사라진다.
 * 채널이 없는 회원이면 그 자리에서 만든다 — 채널이 없다는 이유로 계약 흐름을 실패시키지 않는다.
 */
public interface ContractChannelGateway {

    /** [서명 안내 다시 받기] — <b>요청자의</b> 운영팀 채널에 요청 카드를 등록한다. */
    ChannelCard postResendRequestCard(Contract contract, ContractResendRequest request);

    /**
     * 운영자 [계약 취소] 결과 — 요청이 들어온 쪽({@code SELLER} = 브랜드 · {@code CREATOR} = 인플루언서)의
     * 운영팀 채널에 결과 카드를 등록한다. 채널은 회원당 하나라 요청자가 정해지면 유도된다.
     */
    ChannelCard postAdminCanceledCard(Contract contract, ContractActorType requesterType, Long operatorId);

    record ChannelCard(Long threadId, Long messageId) {
    }
}
