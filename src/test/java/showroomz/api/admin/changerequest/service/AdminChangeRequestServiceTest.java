package showroomz.api.admin.changerequest.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import showroomz.api.admin.changerequest.dto.AdminChangeRequestDto;
import showroomz.api.seller.auth.repository.SellerRepository;
import showroomz.api.seller.auth.type.SellerStatus;
import showroomz.domain.changerequest.entity.BrandChangeRequest;
import showroomz.domain.changerequest.repository.BrandChangeRequestRepository;
import showroomz.domain.changerequest.type.ChangeRequestField;
import showroomz.domain.changerequest.type.ChangeRequestRejectReason;
import showroomz.domain.changerequest.type.ChangeRequestType;
import showroomz.domain.market.entity.Market;
import showroomz.domain.market.repository.MarketRepository;
import showroomz.domain.member.seller.entity.Seller;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.service.MailService;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminChangeRequestServiceTest {

    private static final long REQUEST_ID = 1L;
    private static final long PROCESSOR_ID = 99L;

    @Mock
    private BrandChangeRequestRepository brandChangeRequestRepository;
    @Mock
    private MarketRepository marketRepository;
    @Mock
    private SellerRepository sellerRepository;
    @Mock
    private MailService mailService;

    private AdminChangeRequestService adminChangeRequestService;

    private Seller seller;
    private Market market;

    @BeforeEach
    void setUp() {
        seller = new Seller("brand@showroomz.co.kr", "encoded", "김담당", "010-1234-5678", LocalDateTime.now());
        seller.setRepresentativeName("김대표");
        market = new Market(seller, "코코브라운", "02-1234-5678");

        adminChangeRequestService = new AdminChangeRequestService(
                brandChangeRequestRepository, marketRepository, sellerRepository,
                new ChangeRequestApplier(), mailService);
    }

    private BrandChangeRequest pendingBusinessInfoRequest(String requestedName) {
        BrandChangeRequest request = BrandChangeRequest.create(
                "CHG-2026-0001", market, ChangeRequestType.BUSINESS_INFO,
                "사유", "김담당", "https://cdn.example.com/a.jpg", "a.jpg", 1024L);
        request.addItem(ChangeRequestField.REPRESENTATIVE_NAME, "김대표", requestedName, 0);
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        return request;
    }

    @Test
    @DisplayName("승인하면 요청 항목이 Seller에 실제로 반영된다")
    void approveAppliesRequestedValuesToSeller() {
        BrandChangeRequest request = pendingBusinessInfoRequest("이대표");
        given(brandChangeRequestRepository.findById(REQUEST_ID)).willReturn(Optional.of(request));

        AdminChangeRequestDto.ProcessResponse response = adminChangeRequestService.approve(REQUEST_ID, PROCESSOR_ID);

        assertThat(seller.getRepresentativeName()).isEqualTo("이대표");
        assertThat(response.getStatus().name()).isEqualTo("APPROVED");
        assertThat(request.getProcessedBy()).isEqualTo(PROCESSOR_ID);
    }

    @Test
    @DisplayName("브랜드명 변경 승인 시 다른 브랜드가 선점한 이름이면 승인을 거부한다")
    void approveRejectsWhenMarketNameTakenByAnother() {
        BrandChangeRequest request = BrandChangeRequest.create(
                "CHG-2026-0002", market, ChangeRequestType.BUSINESS_INFO,
                "사유", "김담당", "https://cdn.example.com/a.jpg", "a.jpg", 1024L);
        request.addItem(ChangeRequestField.MARKET_NAME, "코코브라운", "새브랜드명", 0);
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);

        given(brandChangeRequestRepository.findById(REQUEST_ID)).willReturn(Optional.of(request));
        given(marketRepository.existsByMarketNameAndSellerStatusNotRejected(eq("새브랜드명"), eq(SellerStatus.REJECTED)))
                .willReturn(true);

        assertThatThrownBy(() -> adminChangeRequestService.approve(REQUEST_ID, PROCESSOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_MARKET_NAME);
        assertThat(market.getMarketName()).isEqualTo("코코브라운");
    }

    @Test
    @DisplayName("PENDING 상태가 아닌 요청은 승인/반려할 수 없다")
    void rejectsProcessingNonPendingRequest() {
        BrandChangeRequest request = pendingBusinessInfoRequest("이대표");
        request.approve(PROCESSOR_ID); // 이미 처리 완료 상태로 전이
        given(brandChangeRequestRepository.findById(REQUEST_ID)).willReturn(Optional.of(request));

        assertThatThrownBy(() -> adminChangeRequestService.approve(REQUEST_ID, PROCESSOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHANGE_REQUEST_NOT_PENDING);
    }

    @Test
    @DisplayName("반려 사유가 요청 유형에 맞지 않으면 거부한다")
    void rejectsRejectReasonNotSupportingType() {
        BrandChangeRequest request = pendingBusinessInfoRequest("이대표");
        given(brandChangeRequestRepository.findById(REQUEST_ID)).willReturn(Optional.of(request));

        AdminChangeRequestDto.RejectRequest rejectRequest =
                new AdminChangeRequestDto.RejectRequest(ChangeRequestRejectReason.BANKBOOK_MISSING, null);

        assertThatThrownBy(() -> adminChangeRequestService.reject(REQUEST_ID, PROCESSOR_ID, rejectRequest))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHANGE_REQUEST_REJECT_REASON_TYPE_MISMATCH);
    }

    @Test
    @DisplayName("기타(OTHER) 사유를 선택하면 상세 사유가 필수다")
    void requiresDetailWhenReasonIsOther() {
        BrandChangeRequest request = pendingBusinessInfoRequest("이대표");
        given(brandChangeRequestRepository.findById(REQUEST_ID)).willReturn(Optional.of(request));

        AdminChangeRequestDto.RejectRequest rejectRequest =
                new AdminChangeRequestDto.RejectRequest(ChangeRequestRejectReason.OTHER, "  ");

        assertThatThrownBy(() -> adminChangeRequestService.reject(REQUEST_ID, PROCESSOR_ID, rejectRequest))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHANGE_REQUEST_REJECT_DETAIL_REQUIRED);
    }

    @Test
    @DisplayName("정형 사유 반려가 정상 처리되면 상태가 REJECTED로 바뀌고 사유가 기록된다")
    void rejectSucceedsWithSupportedReason() {
        BrandChangeRequest request = pendingBusinessInfoRequest("이대표");
        given(brandChangeRequestRepository.findById(REQUEST_ID)).willReturn(Optional.of(request));

        AdminChangeRequestDto.RejectRequest rejectRequest =
                new AdminChangeRequestDto.RejectRequest(ChangeRequestRejectReason.REASON_INSUFFICIENT, null);

        AdminChangeRequestDto.ProcessResponse response = adminChangeRequestService.reject(REQUEST_ID, PROCESSOR_ID, rejectRequest);

        assertThat(response.getStatus().name()).isEqualTo("REJECTED");
        assertThat(request.getRejectReason()).isEqualTo("REASON_INSUFFICIENT");
    }
}
