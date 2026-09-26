package showroomz.api.seller.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.contract.service.ContractDraftGenerator;
import showroomz.api.seller.contract.dto.ContractCreateRequest;
import showroomz.api.seller.contract.dto.ContractCreateResponse;
import showroomz.api.seller.contract.dto.ContractDetailResponse;
import showroomz.api.seller.contract.dto.ContractDuplicateResponse;
import showroomz.api.seller.contract.dto.ContractResendRequestResponse;
import showroomz.api.seller.contract.dto.ContractReviewRequestRequest;
import showroomz.api.seller.contract.dto.ContractUpdateRequest;
import showroomz.api.seller.contract.dto.ContractValidationResponse;
import showroomz.domain.connection.entity.Connection;
import showroomz.domain.connection.repository.ConnectionRepository;
import showroomz.domain.contract.entity.Contract;
import showroomz.domain.contract.entity.ContractClauseVersion;
import showroomz.domain.contract.entity.ContractItem;
import showroomz.domain.contract.entity.ContractResendRequest;
import showroomz.domain.contract.repository.ContractClauseVersionRepository;
import showroomz.domain.contract.repository.ContractRepository;
import showroomz.domain.contract.repository.ContractResendRequestRepository;
import showroomz.domain.contract.service.ContractHistoryRecorder;
import showroomz.domain.contract.service.ContractNotifier;
import showroomz.domain.contract.service.ContractNumberGenerator;
import showroomz.domain.contract.type.ContractActorType;
import showroomz.domain.contract.type.ContractClauseVersionStatus;
import showroomz.domain.contract.type.ContractEventType;
import showroomz.domain.contract.type.ContractStatus;
import showroomz.domain.contract.type.ContractViolation;
import showroomz.domain.contract.type.ContractViolationCode;
import showroomz.domain.market.entity.Market;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.product.entity.Product;
import showroomz.domain.product.repository.ProductRepository;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ContractValidationException;
import showroomz.global.error.exception.ErrorCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 파트너센터 계약 작성·전이(설계서 4-2·4-3).
 *
 * <p>쓰기 API는 전부 진입 시 <b>상태를 먼저 판정</b>하고 허용 집합 밖이면 409로 떨군다 —
 * 편집 잠금은 화면 상태가 아니라 서버 권한이다(설계서 0-4).
 *
 * <p>상태 전이는 전부 조건부 UPDATE로 쓴다. 읽고-판정하고-저장하는 사이에 다른 요청이 상태를 바꿔도
 * DB가 한 번 더 막는다(설계서 3-3).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SellerContractCommandService {

    private final ContractAccessGuard accessGuard;
    private final ContractValidator contractValidator;
    private final ContractDetailAssembler detailAssembler;
    private final ContractRepository contractRepository;
    private final ContractResendRequestRepository resendRequestRepository;
    private final ContractClauseVersionRepository clauseVersionRepository;
    private final ConnectionRepository connectionRepository;
    private final ProductRepository productRepository;
    private final ContractNumberGenerator contractNumberGenerator;
    private final ContractHistoryRecorder historyRecorder;
    private final ContractNotifier contractNotifier;
    private final ContractDraftGenerator draftGenerator;

    // ── P3 · 작성 · 임시저장 ────────────────────────────────────────────────

    /** 빈 초안 생성(B1). 조건은 여기서 받지 않는다 — 작성 화면이 열린 뒤 PUT으로 채운다. */
    public ContractCreateResponse create(String sellerEmail, ContractCreateRequest request) {
        Market market = accessGuard.resolveMarket(sellerEmail);

        Creator creator = null;
        Connection connection = null;
        if (request.creatorId() != null) {
            // 스레드 경유 진입 — 상대를 고정한다(§25-5-1). 연결 근거는 요청이 보낸 connectionId가 아니라
            // 지금 실제로 CONNECTED인 연결에서 가져온다. 보내온 id를 믿으면 남의 연결을 붙일 수 있다.
            connection = requireConnectedPair(market.getId(), request.creatorId());
            creator = connection.getCreator();
        }

        Contract contract = contractRepository.save(Contract.createDraft(market, creator, connection));
        historyRecorder.recordBySeller(contract, ContractEventType.CREATED, null, LocalDateTime.now());

        return new ContractCreateResponse(contract.getId(), contract.getVersion());
    }

    /** 임시저장(B1·B2) — 전체 교체. 형식만 보고 「필수」는 보지 않는다(설계서 0-3). */
    public ContractDetailResponse update(String sellerEmail, Long contractId, ContractUpdateRequest request) {
        Market market = accessGuard.resolveMarket(sellerEmail);
        Contract contract = accessGuard.loadOwned(contractId, market);

        requireEditable(contract);

        // 버전 검사와 증가를 조건부 UPDATE 하나로 한다(설계서 3-3·3-4). 읽어서 비교만 하면
        // ① 동시 요청 둘이 같은 버전을 보고 나란히 통과하고 ② 항목만 바꾼 저장은 계약 행을
        // 더럽히지 않아 JPA가 버전을 올리지도 않는다 — 두 탭의 저장이 서로를 말없이 덮는다.
        if (contractRepository.bumpVersion(contractId, request.version(), LocalDateTime.now()) == 0) {
            throw new BusinessException(ErrorCode.CONTRACT_MODIFIED_ELSEWHERE);
        }
        // 위 UPDATE가 영속성 컨텍스트를 비웠다 — 올라간 버전으로 다시 읽어 그 위에 값을 얹는다.
        contract = accessGuard.loadOwned(contractId, market);

        applyCounterparty(contract, market, request.creatorId());
        applyTerms(contract, request);
        contract.replaceItems(buildItems(contract, market, request.items()));

        // 저장 결과를 그대로 돌려준다 — 서버가 초기화한 값(상품이 바뀐 행의 공구가·리워드율)과
        // 올라간 버전을 FE가 다시 조회하지 않고 화면에 반영할 수 있어야 한다.
        contractRepository.saveAndFlush(contract);
        return detailAssembler.assemble(contract);
    }

    /**
     * 작성중·검토 반려만 지운다. 검토 대기 이후는 어드민이 보고 있거나 상대에게 나간 계약이라
     * 되돌림은 취소이지 삭제가 아니다(설계서 4-2).
     *
     * <p>행을 지우지 않고 표시만 한다 — 검토 반려 계약은 계약번호·어드민 반려 이력·제출본 PDF를
     * 이미 가지고 있다. 표시된 계약은 파트너센터·어드민 어디에서도 조회되지 않는다.
     */
    public void delete(String sellerEmail, Long contractId) {
        Market market = accessGuard.resolveMarket(sellerEmail);
        Contract contract = accessGuard.loadOwned(contractId, market);

        if (!contract.isDeletable()) {
            throw new BusinessException(ErrorCode.CONTRACT_EDIT_LOCKED);
        }

        LocalDateTime now = LocalDateTime.now();
        // 이력을 먼저 쌓는다 — 아래 UPDATE가 0행이면 예외로 함께 롤백된다.
        historyRecorder.recordBySeller(contract, ContractEventType.DELETED, null, now);
        // 다른 탭의 검토 요청과 경합하면 한쪽만 1행을 얻는다 — 읽은 뒤 상태가 바뀐 것이다.
        if (contractRepository.softDelete(contract.getId(), ContractStatus.DELETABLE, now) == 0) {
            throw new BusinessException(ErrorCode.CONTRACT_STATUS_CONFLICT);
        }
    }

    // ── P4 · 검증 ──────────────────────────────────────────────────────────

    /** 상태를 바꾸지 않고 하드·경고 판정만 돌려준다(설계서 2-4). */
    @Transactional(readOnly = true)
    public ContractValidationResponse validate(String sellerEmail, Long contractId) {
        Market market = accessGuard.resolveMarket(sellerEmail);
        Contract contract = accessGuard.loadOwned(contractId, market);

        ContractValidator.Result result =
                contractValidator.validate(contract, market.getId(), LocalDateTime.now());
        return ContractValidationResponse.of(result.violations(), result.warnings());
    }

    // ── P5 · 상태 전이 ──────────────────────────────────────────────────────

    /**
     * 검토 요청(B3c) — 한 트랜잭션이 하는 일:
     * 상태 판정 → 스냅샷 정가 갱신 → 하드 전량 재검증 → 경고 판정·대조 → 계약번호 부여 →
     * 조항 버전 고정 → 상태 전이 → 이력 append → 어드민 통지.
     *
     * <p>번호 부여가 전이와 같은 트랜잭션 안에 있어야 번호 없는 REVIEW_PENDING이 생기지 않는다.
     */
    public ContractDetailResponse requestReview(String sellerEmail, Long contractId,
                                                ContractReviewRequestRequest request) {
        Market market = accessGuard.resolveMarket(sellerEmail);
        Contract contract = accessGuard.loadOwned(contractId, market);
        requireEditable(contract);

        LocalDateTime now = LocalDateTime.now();

        refreshItemSnapshots(contract);

        ContractValidator.Result result = contractValidator.validate(contract, market.getId(), now);
        if (!result.canSubmit()) {
            throw new ContractValidationException(result.violations());
        }
        requireWarningsAcknowledged(result, request.acknowledgedWarningsOrEmpty());

        // 조건부 UPDATE가 경합을 막는다 — 어드민의 [검토 통과]와 동시에 들어오면 하나는 0행이 된다.
        int changed = contractRepository.transitionStatusFromAny(
                contract.getId(),
                List.of(ContractStatus.DRAFT, ContractStatus.REVIEW_REJECTED),
                ContractStatus.REVIEW_PENDING);
        if (changed == 0) {
            throw new BusinessException(ErrorCode.CONTRACT_STATUS_CONFLICT);
        }

        String contractNumber = contract.getContractNumber() == null
                ? contractNumberGenerator.generate(now.toLocalDate())
                : contract.getContractNumber();

        ContractClauseVersion clauseVersion = clauseVersionRepository
                .findFirstByStatusOrderByEffectiveDateDescIdDesc(ContractClauseVersionStatus.EFFECTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTRACT_CLAUSE_VERSION_NOT_FOUND));

        String warningFlags = joinWarningFlags(result.distinctWarningCodes());
        contract.applyReviewRequested(contractNumber, clauseVersion, warningFlags, now);

        historyRecorder.recordBySeller(contract, ContractEventType.REVIEW_REQUESTED,
                warningFlags == null ? null : "확인한 주의 항목: " + warningFlags, now);
        contractNotifier.notifyAdmin(contract, ContractEventType.REVIEW_REQUESTED.name());

        contractRepository.saveAndFlush(contract);
        // 제출본 PDF — 계약번호·조항 버전·제출 시각이 모두 정해진 뒤에 만든다. 실패해도 제출은 성공이고
        // 생성본은 어드민 첫 다운로드가 만든다(기존 경로). 성공하면 응답에 제출본이 바로 실린다.
        draftGenerator.generateOnSubmit(contract, now);
        return detailAssembler.assemble(contract);
    }

    /**
     * 검토 요청 취소(B3c) — 작성중으로 되돌린다. <b>종결이 아니고 사유도 받지 않는다</b>(설계서 3-2).
     * 브랜드에게 계약 취소(종결)는 없다 — 발송 전의 되돌림은 이것뿐이고, 발송 이후 취소는 운영자 몫이다.
     */
    public ContractDetailResponse cancelReviewRequest(String sellerEmail, Long contractId) {
        Market market = accessGuard.resolveMarket(sellerEmail);
        Contract contract = accessGuard.loadOwned(contractId, market);

        if (contract.getStatus() != ContractStatus.REVIEW_PENDING) {
            throw new BusinessException(ErrorCode.CONTRACT_STATUS_CONFLICT);
        }

        int changed = contractRepository.transitionStatus(
                contract.getId(), ContractStatus.REVIEW_PENDING, ContractStatus.DRAFT);
        if (changed == 0) {
            throw new BusinessException(ErrorCode.CONTRACT_STATUS_CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        contract.applyReviewRequestCanceled();

        historyRecorder.recordBySeller(contract, ContractEventType.REVIEW_REQUEST_CANCELED, null, now);
        // 어드민은 이미 검토를 시작했을 수 있다 — 상대에게는 아직 아무것도 가지 않았으므로 통지하지 않는다.
        contractNotifier.notifyAdmin(contract, ContractEventType.REVIEW_REQUEST_CANCELED.name());

        contractRepository.saveAndFlush(contract);
        return detailAssembler.assemble(contract);
    }

    // ── P6 · 체결 이후 ─────────────────────────────────────────────────────

    /**
     * [서명 안내 다시 받기] — 상태는 변하지 않는다. 요청은 발송이 아니다.
     *
     * <p>횟수 제한은 정책 미정(§28-8 D #7)이라 만들지 않되 연타는 막는다 —
     * 미처리 요청이 이미 있으면 새 행 대신 그 요청을 돌려준다(설계서 3-6).
     */
    public ContractResendRequestResponse requestResend(String sellerEmail, Long contractId) {
        Market market = accessGuard.resolveMarket(sellerEmail);
        Contract contract = accessGuard.loadOwned(contractId, market);

        if (contract.getStatus() != ContractStatus.SIGNING) {
            throw new BusinessException(ErrorCode.CONTRACT_STATUS_CONFLICT);
        }
        // 내가 이미 서명했으면 다시 받을 안내가 없다(§26-B4a) — 상대의 안내를 브랜드가 대신
        // 요청하는 경로는 시안에 없다. 스튜디오의 [서명 안내 다시 받기]와 같은 판정이다.
        if (contract.getBrandSignedAt() != null) {
            throw new BusinessException(ErrorCode.CONTRACT_RESEND_NOT_ALLOWED);
        }

        return resendRequestRepository
                .findFirstByContractIdAndHandledAtIsNullOrderByRequestedAtDesc(contract.getId())
                .map(existing -> new ContractResendRequestResponse(
                        existing.getId(), existing.getRequestedAt(), true))
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    ContractResendRequest saved = resendRequestRepository.save(ContractResendRequest.of(
                            contract, ContractActorType.SELLER, market.getId(), now));
                    historyRecorder.recordBySeller(contract, ContractEventType.RESEND_REQUESTED, null, now);
                    contractNotifier.notifyAdmin(contract, ContractEventType.RESEND_REQUESTED.name());
                    return new ContractResendRequestResponse(saved.getId(), saved.getRequestedAt(), false);
                });
    }

    /**
     * 고정 지급비 [지급 완료 기록](B5a).
     * 되돌리는 API를 만들지 않는다 — 시안에 취소 버튼이 없고 정정 경로가 미결이다(설계서 미결 #4).
     */
    public ContractDetailResponse recordFixedFeePayment(String sellerEmail, Long contractId) {
        Market market = accessGuard.resolveMarket(sellerEmail);
        Contract contract = accessGuard.loadOwned(contractId, market);

        if (contract.getStatus() != ContractStatus.CONCLUDED || !contract.hasFixedFee()) {
            throw new BusinessException(ErrorCode.CONTRACT_STATUS_CONFLICT);
        }
        if (contract.getFixedFeePaidAt() != null) {
            throw new BusinessException(ErrorCode.CONTRACT_FIXED_FEE_ALREADY_PAID);
        }

        LocalDateTime now = LocalDateTime.now();
        int changed = contractRepository.markFixedFeePaid(contract.getId(), now);
        if (changed == 0) {
            // 읽은 뒤 커밋된 다른 요청이 먼저 기록했다.
            throw new BusinessException(ErrorCode.CONTRACT_FIXED_FEE_ALREADY_PAID);
        }

        contract.applyFixedFeePaid(now);
        historyRecorder.recordBySeller(contract, ContractEventType.FIXED_FEE_PAID, null, now);
        contractNotifier.notifyCounterparty(contract, ContractEventType.FIXED_FEE_PAID.name());

        contractRepository.saveAndFlush(contract);
        return detailAssembler.assemble(contract);
    }

    /**
     * 「이 조건으로 새 계약 작성」(§26-5). 복사가 아니라 <b>새 행 생성</b>이다 —
     * 원 계약은 그대로 종결 상태로 남는다.
     *
     * <p>진행 중 계약의 복제는 허용하지 않는다. 같은 조건의 계약 2건이 동시에 검토 큐에 올라가는
     * 상황을 기획이 다룬 적 없다(설계서 4-3).
     */
    public ContractDuplicateResponse duplicate(String sellerEmail, Long contractId) {
        Market market = accessGuard.resolveMarket(sellerEmail);
        Contract source = accessGuard.loadOwned(contractId, market);

        if (!ContractStatus.DUPLICABLE.contains(source.getStatus())) {
            throw new BusinessException(ErrorCode.CONTRACT_NOT_DUPLICABLE);
        }

        Contract copy = Contract.createDraft(market, source.getCreator(), source.getConnection());
        copy.updateTerms(
                source.getTitle(),
                // 과거 날짜를 그대로 복사하면 H5(시작 일시 ≥ 검토 요청일 + 7일)에 즉시 걸린다.
                // 게시 완료 기한은 기간에 연동되므로 함께 비운다(§26-5).
                null, null,
                source.getFixedFeeAmount(),
                source.getFixedFeeTrigger(),
                // 고지 확인은 다시 받는다 — 새 계약의 동의를 옛 계약의 체크로 대신할 수 없다.
                null,
                source.getContentFeedCount(),
                source.getContentReelsCount(),
                source.getContentStoryCount(),
                null,
                source.getSecondaryUseAllowed(),
                source.getSecondaryUsePeriodType(),
                source.getSecondaryUseMonths(),
                source.getBrandPreReview(),
                source.getNote());
        copy.markDuplicatedFrom(source.getId());

        List<ContractItem> copiedItems = new ArrayList<>();
        for (ContractItem item : source.getItems()) {
            copiedItems.add(ContractItem.builder()
                    .product(item.getProduct())
                    .productName(item.getProductName())
                    .regularPrice(item.getRegularPrice())
                    .groupBuyPrice(item.getGroupBuyPrice())
                    .rewardRate(item.getRewardRate())
                    .minQuantity(item.getMinQuantity())
                    .sortOrder(item.getSortOrder())
                    .build());
        }
        copy.replaceItems(copiedItems);

        Contract saved = contractRepository.save(copy);
        // 상품이 미진열로 바뀌었거나 정가가 내려갔을 수 있다. 옛 정가를 들고 있으면 H1이 통과해버린다.
        refreshItemSnapshots(saved);

        LocalDateTime now = LocalDateTime.now();
        historyRecorder.recordBySeller(saved, ContractEventType.CREATED,
                "계약 %s의 조건으로 재작성".formatted(
                        source.getContractNumber() == null ? source.getId().toString() : source.getContractNumber()),
                now);

        contractRepository.flush();
        ContractValidator.Result result = contractValidator.validate(saved, market.getId(), now);

        return new ContractDuplicateResponse(
                saved.getId(),
                source.getId(),
                ContractValidationResponse.of(result.violations(), result.warnings()));
    }

    // ── 공통 ───────────────────────────────────────────────────────────────

    /** §25-4의 잠금 구간을 서버가 집행한다. 허용 집합은 딱 둘 — DRAFT, REVIEW_REJECTED. */
    private void requireEditable(Contract contract) {
        if (!contract.isEditable()) {
            throw new BusinessException(ErrorCode.CONTRACT_EDIT_LOCKED);
        }
    }

    private Connection requireConnectedPair(Long marketId, Long creatorId) {
        return connectionRepository.findConnectedPair(marketId, creatorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTRACT_COUNTERPARTY_NOT_CONNECTED));
    }

    private void applyCounterparty(Contract contract, Market market, Long requestedCreatorId) {
        if (contract.isCounterpartyFixed()) {
            Long fixedId = contract.getCreator() == null ? null : contract.getCreator().getId();
            if (!Objects.equals(fixedId, requestedCreatorId)) {
                throw new BusinessException(ErrorCode.CONTRACT_COUNTERPARTY_FIXED);
            }
            return;
        }
        if (requestedCreatorId == null) {
            contract.changeCounterparty(null, null);
            return;
        }
        // 상대는 임시저장 시점에도 연결됨만 고를 수 있다(§25-4) — 검토 요청 때 또 본다.
        Connection connection = requireConnectedPair(market.getId(), requestedCreatorId);
        // 연결은 상대 검증의 근거일 뿐 여기서 고정 표시로 저장하지 않는다 —
        // connection_id가 채워진 계약은 「스레드 경유로 상대가 고정된 계약」이라는 뜻이다.
        contract.changeCounterparty(connection.getCreator(), null);
    }

    private void applyTerms(Contract contract, ContractUpdateRequest request) {
        LocalDateTime noticeAgreedAt = contract.getFixedFeeNoticeAgreedAt();
        if (Boolean.TRUE.equals(request.fixedFeeNoticeAgreed())) {
            // 이미 체크돼 있으면 시각을 갱신하지 않는다 — 언제 확인했는지가 기록의 요점이다.
            if (noticeAgreedAt == null) {
                noticeAgreedAt = LocalDateTime.now();
            }
        } else {
            noticeAgreedAt = null;
        }

        contract.updateTerms(
                trimToNull(request.title()),
                request.groupBuyStartAt(),
                request.groupBuyEndAt(),
                request.fixedFeeAmount(),
                request.fixedFeeTrigger(),
                noticeAgreedAt,
                request.contentFeedCount(),
                request.contentReelsCount(),
                request.contentStoryCount(),
                request.contentDueDate(),
                request.secondaryUseAllowed(),
                request.secondaryUsePeriodType(),
                request.secondaryUseMonths(),
                request.brandPreReview(),
                trimToNull(request.note()));
    }

    /**
     * 항목 배열을 통째로 다시 만든다. sort_order는 배열 index다.
     *
     * <p>형식 위반(10원 단위·소수점 자릿수)만 400으로 막는다 — 값이 비어 있는 것은 막지 않는다.
     */
    private List<ContractItem> buildItems(Contract contract, Market market,
                                          List<ContractUpdateRequest.Item> requestItems) {
        if (requestItems == null || requestItems.isEmpty()) {
            return List.of();
        }

        Map<Long, ContractItem> existingById = new HashMap<>();
        for (ContractItem item : contract.getItems()) {
            existingById.put(item.getId(), item);
        }

        List<ContractViolation> formViolations = new ArrayList<>();
        List<ContractItem> built = new ArrayList<>();

        for (int index = 0; index < requestItems.size(); index++) {
            ContractUpdateRequest.Item request = requestItems.get(index);
            String prefix = "items[%d]".formatted(index);

            Product product = resolveProduct(market, request.productId());

            ContractItem existing = request.contractItemId() == null
                    ? null : existingById.get(request.contractItemId());
            boolean productChanged = existing != null
                    && !Objects.equals(existing.getProductId(), request.productId());

            Integer groupBuyPrice = productChanged ? null : request.groupBuyPrice();
            BigDecimal rewardRate = productChanged ? null : request.rewardRate();
            Integer minQuantity = productChanged ? null : request.minQuantity();

            if (groupBuyPrice != null && groupBuyPrice % 10 != 0) {
                formViolations.add(ContractViolation.of(ContractViolationCode.H2, prefix + ".groupBuyPrice"));
            }
            if (rewardRate != null && rewardRate.stripTrailingZeros().scale() > 1) {
                formViolations.add(ContractViolation.of(
                        ContractViolationCode.ITEM_REWARD_RATE_SCALE, prefix + ".rewardRate"));
            }

            built.add(ContractItem.builder()
                    .product(product)
                    // 상품명·정가는 지금 값을 복사해 둔다. 검토 요청 시점에 한 번 더 갱신된다(설계서 0-5).
                    .productName(product == null ? null : product.getName())
                    .regularPrice(product == null ? null : product.getRegularPrice())
                    .groupBuyPrice(groupBuyPrice)
                    .rewardRate(rewardRate)
                    .minQuantity(minQuantity)
                    .sortOrder(index)
                    .build());
        }

        if (!formViolations.isEmpty()) {
            throw new ContractValidationException(formViolations);
        }
        return built;
    }

    /**
     * 임시저장 단계에서는 <b>소유</b>만 본다. 진열 여부는 검토 요청 때 본다 —
     * 작성 도중 잠깐 미진열이 된 상품 때문에 임시저장이 실패하면 안 된다.
     * 남의 상품은 다르다 — 상품명·정가를 스냅샷으로 복사하는 순간 남의 상품 정보가 이 계약에 들어온다.
     */
    private Product resolveProduct(Market market, Long productId) {
        if (productId == null) {
            return null;
        }
        Product product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (product.getMarket() == null || !market.getId().equals(product.getMarket().getId())) {
            throw new BusinessException(ErrorCode.CONTRACT_PRODUCT_NOT_OWNED);
        }
        return product;
    }

    /** 스냅샷 정가를 현재 상품 정가로 맞춘다 — 그래야 H1을 지금 값으로 다시 볼 수 있다(설계서 2-2). */
    private void refreshItemSnapshots(Contract contract) {
        for (ContractItem item : contract.getItems()) {
            Product product = item.getProduct();
            if (product != null) {
                item.refreshSnapshot(product.getName(), product.getRegularPrice());
            }
        }
    }

    /**
     * 서버가 판정한 경고 집합과 사용자가 확인한 집합이 다르면 409로 되돌려 모달을 다시 띄우게 한다 —
     * 모달을 본 뒤 다른 탭에서 값을 고쳤을 수 있다(설계서 2-3).
     */
    private void requireWarningsAcknowledged(ContractValidator.Result result, List<String> acknowledged) {
        Set<String> expected = new TreeSet<>(result.distinctWarningCodes());
        Set<String> actual = new TreeSet<>(acknowledged);
        if (!expected.equals(actual)) {
            throw new BusinessException(ErrorCode.CONTRACT_WARNING_MISMATCH);
        }
    }

    private String joinWarningFlags(List<String> codes) {
        return codes.isEmpty() ? null : String.join(",", codes);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
