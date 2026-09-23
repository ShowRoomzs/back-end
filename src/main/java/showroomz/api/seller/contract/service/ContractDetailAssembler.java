package showroomz.api.seller.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.contract.dto.ContractDetailResponse;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractItem;
import showroomz.domain.contract.repository.ContractDocumentRepository;
import showroomz.domain.contract.repository.ContractHistoryRepository;
import showroomz.domain.contract.type.ContractCloseReasonLabels;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.contract.type.WithholdingType;
import showroomz.global.utils.RewardCalculator;

import java.util.List;

/**
 * 계약 상세 응답 조립.
 *
 * <p>상세 화면 13종이 이 응답 하나를 쓰므로(설계서 4-5) 조립을 한 곳에 모은다.
 * 재작성·검증 응답이 상세를 함께 실어야 할 때도 같은 조립기를 부른다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractDetailAssembler {

    /** 시안 「정산 조건 요약」의 고정 표시값. */
    private static final int PLATFORM_FEE_RATE = 2;

    private final ContractHistoryRepository contractHistoryRepository;
    private final ConnectionRepository connectionRepository;
    private final ContractDocumentRepository contractDocumentRepository;
    private final showroomz.api.admin.contract.service.ContractDocumentStorage contractDocumentStorage;

    public ContractDetailResponse assemble(Contract contract) {
        ContractStatus status = contract.getStatus();

        return new ContractDetailResponse(
                contract.getId(),
                contract.getContractNumber(),
                contract.getTitle(),
                status,
                status.getLabel(),
                status.getTone(),
                contract.getVersion(),
                contract.getSourceContractId(),
                counterparty(contract),
                period(contract),
                items(contract),
                fixedFee(contract),
                content(contract),
                review(contract),
                signature(contract),
                settlement(contract),
                closure(contract),
                groupBuy(contract),
                documents(contract),
                permissions(contract),
                history(contract));
    }

    /**
     * {@code connection_id}는 <b>스레드 경유로 상대가 고정된 계약</b>에만 채워진다(§25-5-1).
     * 목록에서 시작해 상대를 직접 고른 계약은 비어 있으므로, 「스레드 열기」 딥링크가 쓸 연결 ID는
     * 읽는 시점의 CONNECTED 연결에서 찾아 채운다 — 연결이 그 사이 끊겼으면 null이 맞다.
     */
    private ContractDetailResponse.Counterparty counterparty(Contract contract) {
        if (contract.getCreator() == null) {
            return new ContractDetailResponse.Counterparty(null, null, null, false);
        }

        Long connectionId = contract.getConnection() != null
                ? contract.getConnection().getId()
                : connectionRepository
                        .findConnectedPair(contract.getMarket().getId(), contract.getCreator().getId())
                        .map(connection -> connection.getId())
                        .orElse(null);

        return new ContractDetailResponse.Counterparty(
                contract.getCreator().getId(),
                contract.getCreator().getShowroomName(),
                connectionId,
                contract.isCounterpartyFixed());
    }

    private ContractDetailResponse.Period period(Contract contract) {
        return new ContractDetailResponse.Period(
                contract.getGroupBuyStartAt(), contract.getGroupBuyEndAt(), contract.periodDays());
    }

    private List<ContractDetailResponse.Item> items(Contract contract) {
        return contract.getItems().stream()
                .map(this::toItem)
                .toList();
    }

    private ContractDetailResponse.Item toItem(ContractItem item) {
        return new ContractDetailResponse.Item(
                item.getId(),
                item.getProductId(),
                item.getProductName(),
                item.getRegularPrice(),
                item.getGroupBuyPrice(),
                item.getRewardRate(),
                // 저장하지 않는 파생값 — 계약·정산·계약서 PDF가 같은 유틸을 부른다(설계서 1-5).
                RewardCalculator.calcUnitReward(item.getGroupBuyPrice(), item.getRewardRate()),
                item.getMinQuantity());
    }

    private ContractDetailResponse.FixedFee fixedFee(Contract contract) {
        return new ContractDetailResponse.FixedFee(
                contract.getFixedFeeAmount(),
                contract.getFixedFeeTrigger(),
                contract.getFixedFeeTrigger() == null ? null : contract.getFixedFeeTrigger().getLabel(),
                contract.getFixedFeeNoticeAgreedAt(),
                contract.getFixedFeePaidAt(),
                contract.isObligationAlive());
    }

    private ContractDetailResponse.Content content(Contract contract) {
        return new ContractDetailResponse.Content(
                contract.getContentFeedCount(),
                contract.getContentReelsCount(),
                contract.getContentStoryCount(),
                contract.getContentDueDate(),
                contract.getSecondaryUseAllowed(),
                contract.getSecondaryUsePeriodType(),
                contract.getSecondaryUseMonths(),
                contract.getBrandPreReview(),
                contract.getNote());
    }

    private ContractDetailResponse.Review review(Contract contract) {
        ContractDetailResponse.Review.RejectReason rejectReason = contract.getRejectReasonCode() == null
                ? null
                : new ContractDetailResponse.Review.RejectReason(
                        contract.getRejectReasonCode(), contract.getRejectReasonDetail());

        return new ContractDetailResponse.Review(
                contract.getReviewRequestedAt(),
                contract.getReviewApprovedAt(),
                contract.getReviewRejectedAt(),
                rejectReason);
    }

    private ContractDetailResponse.Signature signature(Contract contract) {
        return new ContractDetailResponse.Signature(
                contract.getSignatureRequestedAt(),
                contract.getSignatureDeadlineAt(),
                contract.getBrandSignedAt(),
                contract.getCreatorSignedAt(),
                contract.getSignatureAsOf(),
                contract.getCreatorViewedAt() != null);
    }

    private ContractDetailResponse.Settlement settlement(Contract contract) {
        WithholdingType withholdingType = contract.getCreator() == null
                ? null
                : WithholdingType.from(contract.getCreator().getBusinessType());

        return new ContractDetailResponse.Settlement(
                PLATFORM_FEE_RATE,
                // 자문 회신 전이라 null로 내린다. 0으로 내리면 화면이 「0%」로 읽어
                // 수수료가 없다는 뜻이 되어버린다 — null과 0은 다르다(설계서 4-5).
                null,
                withholdingType,
                withholdingType == null ? null : withholdingType.getLabel());
    }

    private ContractDetailResponse.Closure closure(Contract contract) {
        return new ContractDetailResponse.Closure(
                contract.getClosedAt(),
                contract.getCloseActorType(),
                contract.getCloseReasonCode(),
                closeReasonLabel(contract.getCloseReasonCode()),
                contract.getCloseReasonMemo());
    }

    /**
     * 종결 사유 라벨. 브랜드 취소 사유 5종과 인플루언서 거절 사유 5종이 같은 컬럼을 쓰므로
     * 해석은 공용 리졸버에 맡긴다(§27 설계서 5-1 「사유는 브랜드에게 그대로 전달된다」).
     * 모르는 코드에는 그럴듯한 문구를 지어내지 않고 null을 내린다.
     */
    private String closeReasonLabel(String reasonCode) {
        return ContractCloseReasonLabels.labelOf(reasonCode);
    }

    private ContractDetailResponse.GroupBuy groupBuy(Contract contract) {
        return new ContractDetailResponse.GroupBuy(
                contract.getGroupBuyId(),
                contract.getStatus() == ContractStatus.CONCLUDED && contract.getGroupBuyId() == null);
    }

    private List<ContractDetailResponse.Document> documents(Contract contract) {
        if (contract.getStatus() != ContractStatus.CONCLUDED) return List.of();
        return contractDocumentRepository.findByContractIdOrderByDocumentTypeAsc(contract.getId()).stream()
                .filter(document -> document.getDocumentType() != showroomz.domain.contract.type.ContractDocumentType.GENERATED_DRAFT)
                .map(document -> new ContractDetailResponse.Document(
                        document.getDocumentType(),
                        document.getDocumentType().getLabel(),
                        contractDocumentStorage.download(document).downloadUrl()))
                .toList();
    }

    /** 설계서 4-5 — 상태 × 서명 조합 × 지급 여부로 갈리는 버튼 판정을 서버가 한 번에 한다. */
    private ContractDetailResponse.Permissions permissions(Contract contract) {
        ContractStatus status = contract.getStatus();
        boolean editable = status.isEditable();

        return new ContractDetailResponse.Permissions(
                editable,
                // 삭제는 작성중 초안만. 검토 요청 이후의 되돌림은 취소이지 삭제가 아니다(설계서 4-2).
                status == ContractStatus.DRAFT,
                editable,
                status == ContractStatus.REVIEW_PENDING,
                ContractStatus.CANCELABLE.contains(status),
                // [서명 안내 다시 받기]는 「내 서명 안내」를 다시 보내달라는 요청이다 —
                // 내가 이미 서명했으면 받을 안내가 없어 B4a에서는 버튼이 사라진다(§26-B4a).
                status == ContractStatus.SIGNING && contract.getBrandSignedAt() == null,
                status == ContractStatus.CONCLUDED
                        && contract.hasFixedFee()
                        && contract.getFixedFeePaidAt() == null,
                status == ContractStatus.CONCLUDED && contract.getGroupBuyId() == null,
                ContractStatus.DUPLICABLE.contains(status));
    }

    private List<ContractDetailResponse.HistoryEntry> history(Contract contract) {
        return contractHistoryRepository.findByContractIdOrderByOccurredAtAscIdAsc(contract.getId()).stream()
                .map(entry -> new ContractDetailResponse.HistoryEntry(
                        entry.getEventType(),
                        entry.getActorType(),
                        entry.getActorDisplayName(),
                        entry.getDetail(),
                        entry.getOccurredAt()))
                .toList();
    }
}
