package showroomz.api.admin.delivery.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import showroomz.api.admin.delivery.dto.RemoteAreaPostcodeDto;
import showroomz.api.app.auth.DTO.ErrorResponse;

import java.util.List;

@Tag(name = "Admin - Remote Area Postcode", description = "관리자 추가 배송비 권역(도서산간 · 제주) 우편번호 API")
public interface AdminRemoteAreaPostcodeControllerDocs {

    @Operation(summary = "권역 우편번호 목록",
            description = """
                    등록된 우편번호 범위 전체 — 시작 우편번호 순. 플랫폼 단일 목록이다(브랜드별 목록 없음 · 55 계획서 D4).

                    체크아웃의 배송 권역은 배송지 우편번호로 정한다 — **제주(63000 ~ 63644)는 코드가 알고**(등록이 비어도 제주 추가비는 빠지지 않는다),
                    도서산간은 이 목록만 본다. 두 권역이 겹치면 제주가 우선이다. 추가비 금액은 브랜드가 06 배송 정책에서 정한다
                    (`jejuSurcharge` · `remoteAreaSurcharge`).

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<List<RemoteAreaPostcodeDto.Item>> list();

    @Operation(summary = "권역 우편번호 범위 등록",
            description = """
                    범위 1건을 등록한다. 번호 하나면 시작 = 끝.

                    - 우편번호는 5자리 숫자 · 시작 ≤ 끝 · 권역은 `REMOTE`(도서산간) · `JEJU`(제주)만
                    - 등록된 범위와 한 번호라도 겹치면 409 — 바꾸려면 지우고 다시 등록한다
                    - `REMOTE` 범위가 제주 기본 범위(63000 ~ 63644)와 겹쳐도 409 — 겹치면 제주가 우선이라 적용되지 않는다
                    - **새 주문부터 반영된다** — 권역 · 추가비는 결제 시점 하위주문 스냅샷이라 이미 결제된 주문의 배송비는 바뀌지 않는다

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공",
                    content = @Content(schema = @Schema(implementation = RemoteAreaPostcodeDto.Item.class), examples = @ExampleObject(value = """
                            {"id": 12, "postcodeFrom": "40200", "postcodeTo": "40240", "region": "REMOTE", "regionLabel": "도서산간", "name": "경북 울릉군", "createdAt": "2026-10-24T10:00:00"}
                            """))),
            @ApiResponse(responseCode = "400", description = "우편번호 형식(5자리 숫자 아님) · 시작 > 끝 · 권역 누락 · STANDARD · 이름 100자 초과 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "등록된 범위 · 제주 기본 범위와 겹침 (`REMOTE_AREA_POSTCODE_OVERLAP`) — 메시지에 겹친 범위가 있다",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<RemoteAreaPostcodeDto.Item> create(RemoteAreaPostcodeDto.CreateRequest request);

    @Operation(summary = "권역 우편번호 CSV 일괄 등록",
            description = """
                    초기 목록 · 대량 갱신용. **한 행이라도 틀리면 하나도 등록하지 않는다**(전부 또는 없음). 검증은 1건 등록과 같고, 파일 안의 행끼리 겹쳐도 409 다.

                    **multipart/form-data** — 파트 `file`(CSV)

                    | 칸 | 규칙 |
                    |---|---|
                    | 1 시작 우편번호 | 5자리 숫자 · 필수 |
                    | 2 끝 우편번호 | 5자리 숫자 · 비면 시작과 같다(번호 하나) |
                    | 3 권역 | `REMOTE` · `JEJU` · `도서산간` · `제주` · 비면 도서산간 |
                    | 4 이름 | 100자 · 선택 · 쉼표가 있으면 큰따옴표로 감싼다 |

                    - 첫 줄의 첫 칸에 숫자가 없으면 머리글로 보고 건너뛴다 · 빈 줄은 건너뛴다 · 최대 5,000행
                    - 인코딩은 UTF-8(BOM 허용), UTF-8 이 아니면 엑셀 기본 저장(MS949)으로 읽는다
                    - 오류 메시지는 「3행: …」처럼 파일의 줄 번호를 단다

                    **권한:** ADMIN
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공 — 파일 순서",
                    content = @Content(schema = @Schema(implementation = RemoteAreaPostcodeDto.BulkResult.class))),
            @ApiResponse(responseCode = "400", description = "파일 없음 · 등록할 행 없음 · 5,000행 초과 · 행 형식 오류 (`INVALID_INPUT`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "파일 안 · 등록된 범위 · 제주 기본 범위와 겹침 (`REMOTE_AREA_POSTCODE_OVERLAP`)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<RemoteAreaPostcodeDto.BulkResult> bulkCreate(@Parameter(description = "CSV 파일") MultipartFile file);

    @Operation(summary = "권역 우편번호 범위 삭제",
            description = "범위 1건을 지운다. 새 주문부터 일반 권역이 된다(이미 결제된 주문의 배송비는 그대로). 제주 기본 범위는 코드가 알아 지워도 제주로 판정된다.\n\n**권한:** ADMIN")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "400", description = "id 형식 오류 (`INVALID_INPUT`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "없는 범위 (`REMOTE_AREA_POSTCODE_NOT_FOUND`)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> delete(@Parameter(description = "범위 ID", example = "12") Long id);
}
