package showroomz.api.seller.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import showroomz.domain.order.type.ClaimReshipColumn;
import showroomz.domain.order.type.DeliveryCarrier;

import java.util.List;

/** 재발송(35 설계서 3-4) — 송장 등록 · 수정 · 목록 엑셀 · 업로드 파싱의 요청·응답. */
public final class SellerClaimReshipDto {

    private SellerClaimReshipDto() {
    }

    /** 재발송 송장 등록 확정 — 다건 · 행 단위 부분 성공. */
    public record RegisterRequest(
            @Valid @NotEmpty(message = "등록할 송장을 입력해 주세요.") @Size(max = 500) List<Item> items
    ) {
    }

    public record Item(
            @Schema(description = "클레임 id", example = "3021") @NotNull Long claimId,
            @Schema(description = "택배사", example = "CJ") DeliveryCarrier carrier,
            @Schema(description = "송장번호 — 숫자 외 문자는 서버가 지운다. 빈 값 행은 조용히 건너뛴다", example = "640012345678")
            String trackingNumber
    ) {
    }

    /** 재발송 송장 수정 — 재발송 중만. */
    public record UpdateRequest(
            @Schema(description = "택배사", example = "CJ") @NotNull(message = "택배사를 선택해 주세요.") DeliveryCarrier carrier,
            @Schema(description = "송장번호", example = "640012345678")
            @NotBlank(message = "송장번호를 입력해 주세요.") String trackingNumber
    ) {
    }

    /** 재발송 목록 다운로드(E1). */
    public record ExportRequest(
            @Schema(description = "선택 건 — 비어 있으면 내 마켓의 재발송 대기 전체", example = "[3021, 3022]", nullable = true)
            List<Long> claimIds,
            @Schema(description = "엑셀 열 구성 — 배열 순서 = 좌→우 열 순서 · 중복은 첫 위치만 남는다. 뒤에 빈 「택배사」 「송장번호」 "
                    + "2열이 항상 붙는다", example = "[\"CLAIM_NUMBER\", \"RECIPIENT\", \"PHONE\", \"ZIP_CODE\", \"ADDRESS\", \"PRODUCT_NAME\", \"OPTION\"]")
            @NotEmpty(message = "컬럼을 1개 이상 선택해 주세요.") List<ClaimReshipColumn> columns,
            @Schema(description = "이 구성을 기본값으로 저장 — 생략 시 저장하지 않음", example = "false", nullable = true)
            Boolean saveAsDefault
    ) {
    }

    public record TemplateUpdateRequest(
            @NotEmpty(message = "컬럼을 1개 이상 선택해 주세요.") List<ClaimReshipColumn> columns
    ) {
    }

    /** 재발송 목록의 컬럼 기본값 — 저장한 적 없으면 기본 7종. */
    public record TemplateResponse(
            @Schema(description = "미리 선택할 컬럼 — 순서 보존") List<ClaimReshipColumn> columns,
            @Schema(description = "고를 수 있는 컬럼 전체") List<Available> available
    ) {

        @Schema(name = "SellerClaimReshipColumnOption")
        public record Available(ClaimReshipColumn code, @Schema(example = "접수번호") String header,
                                @Schema(description = "기본 구성에 드는가") boolean basic) {
        }
    }

    /**
     * 재발송 송장 일괄 업로드 검증(E2) — <b>상태를 바꾸지 않는다.</b> FE 가 이 응답으로 셀만 채우고(「N건 목록에 채우기」),
     * 확정은 수기 입력과 같은 등록 API 다(확정 지점이 하나).
     */
    public record ParseResponse(
            @Schema(description = "읽은 행 수 — 머리글·빈 행 제외", example = "3") int totalRows,
            @Schema(description = "등록 가능 행 수 — 「N건 목록에 채우기」의 N", example = "2") int validRows,
            @Schema(description = "행별 분류 결과 — 엑셀 행 순서") List<ParsedRow> rows
    ) {
    }

    @Schema(name = "SellerClaimReshipParsedRow")
    public record ParsedRow(
            @Schema(description = "엑셀 행 번호 — 1부터 · 머리글 포함(첫 데이터 행 = 2)", example = "2") int rowNumber,
            @Schema(description = "접수번호 — 엑셀에 적힌 값", example = "CLM-3021") String claimNumber,
            @Schema(description = "매칭된 클레임 — 오류 행은 null", example = "3021", nullable = true) Long claimId,
            @Schema(description = "택배사 — 한글명·코드 모두 인식. 칸이 비었으면 null(FE 셀에서 고른 뒤 확정한다)", nullable = true)
            DeliveryCarrier carrier,
            @Schema(description = "송장번호 — 숫자만 남긴 값", example = "640012345678") String trackingNumber,
            @Schema(description = "등록 가능 여부") boolean valid,
            @Schema(description = "CLAIM_NOT_FOUND(없는 번호 · 내 마켓 것이 아님 — 같은 사유) · NOT_RESHIP_READY · "
                    + "CLAIM_DUPLICATE_IN_FILE · TRACKING_REQUIRED · CARRIER_INVALID · INVOICE_DUPLICATE — 정상 행은 null",
                    example = "NOT_RESHIP_READY", nullable = true) String errorCode,
            @Schema(description = "오류 안내 문구 — 정상 행은 null", nullable = true) String message
    ) {
    }
}
