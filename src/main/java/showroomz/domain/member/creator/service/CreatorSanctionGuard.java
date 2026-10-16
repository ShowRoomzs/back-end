package showroomz.domain.member.creator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.repository.CreatorWithdrawalRequestRepository;
import showroomz.domain.member.creator.type.CreatorBlockedAction;
import showroomz.domain.member.creator.type.CreatorStatus;
import showroomz.domain.member.creator.type.CreatorWithdrawalStatus;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

/**
 * 인플루언서 정지 · 탈퇴 심사 중 제약의 <b>서버 집행</b>(§51-1 · 53 설계서 4-2). 화면의 「비활성 + 안내」는 FE 몫이고 여기는 그 행위를
 * 거절한다. 「새로 벌이는 일만 차단」 — 진행 중 공구 운영 · 소통 스레드 · 정산 조회 · 기존 게시물 수정 · 삭제 · 로그인은 막지 않는다.
 *
 * <ul>
 *   <li>정지: 새 게시물 게시 · 계약 서명(서명 요청이 나가는 순간 — 브랜드의 검토 요청 · 운영자의 검토 통과) · 공구 게시물 제출(플래그 ·
 *   §55 B-11 미정이라 기본 꺼짐)</li>
 *   <li>탈퇴 심사 중(스튜디오 S7): 새 연결 수락 · 계약 서명</li>
 * </ul>
 *
 * <p>상태 비교를 서비스 여기저기에 흩지 않도록 네 메서드만 둔다.
 */
@Component
@RequiredArgsConstructor
public class CreatorSanctionGuard {

    private final CreatorWithdrawalRequestRepository withdrawalRequests;

    /** 정지 시 공구 게시물 제출까지 막을지(§55 B-11) — 켜면 정지된 인플루언서의 준비완료 공구가 게시물 게이트에서 막힌다. */
    @Value("${member.sanction.block-group-buy-post:false}")
    private boolean blockGroupBuyPost;

    public void requireCanPublishPost(Creator creator) {
        if (creator.getStatus() == CreatorStatus.SUSPENDED) {
            throw suspended(CreatorBlockedAction.POST_PUBLISH);
        }
    }

    public void requireCanSubmitGroupBuyPost(Creator creator) {
        if (blockGroupBuyPost && creator.getStatus() == CreatorStatus.SUSPENDED) {
            throw suspended(CreatorBlockedAction.GROUP_BUY_POST_SUBMIT);
        }
    }

    /** 이 인플루언서에게 서명 요청이 나가도 되는가 — 브랜드 검토 요청 · 운영자 검토 통과가 부른다. */
    public void requireCanReceiveContract(Creator creator) {
        if (creator == null) {
            return;
        }
        if (creator.getStatus() == CreatorStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWN);
        }
        if (creator.getStatus() == CreatorStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.CREATOR_COUNTERPARTY_SUSPENDED);
        }
        if (hasPendingWithdrawal(creator)) {
            throw new BusinessException(ErrorCode.CREATOR_COUNTERPARTY_WITHDRAWAL_PENDING);
        }
    }

    public void requireCanAcceptConnection(Creator creator) {
        if (hasPendingWithdrawal(creator)) {
            throw new BusinessException(ErrorCode.CREATOR_WITHDRAWAL_PENDING,
                    "탈퇴 신청 심사 중에는 " + CreatorBlockedAction.CONNECTION_ACCEPT.getPhrase() + " 수 없습니다.");
        }
    }

    private boolean hasPendingWithdrawal(Creator creator) {
        return withdrawalRequests.existsByCreatorIdAndStatus(creator.getId(), CreatorWithdrawalStatus.PENDING);
    }

    private static BusinessException suspended(CreatorBlockedAction action) {
        return new BusinessException(ErrorCode.CREATOR_SUSPENDED,
                "계정 정지 중에는 " + action.getPhrase() + " 수 없습니다.");
    }
}
