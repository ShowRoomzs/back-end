package showroomz.api.seller.changerequest.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.basicinfo.SettlementAccountMasker;
import showroomz.api.seller.changerequest.dto.ChangeRequestBannerResponse;
import showroomz.api.seller.changerequest.dto.ChangeRequestCreateResponse;
import showroomz.api.seller.changerequest.dto.ChangeRequestFieldOption;
import showroomz.api.seller.changerequest.dto.ChangeRequestItemRequest;
import showroomz.api.seller.changerequest.dto.CreateChangeRequestRequest;
import showroomz.domain.bank.entity.Bank;
import showroomz.domain.bank.repository.BankRepository;
import showroomz.domain.changerequest.entity.BrandChangeRequest;
import showroomz.domain.changerequest.repository.BrandChangeRequestRepository;
import showroomz.domain.changerequest.service.ChangeRequestFieldResolver;
import showroomz.domain.changerequest.type.ChangeRequestField;
import showroomz.domain.changerequest.type.ChangeRequestRejectReason;
import showroomz.domain.changerequest.type.ChangeRequestStatus;
import showroomz.domain.changerequest.type.ChangeRequestType;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.Year;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandChangeRequestService {

    private static final Set<ChangeRequestField> SETTLEMENT_ACCOUNT_FIELDS =
            Set.of(ChangeRequestField.BANK_CODE, ChangeRequestField.ACCOUNT_NUMBER, ChangeRequestField.ACCOUNT_HOLDER);
    private static final String ACCOUNT_NUMBER_PATTERN = "^\\d{10,16}$";

    private final SellerRepository sellerRepository;
    private final MarketRepository marketRepository;
    private final BrandChangeRequestRepository brandChangeRequestRepository;
    private final BankRepository bankRepository;

    public List<ChangeRequestFieldOption> getFields(String sellerEmail, ChangeRequestType type) {
        Market market = getMyMarket(sellerEmail);
        Seller seller = market.getSeller();
        return ChangeRequestField.of(type).stream()
                .map(field -> ChangeRequestFieldOption.builder()
                        .fieldKey(field)
                        .label(field.getLabel())
                        .currentValue(ChangeRequestFieldResolver.currentValue(field, seller, market))
                        .build())
                .toList();
    }

    public ChangeRequestBannerResponse getBanner(String sellerEmail, ChangeRequestType type) {
        Market market = getMyMarket(sellerEmail);
        List<BrandChangeRequest> candidates = brandChangeRequestRepository.findBannerCandidates(
                market.getId(), type, ChangeRequestStatus.PENDING,
                List.of(ChangeRequestStatus.APPROVED, ChangeRequestStatus.REJECTED), PageRequest.of(0, 1));
        if (candidates.isEmpty()) {
            return null;
        }
        return toBanner(candidates.get(0));
    }

    @Transactional
    public ChangeRequestCreateResponse create(String sellerEmail, CreateChangeRequestRequest request) {
        Market market = getMyMarket(sellerEmail);
        Seller seller = market.getSeller();
        ChangeRequestType type = request.getType();

        validateCreateRequest(type, request);

        if (brandChangeRequestRepository.findByMarket_IdAndTypeAndStatus(market.getId(), type, ChangeRequestStatus.PENDING).isPresent()) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_ALREADY_PENDING);
        }

        String requestCode = generateRequestCode();
        BrandChangeRequest changeRequest = BrandChangeRequest.create(
                requestCode, market, type, request.getReason(), seller.getName(),
                request.getEvidenceFileUrl(), request.getEvidenceFileName(), request.getEvidenceFileSize());

        int sortOrder = 0;
        for (ChangeRequestField field : ChangeRequestField.of(type)) {
            ChangeRequestItemRequest item = findItem(request.getItems(), field);
            if (item == null) {
                continue;
            }
            String currentValue = ChangeRequestFieldResolver.currentValue(field, seller, market);
            String requestedValue = resolveRequestedValueForStorage(field, item.getRequestedValue());
            if (requestedValue.equals(currentValue)) {
                throw new BusinessException(ErrorCode.CHANGE_REQUEST_VALUE_UNCHANGED);
            }
            changeRequest.addItem(field, currentValue, requestedValue, sortOrder++);
        }

        try {
            brandChangeRequestRepository.save(changeRequest);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_ALREADY_PENDING);
        }

        return ChangeRequestCreateResponse.builder()
                .requestId(changeRequest.getId())
                .requestCode(changeRequest.getRequestCode())
                .type(changeRequest.getType())
                .status(changeRequest.getStatus())
                .requestedAt(changeRequest.getRequestedAt())
                .notifyEmail(seller.getEmail())
                .build();
    }

    @Transactional
    public void cancel(String sellerEmail, Long requestId) {
        BrandChangeRequest changeRequest = getOwnedRequest(sellerEmail, requestId);
        if (!changeRequest.isPending()) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_NOT_PENDING);
        }
        changeRequest.cancel();
    }

    @Transactional
    public void acknowledge(String sellerEmail, Long requestId) {
        BrandChangeRequest changeRequest = getOwnedRequest(sellerEmail, requestId);
        if (changeRequest.getStatus() != ChangeRequestStatus.APPROVED && changeRequest.getStatus() != ChangeRequestStatus.REJECTED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        changeRequest.acknowledge();
    }

    private BrandChangeRequest getOwnedRequest(String sellerEmail, Long requestId) {
        Market market = getMyMarket(sellerEmail);
        BrandChangeRequest changeRequest = brandChangeRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHANGE_REQUEST_NOT_FOUND));
        if (!changeRequest.getMarket().getId().equals(market.getId())) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_ACCESS_DENIED);
        }
        return changeRequest;
    }

    private void validateCreateRequest(ChangeRequestType type, CreateChangeRequestRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_ITEMS_REQUIRED);
        }
        for (ChangeRequestItemRequest item : request.getItems()) {
            if (!ChangeRequestField.of(type).contains(item.getFieldKey())) {
                throw new BusinessException(ErrorCode.CHANGE_REQUEST_FIELD_NOT_ALLOWED);
            }
        }
        if (type == ChangeRequestType.SETTLEMENT_ACCOUNT) {
            Set<ChangeRequestField> submitted = request.getItems().stream()
                    .map(ChangeRequestItemRequest::getFieldKey)
                    .collect(java.util.stream.Collectors.toSet());
            if (!submitted.equals(SETTLEMENT_ACCOUNT_FIELDS)) {
                throw new BusinessException(ErrorCode.CHANGE_REQUEST_ITEMS_REQUIRED);
            }
            String accountNumber = findItem(request.getItems(), ChangeRequestField.ACCOUNT_NUMBER).getRequestedValue();
            if (accountNumber == null || !accountNumber.matches(ACCOUNT_NUMBER_PATTERN)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "계좌번호는 하이픈 없이 숫자 10~16자리로 입력해주세요.");
            }
        } else if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_REASON_REQUIRED);
        }

        if (isBlank(request.getEvidenceFileUrl()) || isBlank(request.getEvidenceFileName()) || request.getEvidenceFileSize() == null) {
            throw new BusinessException(ErrorCode.CHANGE_REQUEST_EVIDENCE_REQUIRED);
        }
    }

    /** BANK_CODE는 클라이언트가 코드를 보내지만 대조표·배너 표시는 은행명을 쓴다(§1-3). */
    private String resolveRequestedValueForStorage(ChangeRequestField field, String requestedValue) {
        if (field != ChangeRequestField.BANK_CODE) {
            return requestedValue;
        }
        Bank bank = bankRepository.findById(requestedValue)
                .orElseThrow(() -> new BusinessException(ErrorCode.BANK_NOT_FOUND));
        return bank.getName();
    }

    private ChangeRequestItemRequest findItem(List<ChangeRequestItemRequest> items, ChangeRequestField field) {
        return items.stream().filter(i -> i.getFieldKey() == field).findFirst().orElse(null);
    }

    private String generateRequestCode() {
        String prefix = "CHG-" + Year.now().getValue() + "-";
        long sequence = brandChangeRequestRepository.countByRequestCodeStartingWith(prefix) + 1;
        return prefix + String.format("%04d", sequence);
    }

    private ChangeRequestBannerResponse toBanner(BrandChangeRequest request) {
        List<String> changedFieldLabels = request.getItems().stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .map(item -> item.getFieldKey().getLabel())
                .toList();

        ChangeRequestBannerResponse.ChangeRequestBannerResponseBuilder builder = ChangeRequestBannerResponse.builder()
                .requestId(request.getId())
                .requestCode(request.getRequestCode())
                .type(request.getType())
                .status(request.getStatus())
                .changedFieldLabels(changedFieldLabels)
                .requestedAt(request.getRequestedAt())
                .processedAt(request.getProcessedAt())
                .cancelable(request.isPending())
                .rejectReason(resolveRejectReasonText(request))
                .rejectReasonDetail(request.getStatus() == ChangeRequestStatus.REJECTED ? request.getRejectReasonDetail() : null);

        if (request.getType() == ChangeRequestType.SETTLEMENT_ACCOUNT) {
            String bankName = findItemValue(request, ChangeRequestField.BANK_CODE);
            String accountNumber = findItemValue(request, ChangeRequestField.ACCOUNT_NUMBER);
            builder.requestedAccount(ChangeRequestBannerResponse.RequestedAccount.builder()
                    .bankName(bankName)
                    .maskedAccountNumber(SettlementAccountMasker.mask(accountNumber))
                    .build());
        }

        return builder.build();
    }

    private String resolveRejectReasonText(BrandChangeRequest request) {
        if (request.getStatus() != ChangeRequestStatus.REJECTED || request.getRejectReason() == null) {
            return null;
        }
        try {
            return ChangeRequestRejectReason.valueOf(request.getRejectReason()).getDescription();
        } catch (IllegalArgumentException e) {
            return request.getRejectReason();
        }
    }

    private String findItemValue(BrandChangeRequest request, ChangeRequestField field) {
        return request.getItems().stream()
                .filter(i -> i.getFieldKey() == field)
                .map(showroomz.domain.changerequest.entity.BrandChangeRequestItem::getRequestedValue)
                .findFirst().orElse(null);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Market getMyMarket(String sellerEmail) {
        Seller seller = sellerRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELLER_NOT_FOUND));
        return marketRepository.findBySeller(seller)
                .orElseThrow(() -> new BusinessException(ErrorCode.MARKET_NOT_FOUND));
    }
}
