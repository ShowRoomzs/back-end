package showroomz.domain.message.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.message.entity.Message;
import showroomz.domain.message.entity.MessageCardPayload;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.type.MessageCardType;
import showroomz.domain.message.type.MessageRefType;
import showroomz.domain.message.type.ParticipantType;

import java.time.LocalDateTime;

/**
 * 계정 정지 · 해제 시스템 카드(§51-4 · 52 설계서 8-1) — 회원의 <b>운영팀 채널</b>에 붙는다. 그 카드 아래 답글이 곧 소명이다(별도 소명 API 없음).
 * 계약 카드({@code MessageContractChannelGateway})와 같은 틀이다.
 *
 * <p>회원 유형에 중립이다 — 채널은 호출자가 고른다(브랜드 {@code OperatorChannelService.requireMarketChannel} · 인플루언서
 * {@code requireCreatorChannel}). 멱등 키 {@code sanction-{kind}-{sanctionId}-suspended|released}의 {@code kind}가 두 유형의 정지
 * 행 id 충돌을 막는다. 운영자 이름은 넣지 않는다 — id 만(36 설계 0-4).
 */
@Component
@RequiredArgsConstructor
public class MessageSanctionChannelGateway {

    /** 정지 행의 출처 — 멱등 키에만 쓴다. */
    public enum SanctionKind { BRAND, CREATOR }

    private final MessageThreadService messageThreadService;

    /** 「계정 정지」 카드 — 사유 문장(라벨 + 상세) · 정지 시각. @return 카드 message id */
    @Transactional
    public Long postSuspendedCard(MessageThread operatorChannel, SanctionKind kind, Long sanctionId,
                                  String reasonText, LocalDateTime suspendedAt, Long operatorId,
                                  MessageCardPayload.Sanction snapshot) {
        Message card = messageThreadService.postSystemCard(operatorChannel, ParticipantType.ADMIN, operatorId,
                key(kind, sanctionId, "suspended"), MessageCardType.ACCOUNT_SUSPENDED, MessageRefType.MEMBER_SANCTION,
                sanctionId, MessageCardPayload.sanction(reasonText, suspendedAt, operatorId, snapshot)).message();
        return card.getId();
    }

    /** 「정지 해제」 카드 — 해제 사유 · 해제 시각. @return 카드 message id */
    @Transactional
    public Long postReleasedCard(MessageThread operatorChannel, SanctionKind kind, Long sanctionId,
                                 String releaseText, LocalDateTime releasedAt, Long operatorId,
                                 MessageCardPayload.Sanction snapshot) {
        Message card = messageThreadService.postSystemCard(operatorChannel, ParticipantType.ADMIN, operatorId,
                key(kind, sanctionId, "released"), MessageCardType.ACCOUNT_SUSPENSION_RELEASED,
                MessageRefType.MEMBER_SANCTION, sanctionId,
                MessageCardPayload.sanction(releaseText, releasedAt, operatorId, snapshot)).message();
        return card.getId();
    }

    private static String key(SanctionKind kind, Long sanctionId, String suffix) {
        return "sanction-%s-%d-%s".formatted(kind.name(), sanctionId, suffix);
    }
}
