package showroomz.api.seller.changerequest.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.changerequest.dto.ChangeRequestItemRequest;
import showroomz.api.seller.changerequest.dto.CreateChangeRequestRequest;
import showroomz.domain.bank.repository.BankRepository;
import showroomz.domain.changerequest.entity.BrandChangeRequest;
import showroomz.domain.changerequest.repository.BrandChangeRequestRepository;
import showroomz.domain.changerequest.type.ChangeRequestField;
import showroomz.domain.changerequest.type.ChangeRequestType;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BrandChangeRequestServiceTest {

    private static final String SELLER_EMAIL = "brand@showroomz.co.kr";

    @Mock
    private SellerRepository sellerRepository;
    @Mock
    private MarketRepository marketRepository;
    @Mock
    private BrandChangeRequestRepository brandChangeRequestRepository;
    @Mock
    private BankRepository bankRepository;

    @InjectMocks
    private BrandChangeRequestService brandChangeRequestService;

    private Seller seller;
    private Market market;

    @BeforeEach
    void setUp() {
        seller = new Seller(SELLER_EMAIL, "encoded", "김담당", "010-1234-5678", LocalDateTime.now());
        seller.setRepresentativeName("김대표");
        seller.setBusinessAddress("서울시 강남구 테헤란로 000");
        seller.setDetailAddress("오오빌딩 5층");

        market = new Market(seller, "코코브라운", "02-1234-5678");
        given(sellerRepository.findByEmail(SELLER_EMAIL)).willReturn(Optional.of(seller));
        given(marketRepository.findBySeller(seller)).willReturn(Optional.of(market));
    }

    private CreateChangeRequestRequest businessInfoRequest(String requestedRepresentativeName) {
        CreateChangeRequestRequest request = new CreateChangeRequestRequest();
        request.setType(ChangeRequestType.BUSINESS_INFO);
        ChangeRequestItemRequest item = new ChangeRequestItemRequest();
        item.setFieldKey(ChangeRequestField.REPRESENTATIVE_NAME);
        item.setRequestedValue(requestedRepresentativeName);
        request.setItems(List.of(item));
        request.setReason("대표자 변경");
        request.setEvidenceFileUrl("https://cdn.example.com/a.jpg");
        request.setEvidenceFileName("사업자등록증.jpg");
        request.setEvidenceFileSize(1024L);
        return request;
    }

    @Test
    @DisplayName("이미 PENDING 요청이 있으면 중복 요청을 차단한다")
    void rejectsWhenPendingRequestAlreadyExists() {
        given(brandChangeRequestRepository.findByMarket_IdAndTypeAndStatus(
                any(), any(), any())).willReturn(Optional.of(mockPendingRequest()));

        assertThatThrownBy(() -> brandChangeRequestService.create(SELLER_EMAIL, businessInfoRequest("이대표")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHANGE_REQUEST_ALREADY_PENDING);
    }

    @Test
    @DisplayName("요청값이 현재값과 같으면 400으로 거부한다")
    void rejectsWhenRequestedValueEqualsCurrentValue() {
        given(brandChangeRequestRepository.findByMarket_IdAndTypeAndStatus(any(), any(), any()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> brandChangeRequestService.create(SELLER_EMAIL, businessInfoRequest("김대표")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHANGE_REQUEST_VALUE_UNCHANGED);
    }

    @Test
    @DisplayName("사업자등록번호처럼 요청 유형에 속하지 않는 항목을 보내면 거부한다")
    void rejectsFieldNotBelongingToType() {
        CreateChangeRequestRequest request = new CreateChangeRequestRequest();
        request.setType(ChangeRequestType.BUSINESS_INFO);
        ChangeRequestItemRequest item = new ChangeRequestItemRequest();
        item.setFieldKey(ChangeRequestField.BANK_CODE); // SETTLEMENT_ACCOUNT 전용 항목
        item.setRequestedValue("004");
        request.setItems(List.of(item));
        request.setReason("사유");
        request.setEvidenceFileUrl("https://cdn.example.com/a.jpg");
        request.setEvidenceFileName("file.jpg");
        request.setEvidenceFileSize(1024L);

        assertThatThrownBy(() -> brandChangeRequestService.create(SELLER_EMAIL, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHANGE_REQUEST_FIELD_NOT_ALLOWED);
    }

    @Test
    @DisplayName("M1(BUSINESS_INFO)은 변경 사유가 없으면 거부한다")
    void requiresReasonForBusinessInfo() {
        CreateChangeRequestRequest request = businessInfoRequest("이대표");
        request.setReason(" ");

        assertThatThrownBy(() -> brandChangeRequestService.create(SELLER_EMAIL, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHANGE_REQUEST_REASON_REQUIRED);
    }

    @Test
    @DisplayName("M2(SETTLEMENT_ACCOUNT) 계좌번호는 하이픈 없는 10~16자리가 아니면 거부한다")
    void rejectsInvalidAccountNumberFormat() {
        CreateChangeRequestRequest request = settlementAccountRequest("004", "123-456", "홍길동");

        assertThatThrownBy(() -> brandChangeRequestService.create(SELLER_EMAIL, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("생성 시 current_value는 클라이언트 입력이 아니라 서버 스냅샷을 사용한다")
    void snapshotsCurrentValueFromServerState() {
        given(brandChangeRequestRepository.findByMarket_IdAndTypeAndStatus(any(), any(), any()))
                .willReturn(Optional.empty());
        given(brandChangeRequestRepository.countByRequestCodeStartingWith(anyString())).willReturn(0L);
        given(brandChangeRequestRepository.save(any(BrandChangeRequest.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        brandChangeRequestService.create(SELLER_EMAIL, businessInfoRequest("이대표"));

        ArgumentCaptor<BrandChangeRequest> captor = ArgumentCaptor.forClass(BrandChangeRequest.class);
        verify(brandChangeRequestRepository).save(captor.capture());
        BrandChangeRequest saved = captor.getValue();
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().get(0).getCurrentValue()).isEqualTo("김대표");
        assertThat(saved.getItems().get(0).getRequestedValue()).isEqualTo("이대표");
        assertThat(saved.getRequestCode()).startsWith("CHG-");
    }

    private CreateChangeRequestRequest settlementAccountRequest(String bankCode, String accountNumber, String holder) {
        CreateChangeRequestRequest request = new CreateChangeRequestRequest();
        request.setType(ChangeRequestType.SETTLEMENT_ACCOUNT);

        ChangeRequestItemRequest bankItem = new ChangeRequestItemRequest();
        bankItem.setFieldKey(ChangeRequestField.BANK_CODE);
        bankItem.setRequestedValue(bankCode);

        ChangeRequestItemRequest accountItem = new ChangeRequestItemRequest();
        accountItem.setFieldKey(ChangeRequestField.ACCOUNT_NUMBER);
        accountItem.setRequestedValue(accountNumber);

        ChangeRequestItemRequest holderItem = new ChangeRequestItemRequest();
        holderItem.setFieldKey(ChangeRequestField.ACCOUNT_HOLDER);
        holderItem.setRequestedValue(holder);

        request.setItems(List.of(bankItem, accountItem, holderItem));
        request.setEvidenceFileUrl("https://cdn.example.com/bankbook.jpg");
        request.setEvidenceFileName("bankbook.jpg");
        request.setEvidenceFileSize(2048L);
        return request;
    }

    private BrandChangeRequest mockPendingRequest() {
        return BrandChangeRequest.create("CHG-2026-0001", market, ChangeRequestType.BUSINESS_INFO,
                "사유", "김담당", "https://cdn.example.com/a.jpg", "a.jpg", 1024L);
    }
}
