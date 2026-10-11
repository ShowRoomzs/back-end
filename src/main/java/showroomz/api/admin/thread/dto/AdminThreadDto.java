package showroomz.api.admin.thread.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import showroomz.api.admin.thread.type.AdminChannelMemberStatus;
import showroomz.api.admin.thread.type.AdminChannelTab;
import showroomz.api.common.attachment.dto.AttachmentSummary;
import showroomz.api.common.thread.dto.MessageCardResponse;
import showroomz.domain.member.creator.type.CreatorBusinessType;
import showroomz.domain.message.type.MessageCardActionState;
import showroomz.domain.message.type.MessageCardTone;
import showroomz.domain.message.type.MessageCardType;
import showroomz.domain.message.type.MessageType;
import showroomz.domain.message.type.ParticipantType;
import showroomz.domain.message.type.ThreadKind;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 어드민 소통 스레드 응답(36 설계 3 · 4절).
 *
 * <p><b>운영자 이름이 실리는 유일한 응답이다.</b> 파트너센터 · 스튜디오의 메시지 DTO와 합치지 않는다 —
 * 합치는 순간 운영자 개인 식별자가 상대 서피스로 새어 나갈 길이 생긴다(11-3 #3).
 */
public final class AdminThreadDto {
    private AdminThreadDto() {
    }

    @Schema(description = "운영팀 1:1 채널 목록 항목")
    public record ChannelListItem(
            @Schema(description = "스레드 ID", example = "55") Long threadId,
            @Schema(description = "탭", example = "BRAND") AdminChannelTab tab,
            @Schema(description = "브랜드명 또는 쇼룸명", example = "무드코스메틱") String name,
            @Schema(description = "브랜드 대표 이미지 또는 인플루언서 프로필 이미지", nullable = true) String imageUrl,
            @Schema(description = "회원번호 — 브랜드 BRD-{마켓 ID} · 인플루언서 INF-{크리에이터 ID}", example = "BRD-1017")
            String memberNo,
            @Schema(description = "회원 ID — 회원 상세 링크용(브랜드 = 마켓 ID · 인플루언서 = 크리에이터 ID)", example = "1017")
            Long memberId,
            @Schema(description = "[브랜드] 판매 담당자 이름 — 보조 줄 「담당 {이름}」", example = "이현", nullable = true)
            String managerName,
            @Schema(description = "[인플루언서] 사업자 여부 — BUSINESS(사업자) · INDIVIDUAL(개인)", example = "BUSINESS",
                    nullable = true) CreatorBusinessType businessType,
            @Schema(description = "회원 상태", example = "ACTIVE") AdminChannelMemberStatus memberStatus,
            @Schema(description = "최근 메시지 미리보기 — 카드가 마지막이면 카드 제목", nullable = true)
            String lastMessagePreview,
            @Schema(description = "마지막 발신이 운영팀인가 — true면 미리보기에 「운영팀: 」을 붙인다", example = "false")
            boolean lastMessageByOperator,
            @Schema(description = "최근 메시지 시각 — 메시지가 없는 채널은 null", nullable = true) LocalDateTime lastMessageAt,
            @Schema(description = "운영팀 기준 안 읽은 수 — 어느 운영자가 읽든 함께 줄어든다", example = "1") long unreadCount,
            @Schema(description = "메시지를 보낼 수 있는가 — 탈퇴 회원의 채널은 false(열람만)", example = "true")
            boolean writable,
            @Schema(description = "[이슈 탭] 정산 조정 협의 — tab = ISSUE 일 때만", nullable = true) IssueListItem issue) {
    }

    @Schema(description = "이슈 탭 행 — 정산 조정 협의(44 이슈 스레드 설계서 4-1). 행의 name = 공구명")
    public record IssueListItem(
            @Schema(example = "77") Long adjustmentId,
            @Schema(description = "OPEN · AGREED · EXPIRED", example = "OPEN") String status,
            @Schema(example = "협의 중") String statusLabel,
            @Schema(description = "운영자 기준 배지 — 응답 대기 · 합의 · 금액 변경 · 기한 만료 · 원래 금액", example = "응답 대기")
            String badgeLabel,
            @Schema(description = "INFO · SUCCESS · NEUTRAL", example = "INFO") String badgeTone,
            @Schema(description = "「{브랜드명} × {쇼룸명} · 정산 조정 요청」", example = "벨라코스 × 소연_쇼룸 · 정산 조정 요청")
            String subtitle,
            Long settlementId,
            @Schema(example = "STL-2609-006") String settlementNumber,
            Long groupBuyId,
            Long marketId,
            @Schema(example = "벨라코스") String brandName,
            Long creatorId,
            @Schema(example = "소연_쇼룸") String showroomName,
            @Schema(description = "합의 기한", example = "2026-09-17T23:59:59") LocalDateTime deadlineAt,
            @Schema(description = "D-N — 종결 뒤 null", nullable = true) Integer remainingBusinessDays) {
    }

    @Schema(description = "탭 배지 — 탭 숫자의 뜻이 확정되기 전이라 두 값을 함께 내린다")
    public record Summary(
            TabSummary brand,
            TabSummary influencer,
            @Schema(description = "이슈 스레드 탭 — 진행 중 · 종결 seg 숫자. 게시물·소통 GNB 배지에는 더하지 않는다") IssueTabSummary issue) {
    }

    @Schema(description = "이슈 탭 배지 — 운영팀은 참가자가 아니라 안 읽은 수 · 미처리 카드는 항상 0")
    public record IssueTabSummary(
            @Schema(example = "0") long unreadCount,
            @Schema(example = "0") long pendingCardCount,
            @Schema(description = "진행 중(OPEN) 협의 수", example = "1") long openCount,
            @Schema(description = "종결(AGREED · EXPIRED) 협의 수", example = "8") long closedCount) {
    }

    public record TabSummary(
            @Schema(description = "그 탭 전 채널의 운영팀 기준 안 읽은 메시지 수", example = "3") long unreadCount,
            @Schema(description = "운영팀 확인을 기다리는 재발송 요청 카드 수", example = "1") long pendingCardCount) {
    }

    @Schema(description = "스레드 헤더 + 접이식 정보 바")
    public record ChannelInfo(
            Long threadId,
            AdminChannelTab tab,
            @Schema(example = "무드코스메틱") String name,
            @Schema(nullable = true) String imageUrl,
            @Schema(example = "BRD-1017") String memberNo,
            @Schema(example = "1017") Long memberId,
            AdminChannelMemberStatus memberStatus,
            boolean writable,
            Profile profile,
            Progress progress,
            @Schema(description = "그 회원이 당사자인 열린 공구 3자 스레드") List<OpenIssueThread> openIssueThreads,
            @Schema(description = "[이슈 스레드] 정산 조정 패널 — tab = ISSUE 일 때만", nullable = true) IssuePanel issue,
            @Schema(description = "[브랜드–인플루언서 1:1 스레드 열람] 그 쌍 — 이슈 패널의 링크로 들어온 PAIR 스레드일 때만(tab = null)",
                    nullable = true) PairInfo pair) {
    }

    @Schema(description = "이슈 패널(44 이슈 스레드 설계서 4-3) — 정산 영향 · 이슈 정보 · 진행 단계 · 참고 링크")
    public record IssuePanel(
            Long adjustmentId,
            @Schema(description = "OPEN · AGREED · EXPIRED", example = "OPEN") String status,
            @Schema(example = "협의 중") String statusLabel,
            @Schema(example = "응답 대기") String badgeLabel,
            @Schema(example = "INFO") String badgeTone,
            IssueSettlement settlement,
            IssueGroupBuy groupBuy,
            IssueContract contract,
            IssueBrand brand,
            IssueInfluencer influencer,
            @Schema(description = "처음 요청한 쪽 — SELLER · CREATOR", example = "CREATOR") String requesterType,
            LocalDateTime openedAt,
            @Schema(description = "합의 기한 — 개설 + 10영업일 23:59:59", example = "2026-09-17T23:59:59") LocalDateTime deadlineAt,
            @Schema(description = "D-N — 종결 뒤 null", nullable = true) Integer remainingBusinessDays,
            @Schema(nullable = true) LocalDateTime closedAt,
            @Schema(description = "진행 단계 4칸 — REQUESTED · OPEN · AGREED · CLOSED") List<IssueStep> steps,
            IssueLinks links) {
    }

    @Schema(description = "정산 영향")
    public record IssueSettlement(
            Long settlementId,
            @Schema(example = "STL-2609-006") String settlementNumber,
            @Schema(description = "정산 보류 · 전액 / 보류 해제 · 금액 변경 / 보류 해제 · 금액 변경 없음", example = "정산 보류 · 전액")
            String holdLabel,
            @Schema(description = "WARNING · SUCCESS", example = "WARNING") String holdTone,
            @Schema(description = "원래 리워드(공급가)", example = "150528") long originalRewardAmount,
            @Schema(description = "최신 제안", nullable = true) IssueProposal currentProposal,
            @Schema(description = "합의 금액 — AGREED 일 때", nullable = true) Long agreedRewardAmount,
            @Schema(description = "확정 리워드 — 종결 뒤", nullable = true) Long finalRewardAmount,
            @Schema(description = "확정 리워드 기준 인플루언서 실지급 — 종결 뒤", nullable = true) Long finalCreatorNetAmount) {
    }

    public record IssueProposal(
            Long proposalId,
            int seq,
            @Schema(example = "165528") long rewardAmount,
            @Schema(description = "SELLER · CREATOR", example = "SELLER") String proposerType,
            @Schema(example = "벨라코스") String proposerName,
            @Schema(description = "답할 쪽 이름", example = "소연_쇼룸") String responderName,
            @Schema(description = "PENDING · ACCEPTED · REJECTED · COUNTERED · CLOSED", example = "PENDING") String status) {
    }

    public record IssueGroupBuy(Long groupBuyId, @Schema(example = "GB-20260814-041") String groupBuyNumber,
                                @Schema(example = "여름 수분 세럼 공구") String title) {
    }

    public record IssueContract(Long contractId, @Schema(example = "CTR-20260801-019") String contractNumber) {
    }

    public record IssueBrand(Long marketId, @Schema(example = "벨라코스") String name) {
    }

    public record IssueInfluencer(Long creatorId, @Schema(example = "소연_쇼룸") String name) {
    }

    public record IssueStep(
            @Schema(description = "REQUESTED · OPEN · AGREED · CLOSED", example = "OPEN") String key,
            @Schema(example = "협의") String label,
            @Schema(description = "DONE · CURRENT · TODO · SKIPPED(만료 — 합의 · 반영이 일어나지 않음)", example = "CURRENT") String state,
            @Schema(example = "양측 직접") String note) {
    }

    public record IssueLinks(
            @Schema(description = "그 쌍의 브랜드–인플루언서 1:1 스레드 — 열람 전용 · 끊긴 연결이면 null", nullable = true)
            Long pairThreadId,
            Long settlementId,
            Long marketId,
            Long creatorId) {
    }

    @Schema(description = "브랜드–인플루언서 1:1 스레드 열람 헤더 — 읽기 전용")
    public record PairInfo(Long marketId, @Schema(example = "벨라코스") String brandName,
                           Long creatorId, @Schema(example = "소연_쇼룸") String showroomName) {
    }

    @Schema(description = "① 정보 — 브랜드와 인플루언서가 채우는 칸이 다르다. 해당 없는 값은 null")
    public record Profile(
            @Schema(description = "[브랜드] 판매 담당자 이름", nullable = true) String managerName,
            @Schema(description = "[브랜드] 판매 담당자 연락처", nullable = true) String managerContact,
            @Schema(description = "[인플루언서] 사업자 여부", nullable = true) CreatorBusinessType businessType,
            @Schema(description = "[인플루언서] 과세 유형 — 저장 값이 없어 항상 null", nullable = true) String taxType,
            @Schema(description = "[인플루언서] 업무용 이메일", nullable = true) String businessEmail,
            @Schema(description = "[인플루언서] 인스타그램 링크", nullable = true) String instagramUrl,
            @Schema(description = "가입일", nullable = true) LocalDateTime joinedAt) {
    }

    @Schema(description = "② 진행 중 — 각 모듈의 집계값. null은 0이 아니라 「집계할 수 없음」이다(그 행을 그리지 않는다)")
    public record Progress(
            @Schema(description = "서명 진행중 계약 수(서명 진행중 + 체결 처리 대기)", nullable = true) Long contractSigning,
            @Schema(description = "체결 완료 계약 수", nullable = true) Long contractConcluded,
            @Schema(description = "진행중 공구 수", nullable = true) Long groupBuyOngoing,
            @Schema(description = "[인플루언서] 종료 공구 수", nullable = true) Long groupBuyEnded,
            @Schema(description = "[브랜드] 미정산 건수 — 정산 모듈이 없어 항상 null", nullable = true) Long unsettledCount,
            @Schema(description = "[인플루언서] 연결 브랜드 수", nullable = true) Long connectedBrandCount) {
    }

    public record OpenIssueThread(
            Long threadId,
            @Schema(description = "SETTLEMENT_ADJUSTMENT(정산 조정 — 이슈 탭으로 이동) · GROUP_BUY_ISSUE · GROUP_BUY_FULFILLMENT(폐기 이력)")
            ThreadKind kind,
            Long groupBuyId,
            @Schema(nullable = true) String groupBuyTitle) {
    }

    @Schema(description = "메시지 목록 — 최신순 커서 페이징")
    public record MessageList(
            List<MessageItem> content,
            @Schema(description = "다음 페이지 cursor(마지막 항목의 messageId) — hasNext=false면 null", nullable = true)
            Long nextCursor,
            boolean hasNext) {
    }

    @Schema(description = "메시지 — 말풍선 또는 시스템 카드")
    public record MessageItem(
            @Schema(example = "9013") Long messageId,
            @Schema(description = "TEXT(말풍선) · SYSTEM(시스템 카드)", example = "TEXT") MessageType messageType,
            @Schema(description = "보낸 쪽 — 카드는 그 카드를 생기게 한 주체", example = "ADMIN") ParticipantType senderType,
            @Schema(description = "운영팀의 말풍선인가 — 운영자 개인이 아니라 운영팀 기준이다. 카드는 항상 false", example = "true")
            boolean mine,
            @Schema(description = "상대 말풍선의 이름(브랜드명 · 쇼룸명). 운영팀 메시지는 null", nullable = true)
            String senderName,
            @Schema(description = "작성 운영자 이름 — 「운영팀 · {이름}」. 시스템 발신이면 null. 어드민에만 내려간다",
                    example = "김운영", nullable = true) String operatorName,
            @Schema(description = "자동 안내 여부 — 정해진 문구의 자동 전송. 어드민에만 보이는 태그다", example = "false")
            boolean autoNotice,
            @Schema(description = "본문 — 카드는 카드 제목, 첨부만 보낸 메시지는 null", nullable = true) String content,
            List<AttachmentSummary> attachments,
            @Schema(description = "시스템 카드 — messageType = SYSTEM일 때만", nullable = true) Card card,
            LocalDateTime createdAt) {
    }

    @Schema(description = "시스템 카드 — 절차의 기록")
    public record Card(
            @Schema(example = "CONTRACT_RESEND_REQUEST") MessageCardType cardType,
            @Schema(example = "요청 · 서명 안내 다시 받기") String title,
            @Schema(description = "운영자 조치가 남은 요청은 WARNING, 끝났거나 결과 카드는 NEUTRAL", example = "WARNING")
            MessageCardTone tone,
            @Schema(description = "계약 ID — 계약 상세 링크용", example = "41") Long contractId,
            @Schema(example = "CTR-20260813-041") String contractNumber,
            @Schema(example = "겨울 리페어 크림 공구") String groupBuyTitle,
            CardDetail detail,
            @Schema(description = "재발송 요청 카드 · 정산 조정 제안 카드의 액션 — 결과 카드는 null", nullable = true) CardAction action) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CardDetail(
            @Schema(description = "[재발송 요청] 요청자 — SELLER · CREATOR", example = "CREATOR") String requesterType,
            @Schema(description = "[재발송 요청] 요청자 이름", example = "뷰티_소연") String requesterName,
            @Schema(description = "[재발송 요청] 요청 시각") LocalDateTime requestedAt,
            @Schema(description = "[직권 취소] 사유 라벨 + 메모 — 당사자에게도 같은 문구가 나간다") String reasonLabel,
            @Schema(description = "[직권 취소] 처리 시각") LocalDateTime processedAt,
            @Schema(description = "[직권 취소] 처리 운영자 — 어드민에만 내려간다", example = "김운영") String processedByName,
            @Schema(description = "[직권 취소] 양측 통지 여부") Boolean notifiedBothParties,
            @Schema(description = "[정산 조정] 카드의 사실 — 파트너센터 · 스튜디오와 같은 스냅샷") MessageCardResponse.AdjustmentDetail adjustment) {
    }

    public record CardAction(
            @Schema(description = "RESEND_NOTICE(재발송 요청) · ADJUSTMENT_RESPOND(정산 조정 제안 — 운영자 버튼 없음)",
                    example = "RESEND_NOTICE") String type,
            @Schema(description = "PENDING(버튼 노출) · DONE(전송됨) · CLOSED(계약이 서명 단계를 벗어나 닫힘 — 버튼 없음)",
                    example = "PENDING") MessageCardActionState state,
            @Schema(description = "[재발송 완료 알림 보내기]를 누를 수 있는가", example = "true") boolean canExecute,
            @Schema(description = "알림 전송 시각", nullable = true) LocalDateTime doneAt,
            @Schema(description = "알림을 보낸 운영자 — 어드민에만 내려간다", example = "김운영", nullable = true)
            String doneByName,
            @Schema(description = "자동 안내 말풍선의 메시지 ID", nullable = true) Long noticeMessageId,
            @Schema(description = "[정산 조정] 결과 문구 — 동의 · 반대 · 다른 금액 제안으로 응답됨 · 기한 만료", nullable = true)
            String resultLabel,
            @Schema(description = "[정산 조정] 운영자는 답하지 않는다 — 항상 false", example = "false") boolean respondable) {
    }

    @Schema(description = "운영자 메시지 전송")
    public record SendRequest(
            @Schema(description = "FE가 발급한 멱등키(UUID 권장) — 재전송 시 같은 값으로 재요청",
                    requiredMode = Schema.RequiredMode.REQUIRED, example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
            @NotBlank @Size(max = 64) String clientMessageId,
            @Schema(description = "본문 — attachmentIds가 있으면 생략 가능", example = "확인했습니다.", nullable = true)
            String content,
            @Schema(description = "complete까지 마친 첨부 ID — 배열 순서가 표시 순서", example = "[501, 502]", nullable = true)
            List<Long> attachmentIds) {
    }

    @Schema(description = "재발송 완료 알림 전송 결과")
    public record ResendNoticeResponse(
            @Schema(description = "이미 다른 요청이 전송을 끝낸 카드였는가 — true면 이번 호출은 아무것도 보내지 않았다",
                    example = "false") boolean alreadyNotified,
            @Schema(description = "굳은 요청 카드") MessageItem card,
            @Schema(description = "자동 안내 말풍선 — 카드 도입 전 방식으로 닫힌 요청이면 null", nullable = true)
            MessageItem notice) {
    }
}
