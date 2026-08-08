package showroomz.api.seller.connection.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import showroomz.api.app.auth.DTO.ErrorResponse;
import showroomz.api.seller.connection.dto.ConnectRequest;
import showroomz.api.seller.connection.dto.ConnectResponse;
import showroomz.api.seller.connection.dto.ConnectionCodeCheckResponse;
import showroomz.api.seller.connection.dto.ConnectionCreatorSearchItem;
import showroomz.global.dto.PageResponse;
import showroomz.global.dto.PagingRequest;

@Tag(name = "Seller - Connection", description = "파트너센터 연결 요청 API (§13)")
public interface SellerConnectionControllerDocs {

    @Operation(
            summary = "쇼룸명 검색",
            description = "탐색·추천 없이 쇼룸명 부분 일치로만 인플루언서를 찾는다(§13-6).\n\n" +
                    "**권한:** SELLER\n\n" +
                    "결과마다 현재 연결 상태(connectionStatus)를 함께 내려준다 — null이면 연결 이력 없음(요청 가능), " +
                    "REQUESTED/CONNECTED면 화면에서 [요청] 버튼 대신 상태 배지로 대체해 중복 요청을 애초에 막는다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "검색 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PageResponse<ConnectionCreatorSearchItem>> searchCreators(
            @Parameter(description = "쇼룸명 검색어(미입력 시 전체)") @RequestParam(value = "keyword", required = false) String keyword,
            @ModelAttribute PagingRequest pagingRequest
    );

    @Operation(
            summary = "연결코드 확인",
            description = "인플루언서에게 전달받은 연결코드가 유효한지 확인한다(§13-6).\n\n" +
                    "**권한:** SELLER\n\n" +
                    "일치하는 코드가 없어도 예외를 던지지 않고 found=false로 응답한다 — 오타 등으로 흔히 발생하는 " +
                    "정상적인 케이스라 오류 취급하지 않는다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "확인 완료(found로 존재 여부 판단)"),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ConnectionCodeCheckResponse> checkConnectionCode(
            @Parameter(description = "연결코드", required = true) @RequestParam("code") String code
    );

    @Operation(
            summary = "연결 요청",
            description = "creatorId 또는 connectionCode 중 정확히 하나로 상대를 지정해 연결을 요청한다(§13-6).\n\n" +
                    "**권한:** SELLER\n\n" +
                    "이미 CONNECTED/REQUESTED 상태면 409로 거부된다(검색·코드확인 응답의 상태값으로 화면에서 " +
                    "먼저 걸러지는 게 정상 흐름이며, 이 검증은 서버 측 최종 방어선이다)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "요청 생성/재요청 성공"),
            @ApiResponse(responseCode = "400", description = "creatorId/connectionCode 둘 다 없거나 둘 다 있음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "대상 인플루언서를 찾을 수 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 연결됨/요청중인 상대",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ConnectResponse> requestConnection(
            @Valid @RequestBody ConnectRequest request
    );
}
