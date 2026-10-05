package showroomz.api.admin.thread.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import showroomz.api.admin.thread.type.AdminChannelMemberStatus;
import showroomz.api.admin.thread.type.AdminChannelTab;
import showroomz.api.common.attachment.dto.AttachmentSummary;
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
            boolean writable) {
    }

    @Schema(description = "탭 배지 — 탭 숫자의 뜻이 확정되기 전이라 두 값을 함께 내린다")
    public record Summary(
            TabSummary brand,
            TabSummary influencer,
            @Schema(description = "이슈 스레드 탭 — 추후 기획 예정이라 항상 null", nullable = true) TabSummary issue) {
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
            @Schema(description = "그 회원이 당사자인 열린 공구 3자 스레드") List<OpenIssueThread> openIssueThreads) {
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
            @Schema(description = "GROUP_BUY_ISSUE · GROUP_BUY_FULFILLMENT") ThreadKind kind,
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
            @Schema(description = "재발송 요청 카드의 액션 — 결과 카드는 null", nullable = true) CardAction action) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CardDetail(
            @Schema(description = "[재발송 요청] 요청자 — SELLER · CREATOR", example = "CREATOR") String requesterType,
            @Schema(description = "[재발송 요청] 요청자 이름", example = "뷰티_소연") String requesterName,
            @Schema(description = "[재발송 요청] 요청 시각") LocalDateTime requestedAt,
            @Schema(description = "[직권 취소] 사유 라벨 + 메모 — 당사자에게도 같은 문구가 나간다") String reasonLabel,
            @Schema(description = "[직권 취소] 처리 시각") LocalDateTime processedAt,
            @Schema(description = "[직권 취소] 처리 운영자 — 어드민에만 내려간다", example = "김운영") String processedByName,
            @Schema(description = "[직권 취소] 양측 통지 여부") Boolean notifiedBothParties) {
    }

    public record CardAction(
            @Schema(example = "RESEND_NOTICE") String type,
            @Schema(description = "PENDING(버튼 노출) · DONE(전송됨) · CLOSED(계약이 서명 단계를 벗어나 닫힘 — 버튼 없음)",
                    example = "PENDING") MessageCardActionState state,
            @Schema(description = "[재발송 완료 알림 보내기]를 누를 수 있는가", example = "true") boolean canExecute,
            @Schema(description = "알림 전송 시각", nullable = true) LocalDateTime doneAt,
            @Schema(description = "알림을 보낸 운영자 — 어드민에만 내려간다", example = "김운영", nullable = true)
            String doneByName,
            @Schema(description = "자동 안내 말풍선의 메시지 ID", nullable = true) Long noticeMessageId) {
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
