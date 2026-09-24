package showroomz.api.creator.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.contract.service.ContractDocumentStorage;
import showroomz.api.creator.contract.dto.CreatorContractDetailResponse;
import showroomz.api.creator.contract.type.CreatorFixedFeePaymentState;
import showroomz.api.creator.contract.type.CreatorSettlementTiming;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.connection.type.ConnectionStatus;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractDocument;
import showroomz.domain.contract.entity.ContractHistory;
import showroomz.domain.contract.entity.ContractItem;
import showroomz.domain.contract.repository.ContractHistoryRepository;
import showroomz.domain.contract.service.PartyContractDocuments;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractCloseReasonLabels;
import showroomz.domain.contract.type.ContractDocumentType;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.contract.type.WithholdingType;
import showroomz.domain.message.entity.MessageThread;
import showroomz.domain.message.repository.MessageThreadRepository;
import showroomz.global.utils.RewardCalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 스튜디오 계약 상세 응답 조립.
 *
 * <p>화면 8종이 같은 응답 하나를 쓰고 분기는 FE가 값으로 고른다(설계서 4-1). 서버가 하는 일은
 * <b>무엇을 내리고 무엇을 덜어내는가</b>의 판정이다 — 필터링을 FE에 맡기지 않는다.
 * 브랜드의 내부 이력이 응답 JSON에 실려 나가면 화면에 안 그려도 <b>이미 유출된 것</b>이다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorContractDetailAssembler {

    /** 시안 「정산 조건 요약」의 고정 표시값 — 파트너와 같은 값이다. */
    private static final int PLATFORM_FEE_RATE = 2;

    private final ContractHistoryRepository contractHistoryRepository;
    private final PartyContractDocuments partyContractDocuments;
    private final ConnectionRepository connectionRepository;
    private final MessageThreadRepository messageThreadRepository;
    private final ContractDocumentStorage contractDocumentStorage;

    public CreatorContractDetailResponse assemble(Contract contract,
                                                  CreatorContractDetailResponse.Navigation navigation) {
        ContractStatus status = contract.getStatus();
        Connection connection = resolveConnection(contract);
        Long threadId = resolveThreadId(connection);
        boolean connected = connection != null && connection.getStatus() == ConnectionStatus.CONNECTED;
        List<CreatorContractDetailResponse.Document> documents = documents(contract);

        return new CreatorContractDetailResponse(
                contract.getId(),
                contract.getContractNumber(),
                contract.getTitle(),
                status,
                status.getLabel(),
                status.getTone(),
                // 받은 일시 = 서명 요청 발송 시각. 가시성 판정이 NOT NULL을 보증한다.
                contract.getSignatureRequestedAt(),
                brand(contract, threadId, connected),
                period(contract),
                stepper(contract),
                signature(contract),
                payout(contract, threadId),
                content(contract),
                items(contract),
                fixedFee(contract),
                settlement(contract),
                closure(contract),
                groupBuy(contract),
                documents,
                permissions(contract, threadId, connected, documents),
                history(contract, connection),
                navigation);
    }

    private CreatorContractDetailResponse.Brand brand(Contract contract, Long threadId, boolean connected) {
        return new CreatorContractDetailResponse.Brand(
                contract.getMarket().getId(),
                contract.getMarket().getMarketName(),
                threadId,
                connected);
    }

    private CreatorContractDetailResponse.Period period(Contract contract) {
        return new CreatorContractDetailResponse.Period(
                contract.getGroupBuyStartAt(), contract.getGroupBuyEndAt(), contract.periodDays());
    }

    /**
     * 4단 스텝퍼. 검토 블록에서 {@code reviewApprovedAt} 하나만 건너온다 —
     * 검토 요청 시각·반려 사유는 운영자가 브랜드에게만 한 지적이라 내리지 않는다(설계서 4-2).
     */
    private CreatorContractDetailResponse.Stepper stepper(Contract contract) {
        int signedCount = (contract.getBrandSignedAt() != null ? 1 : 0)
                + (contract.getCreatorSignedAt() != null ? 1 : 0);
        return new CreatorContractDetailResponse.Stepper(
                contract.getReviewApprovedAt(),
                contract.getSignatureRequestedAt(),
                signedCount,
                contract.getConcludedAt());
    }

    /**
     * 서명 카드(설계서 4-5).
     *
     * <p>{@code asOf}는 진행 중 계약에서 <b>항상</b> 값이 있어야 한다. 어드민이 서명 현황을 갱신할 때
     * {@code signature_as_of}를 필수로 받지만, 서명 요청 발송 직후 아직 한 번도 갱신하지 않은 계약은
     * NULL이다. 이때는 {@code signature_requested_at}으로 대체한다 —
     * 「발송 시점 기준, 아직 확인 전」이 사실이고, NULL을 내리면 화면이 기준 시각 줄을 통째로 못 그려
     * 「방금 서명했는데 왜 반영이 안 됐지」를 해명할 장치가 사라진다.
     */
    private CreatorContractDetailResponse.Signature signature(Contract contract) {
        boolean inProgress = contract.getStatus() == ContractStatus.SIGNING
                || contract.getStatus() == ContractStatus.CONCLUSION_PENDING;
        var asOf = contract.getSignatureAsOf();
        if (asOf == null && inProgress) {
            asOf = contract.getSignatureRequestedAt();
        }

        return new CreatorContractDetailResponse.Signature(
                contract.getSignatureDeadlineAt(),
                contract.getBrandSignedAt(),
                contract.getCreatorSignedAt(),
                asOf);
    }

    /**
     * 「내가 받는 금액」(설계서 4-3) — <b>종결 3종에서는 블록을 통째로 null로 만든다</b>(§27-6).
     *
     * <p>값을 내리고 FE가 숨기는 방식은, 숨기는 조건을 FE가 틀리면 <b>성립하지 않은 계약의 금액이
     * 「내가 받는 금액」으로 보이는</b> 사고가 된다. 노출 시점은 {@code SIGNING}부터다 —
     * 서명 전에 알아야 할 사실이라 서명 진행중 화면부터 나온다(§27-5).
     *
     * <p>선결제 관련 필드는 존재하지 않는다 — rev.6에서 플랫폼 미중개가 확정돼 테이블에도 없다.
     */
    private CreatorContractDetailResponse.Payout payout(Contract contract, Long threadId) {
        if (!contract.isObligationAlive()) {
            return null;
        }

        List<CreatorContractDetailResponse.Payout.RewardRate> rewardRates = contract.getItems().stream()
                .map(item -> new CreatorContractDetailResponse.Payout.RewardRate(
                        item.getProductName(), item.getRewardRate()))
                .toList();

        return new CreatorContractDetailResponse.Payout(
                contract.getFixedFeeAmount(),
                contract.getFixedFeeTrigger(),
                contract.getFixedFeeTrigger() == null ? null : contract.getFixedFeeTrigger().getLabel(),
                rewardRates,
                CreatorSettlementTiming.GROUP_BUY_ENDED,
                // MVP에서는 항상 false다. 상수를 필드로 내리는 이유는 DTO 주석에 적혀 있다.
                false,
                new CreatorContractDetailResponse.Payout.DisputeChannel(threadId));
    }

    /**
     * 콘텐츠 의무는 종결 3종에서도 <b>그대로 내린다</b> — payout과 반대다.
     * 카드는 남기되 {@code obligationAlive: false}로 「효력 없음」을 표기하는 것이 §27-6이다.
     */
    private CreatorContractDetailResponse.Content content(Contract contract) {
        return new CreatorContractDetailResponse.Content(
                contract.getContentFeedCount(),
                contract.getContentReelsCount(),
                contract.getContentStoryCount(),
                contract.getContentDueDate(),
                contract.getSecondaryUseAllowed(),
                contract.getSecondaryUsePeriodType(),
                contract.getSecondaryUseMonths(),
                contract.getBrandPreReview(),
                contract.getNote(),
                contract.isObligationAlive());
    }

    private List<CreatorContractDetailResponse.Item> items(Contract contract) {
        return contract.getItems().stream().map(this::toItem).toList();
    }

    private CreatorContractDetailResponse.Item toItem(ContractItem item) {
        return new CreatorContractDetailResponse.Item(
                item.getId(),
                item.getProductId(),
                item.getProductName(),
                item.getRegularPrice(),
                item.getGroupBuyPrice(),
                item.getRewardRate(),
                // 파트너와 같은 공유 유틸을 호출한다 — 자체 계산을 두지 않는다(설계서 미결 #8 확정).
                RewardCalculator.calcUnitReward(item.getGroupBuyPrice(), item.getRewardRate()),
                item.getMinQuantity());
    }

    /**
     * 고정 지급비(설계서 4-4) — 「체결완료 = <b>지급 전</b>」을 못박는 자리다.
     * {@code fixed_fee_paid_at} 원시값은 내리지 않고 상태 enum만 내린다.
     */
    private CreatorContractDetailResponse.FixedFee fixedFee(Contract contract) {
        return new CreatorContractDetailResponse.FixedFee(
                contract.getFixedFeeAmount(),
                contract.getFixedFeeTrigger(),
                contract.getFixedFeeTrigger() == null ? null : contract.getFixedFeeTrigger().getLabel(),
                paymentState(contract));
    }

    private CreatorFixedFeePaymentState paymentState(Contract contract) {
        if (!contract.isObligationAlive()) {
            // 계약이 성립하지 않아 지급 없음 — 종결 3종은 「지급 없음」으로만 표기한다(§27-5).
            return CreatorFixedFeePaymentState.NONE;
        }
        return contract.getFixedFeePaidAt() == null
                ? CreatorFixedFeePaymentState.NOT_YET
                : CreatorFixedFeePaymentState.RECORDED_BY_BRAND;
    }

    /**
     * 정산 조건. <b>실지급액은 계산하지 않는다</b>(§25-5-6) — {@code withholdingType}만 내린다.
     * 세무가 확정되기 전에 숫자를 지어내면 인플루언서가 그 숫자로 서명을 결정하게 된다.
     */
    private CreatorContractDetailResponse.Settlement settlement(Contract contract) {
        WithholdingType withholdingType = WithholdingType.from(contract.getCreator().getBusinessType());

        return new CreatorContractDetailResponse.Settlement(
                PLATFORM_FEE_RATE,
                // 자문 회신 전이라 null이다. 0으로 내리면 「수수료가 없다」는 뜻이 되어버린다.
                null,
                withholdingType,
                withholdingType == null ? null : withholdingType.getLabel());
    }

    /**
     * 종결 블록을 그대로 내린다(설계서 0-5). 세 종결이 같은 컬럼을 쓰고, 주체는 {@code actorType}이
     * 구분한다 — 화면 문구의 주체 전환(「내가」 / 「브랜드가」)은 FE가 그 값으로 고른다.
     * 만료는 사유가 없어 코드·메모가 NULL이고 라벨도 null이다.
     */
    private CreatorContractDetailResponse.Closure closure(Contract contract) {
        return new CreatorContractDetailResponse.Closure(
                contract.getClosedAt(),
                contract.getCloseActorType(),
                contract.getCloseReasonCode(),
                ContractCloseReasonLabels.labelOf(contract.getCloseReasonCode()),
                contract.getCloseReasonMemo());
    }

    private CreatorContractDetailResponse.GroupBuy groupBuy(Contract contract) {
        return new CreatorContractDetailResponse.GroupBuy(
                contract.getGroupBuyId(),
                contract.getStatus() == ContractStatus.CONCLUDED && contract.getGroupBuyId() == null);
    }

    /** 체결 전에는 계약서 생성본, 체결완료에서는 체결 문서 2종({@link PartyContractDocuments}). */
    private List<CreatorContractDetailResponse.Document> documents(Contract contract) {
        return partyContractDocuments.list(contract).stream()
                .map(document -> new CreatorContractDetailResponse.Document(
                        document.getDocumentType(),
                        document.getDocumentType().getLabel(),
                        contractDocumentStorage.download(document).downloadUrl()))
                .toList();
    }

    /**
     * 스튜디오 권한 5종(설계서 4-6).
     *
     * <p>{@code canDecline}과 {@code canRequestResend}의 조건이 같다 — 둘 다 「내 서명이 아직
     * 남아 있을 때」다. S3b에서 액션이 [스레드에서 협의하기] 하나로 줄어드는 것이 §27-2의 규칙이고,
     * 재발송도 이미 서명한 사람에게는 보낼 이유가 없다.
     *
     * <p>{@code canOpenThread}가 <b>종결 후에도 true</b>인 것이 §27-1 #5의 결론이다 —
     * 회복 경로는 [스레드에서 협의하기] 하나뿐이고 S7·S8·S9 셋 다 이 버튼만 남는다.
     */
    private CreatorContractDetailResponse.Permissions permissions(
            Contract contract, Long threadId, boolean connected,
            List<CreatorContractDetailResponse.Document> documents) {

        boolean mySignaturePending = contract.getStatus() == ContractStatus.SIGNING
                && contract.getCreatorSignedAt() == null;

        return new CreatorContractDetailResponse.Permissions(
                mySignaturePending,
                mySignaturePending,
                connected && threadId != null,
                contract.getStatus() == ContractStatus.CONCLUDED && documents.size() >= 2,
                contract.getGroupBuyId() != null);
    }

    /**
     * 이력(설계서 6-4) — 화이트리스트는 <b>쿼리 단계에서</b> 걸린다. 서비스에서 전량 조회 후
     * 걸러내면 걸러내기 전 목록이 메모리에 존재하고, 다음 사람이 디버깅용 직렬화를 한 줄 넣는
     * 순간 유출된다.
     *
     * <p>시안의 이력 카드는 <b>최신이 위</b>다. 파트너 상세가 오래된 순인 것과 반대라
     * 여기서 뒤집어 내린다 — 「맨 아래 한 줄」이라는 배치 지시가 이 순서를 전제로 한다.
     *
     * <p>「연결 성립」은 계약 이력이 아니라 {@code Connection.responded_at}에서 읽어
     * <b>맨 아래 한 줄로 합성</b>한다. {@code contract_history}에 없는 행이므로
     * {@code eventType}이 {@code null}인 유일한 항목이고 항상 마지막에 온다.
     *
     * <p>열람 기록({@code creator_viewed_at})은 이력에 <b>남기지 않는다</b>(설계서 5-3).
     * 「상대가 열람함」이 뜨면 브랜드가 인플루언서의 접속을 들여다보는 화면이 되고,
     * 시안 어디에도 그 항목이 없다.
     */
    private List<CreatorContractDetailResponse.HistoryEntry> history(Contract contract, Connection connection) {
        List<ContractHistory> recorded = contractHistoryRepository.findVisibleToCreator(contract.getId());

        List<CreatorContractDetailResponse.HistoryEntry> entries = new ArrayList<>(recorded.size() + 1);
        for (int i = recorded.size() - 1; i >= 0; i--) {
            ContractHistory entry = recorded.get(i);
            entries.add(new CreatorContractDetailResponse.HistoryEntry(
                    entry.getEventType(),
                    entry.getActorType(),
                    // 운영자 실명은 어드민 화면에만 나간다 — 스튜디오는 「운영자」로 익명 표기한다(§25-9).
                    entry.getActorType() == ContractActorType.ADMIN ? null : entry.getActorDisplayName(),
                    entry.getDetail(),
                    entry.getOccurredAt()));
        }

        if (connection != null && connection.getRespondedAt() != null) {
            entries.add(new CreatorContractDetailResponse.HistoryEntry(
                    null,
                    ContractActorType.SELLER,
                    contract.getMarket().getMarketName(),
                    null,
                    connection.getRespondedAt()));
        }
        return entries;
    }

    /**
     * {@code connection_id}는 스레드 경유로 상대가 고정된 계약에만 채워진다(§25-5-1).
     * 목록에서 시작해 상대를 직접 고른 계약은 비어 있으므로, 읽는 시점의 연결을 찾아 채운다 —
     * 그 사이 연결이 끊겼으면 {@code canOpenThread}가 false가 되는 것이 맞다.
     */
    private Connection resolveConnection(Contract contract) {
        if (contract.getConnection() != null) {
            return contract.getConnection();
        }
        return connectionRepository
                .findConnectedPair(contract.getMarket().getId(), contract.getCreator().getId())
                .orElse(null);
    }

    /** 상세 응답에 connectionId가 아니라 threadId를 내린다(설계서 8-1). */
    private Long resolveThreadId(Connection connection) {
        if (connection == null) {
            return null;
        }
        return messageThreadRepository.findByConnection(connection)
                .map(MessageThread::getId)
                .orElse(null);
    }

    /** 문서 단건 다운로드에서 재사용한다 — 응답 조립과 같은 필터를 통과시키기 위해서다. */
    Optional<ContractDocument> findDownloadableDocument(Contract contract, ContractDocumentType documentType) {
        return partyContractDocuments.find(contract, documentType);
    }
}
