package showroomz.api.admin.thread.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.thread.dto.AdminThreadDto.MessageItem;
import showroomz.api.admin.thread.dto.AdminThreadDto.ResendNoticeResponse;
import showroomz.api.admin.thread.dto.AdminThreadDto.SendRequest;
import showroomz.api.common.attachment.dto.AttachmentDownloadResponse;
import showroomz.api.common.attachment.dto.AttachmentSummary;
import showroomz.api.common.attachment.dto.CompleteAttachmentRequest;
import showroomz.api.common.attachment.dto.PresignRequest;
import showroomz.api.common.attachment.dto.PresignResponse;
import showroomz.api.common.attachment.service.MessageAttachmentService;
import showroomz.domain.contract.entity.ContractResendRequest;
import showroomz.domain.contract.repository.ContractResendRequestRepository;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.message.entity.Message;
import showroomz.domain.message.entity.MessageAttachment;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageAttachmentRepository;
import showroomz.domain.message.repository.MessageRepository;
import showroomz.domain.message.service.MessageThreadService;
import showroomz.domain.message.type.MessageCardType;
import showroomz.domain.message.type.ParticipantType;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * 어드민 소통 스레드 쓰기(36 설계 3-6 ~ 3-8 · 5-2).
 *
 * <p>운영자가 카드를 직접 만드는 길은 없다 — 카드는 계약 관리의 흐름에서만 생긴다(「같은 조치를 두 화면에서 만들지 않는다」).
 * 이 화면이 카드에 하는 일은 [재발송 완료 알림 보내기] 하나다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminThreadCommandService {

    /**
     * 재발송 완료 자동 안내(§36-5) — 매번 손으로 쓰지 않고, 사람마다 문구가 달라지지 않는다.
     * 문구 확정 · 관리 위치(고정 / 설정에서 수정)는 §36-9 B-11 미결이라 상수로 둔다.
     */
    static final String RESEND_NOTICE_MESSAGE =
            "모두싸인에서 서명 안내를 다시 보내드렸습니다. 메일함(스팸함 포함)을 확인해 주세요.";
    private static final String RESEND_NOTICE_KEY = "resend-notice-%d";

    private final AdminThreadAccess access;
    private final MessageThreadService messageThreadService;
    private final MessageRepository messages;
    private final MessageAttachmentRepository attachments;
    private final MessageAttachmentService attachmentService;
    private final ContractResendRequestRepository resendRequests;
    private final AdminMessageAssembler messageAssembler;

    public record SendOutcome(MessageItem item, boolean created) {
    }

    /** 운영자 말풍선 — 작성 운영자는 내부 감사용으로 저장되고 상대에게는 「SHOWROOMZ 운영팀」으로만 나간다(§36-4). */
    public SendOutcome send(Long threadId, Long operatorId, SendRequest request) {
        access.operatorName(operatorId);
        MessageThread thread = access.requireWritableChannel(threadId);
        MessageThreadService.SendResult result = messageThreadService.sendMessage(thread, ParticipantType.ADMIN,
                operatorId, request.clientMessageId(), request.content(), request.attachmentIds());
        return new SendOutcome(messageAssembler.assembleOne(thread, result.message()), result.created());
    }

    /** 누가 열든 운영팀의 읽음 위치가 앞으로 간다(0-5). */
    public void markRead(Long threadId) {
        messageThreadService.markReadByOperatorTeam(access.requireOperatorChannel(threadId));
    }

    public PresignResponse presign(Long threadId, Long operatorId, PresignRequest request) {
        access.operatorName(operatorId);
        MessageThread thread = access.requireWritableChannel(threadId);
        return attachmentService.createPresignedUpload(thread, ParticipantType.ADMIN, operatorId, request);
    }

    public AttachmentSummary completeUpload(Long attachmentId, Long operatorId, CompleteAttachmentRequest request) {
        return attachmentService.completeUpload(ParticipantType.ADMIN, operatorId, attachmentId,
                request.getDurationSeconds());
    }

    /** 첨부가 속한 스레드가 운영팀 채널인지로 판정한다 — 상대가 보낸 첨부도 받을 수 있어야 한다. */
    @Transactional(readOnly = true)
    public AttachmentDownloadResponse download(Long attachmentId, Long operatorId) {
        MessageAttachment attachment = attachments.findById(attachmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTACHMENT_ACCESS_DENIED));
        access.requireOperatorChannel(attachment.getThread().getId());
        return attachmentService.createDownloadUrl(attachment, ParticipantType.ADMIN, operatorId);
    }

    /**
     * [재발송 완료 알림 보내기](36 설계 5-2) — <b>전송과 카드 상태 전환이 한 번에 일어난다.</b>
     *
     * <p>요청 행을 잠그고 들어간다. 운영자 둘이 동시에 눌러도 뒤쪽은 이미 처리된 행을 보고, 오류가 아니라
     * 굳은 카드 상태(누가 · 언제)를 받는다 — 화면이 같은 결과로 수렴하면 되고 운영자가 할 일이 없다.
     *
     * <p>이 버튼은 「보냈다」는 운영자의 신고다. 모두싸인 API를 쓰지 않아 실제 재발송을 검증하지 못한다.
     * 계약 상태도, 계약 이력도 바꾸지 않는다(2026.09.28 확정 — 계약 관리에 처리 기록 없음).
     */
    public ResendNoticeResponse sendResendNotice(Long threadId, Long messageId, Long operatorId) {
        access.operatorName(operatorId);
        MessageThread thread = access.requireOperatorChannel(threadId);
        Message card = messages.findById(messageId)
                .filter(m -> m.getThread().getId().equals(thread.getId()))
                .filter(m -> m.isCard() && m.getCardType() == MessageCardType.CONTRACT_RESEND_REQUEST)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_CARD_NOT_FOUND));
        ContractResendRequest request = resendRequests.findForNotice(card.getRefId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_CARD_NOT_FOUND));

        if (request.isHandled()) {
            Message notice = request.getNoticeMessageId() == null ? null
                    : messages.findById(request.getNoticeMessageId()).orElse(null);
            return new ResendNoticeResponse(true, messageAssembler.assembleOne(thread, card),
                    notice == null ? null : messageAssembler.assembleOne(thread, notice));
        }
        // 서명 단계를 벗어난 계약에는 다시 보낼 안내가 없다 — 「다시 보냈다」는 거짓 안내를 막는다.
        if (request.getContract().getStatus() != ContractStatus.SIGNING) {
            throw new BusinessException(ErrorCode.CONTRACT_STATUS_CONFLICT);
        }
        if (!AdminThreadAccess.memberStatusOf(thread).isWritable()) {
            throw new BusinessException(ErrorCode.THREAD_READ_ONLY);
        }

        Message notice = messageThreadService.sendAutoNotice(thread, ParticipantType.ADMIN, operatorId,
                RESEND_NOTICE_KEY.formatted(request.getId()), RESEND_NOTICE_MESSAGE).message();
        request.markNotified(operatorId, notice.getId(), LocalDateTime.now());
        resendRequests.flush();
        return new ResendNoticeResponse(false, messageAssembler.assembleOne(thread, card),
                messageAssembler.assembleOne(thread, notice));
    }
}
