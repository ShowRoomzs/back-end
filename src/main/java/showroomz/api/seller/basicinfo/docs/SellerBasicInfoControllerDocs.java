package showroomz.api.seller.basicinfo.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import showroomz.api.seller.basicinfo.dto.SellerBasicInfoDto;

@Tag(name = "Seller - Basic Info", description = "파트너센터 기본정보 관리 API (§15) — 사업자 정보·정산 계좌·담당자·CS·계정 4개 서브탭")
public interface SellerBasicInfoControllerDocs {

    @Operation(summary = "사업자 정보 조회", description = "조회 전용 필드 + 심사 첨부 3종 + 현재 변경 요청 배너를 함께 내려준다.\n\n**권한:** SELLER")
    ResponseEntity<SellerBasicInfoDto.BusinessInfoResponse> getBusinessInfo();

    @Operation(
            summary = "사업자 정보 직접 수정",
            description = "3분류 중 ①직접 수정 항목만 다룬다 — tax 확인용 이메일, 브랜드 사이트 링크.\n\n**권한:** SELLER"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "저장 성공"),
            @ApiResponse(responseCode = "400", description = "이메일 형식 오류 또는 http(s):// 누락")
    })
    ResponseEntity<Void> updateBusinessInfo(@Valid @RequestBody SellerBasicInfoDto.UpdateBusinessInfoRequest request);

    @Operation(
            summary = "정산 계좌 조회",
            description = "계좌번호는 뒤 6자리만 노출한다(§16-7, 파트너센터 응답에만 적용). 은행 셀렉트는 GET /v1/common/banks를 사용한다.\n\n**권한:** SELLER"
    )
    ResponseEntity<SellerBasicInfoDto.SettlementInfoResponse> getSettlementInfo();

    @Operation(summary = "담당자·CS 조회", description = "판매 담당자·고객센터 번호 + 반품 수취 주소 4필드.\n\n**권한:** SELLER")
    ResponseEntity<SellerBasicInfoDto.ManagerInfoResponse> getManagerInfo();

    @Operation(summary = "담당자·CS 일괄 저장", description = "7필드를 한 번에 저장한다(직접 수정, ①분류).\n\n**권한:** SELLER")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "저장 성공"),
            @ApiResponse(responseCode = "400", description = "연락처·전화번호 형식 오류")
    })
    ResponseEntity<Void> updateManagerInfo(@Valid @RequestBody SellerBasicInfoDto.UpdateManagerInfoRequest request);

    @Operation(summary = "계정 정보 조회", description = "로그인 이메일과 다음 변경 가능일을 내려준다.\n\n**권한:** SELLER")
    ResponseEntity<SellerBasicInfoDto.AccountInfoResponse> getAccountInfo();

    @Operation(
            summary = "비밀번호 변경",
            description = "현재 비밀번호 검증 후 변경한다.\n\n**권한:** SELLER"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "변경 성공"),
            @ApiResponse(responseCode = "401", description = "현재 비밀번호 불일치(LOGIN_PASSWORD_MISMATCH)"),
            @ApiResponse(responseCode = "400", description = "새 비밀번호 확인 불일치 또는 형식 오류")
    })
    ResponseEntity<Void> changePassword(@Valid @RequestBody SellerBasicInfoDto.ChangePasswordRequest request);

    @Operation(
            summary = "로그인 이메일 변경",
            description = "1개월 롤링 제한(§15-5) — 마지막 변경일로부터 1개월 이내면 400(EMAIL_CHANGE_LIMIT_EXCEEDED)이다. " +
                    "성공 시 구 이메일로 변경 통지 메일이 발송되고 기존 리프레시 토큰이 삭제된다(재로그인 필요).\n\n**권한:** SELLER"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공(갱신된 계정 정보 4필드 반환)"),
            @ApiResponse(responseCode = "401", description = "현재 비밀번호 불일치"),
            @ApiResponse(responseCode = "400", description = "월 1회 제한 초과 또는 이메일 중복")
    })
    ResponseEntity<SellerBasicInfoDto.AccountInfoResponse> changeEmail(@Valid @RequestBody SellerBasicInfoDto.ChangeEmailRequest request);
}
