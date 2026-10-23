package showroomz.api.app.address.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import showroomz.domain.address.entity.DeliveryAddress;

public class DeliveryAddressDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "배송지 등록 · 수정 요청")
    public static class Request {
        
        @NotBlank(message = "수령인 이름은 필수입니다.")
        @Size(max = 64, message = "이름은 64자 이내여야 합니다.")
        @Schema(description = "수령인 이름 (최대 64자)", example = "홍길동")
        private String recipientName;

        @NotBlank(message = "우편번호는 필수입니다.")
        @Size(max = 10, message = "우편번호 형식이 올바르지 않습니다.")
        @Schema(description = "우편번호 (최대 10자)", example = "12345")
        private String zipCode;

        @NotBlank(message = "주소는 필수입니다.")
        @Size(max = 255, message = "주소는 255자 이내여야 합니다.")
        @Schema(description = "기본 주소 (최대 255자)", example = "서울특별시 강남구 테헤란로 123")
        private String address;

        @NotBlank(message = "상세 주소는 필수입니다.")
        @Size(max = 255, message = "상세 주소는 255자 이내여야 합니다.")
        @Schema(description = "상세 주소 (최대 255자)", example = "101동 101호")
        private String detailAddress;

        @NotBlank(message = "전화번호는 필수입니다.")
        @Pattern(regexp = "^\\d{2,3}-\\d{3,4}-\\d{4}$", message = "전화번호 형식이 올바르지 않습니다. (예: 010-1234-5678)")
        @Schema(description = "수령인 연락처 (하이픈 포함)", example = "010-1234-5678")
        private String phoneNumber;

        @Size(max = 50, message = "배송 메모는 50자 이내여야 합니다.")
        @Schema(description = "배송 메모 (선택 · 최대 50자) — 요청사항 프리셋 문구 또는 직접 입력한 문구. 없으면 null", example = "문 앞에 두세요")
        private String memo;

        @JsonProperty("isDefault")
        @Schema(description = "기본 배송지 지정 여부 (선택 · 기본값 false) — true면 기존 기본 배송지는 해제된다. "
                + "추가 시 첫 배송지는 값과 관계없이 기본 배송지가 되고, 수정 시 false를 보내면 기본 지정이 풀린다", example = "true")
        private boolean isDefault;
    }

    /** 배송지 추가 결과 — 방금 만든 배송지를 앱이 바로 고를 수 있게 id 를 내린다(C10 설계서 3-6). */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "배송지 추가 결과")
    public static class CreatedResponse {
        @Schema(description = "생성된 배송지 ID — 배송지 선택 화면(C13-2)이 방금 만든 주소를 선택 상태로 두는 데 쓴다", example = "55")
        private Long id;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "배송지 정보")
    public static class Response {
        @Schema(description = "배송지 ID", example = "1")
        private Long id;
        @Schema(description = "수령인 이름", example = "홍길동")
        private String recipientName;
        @Schema(description = "우편번호", example = "12345")
        private String zipCode;
        @Schema(description = "기본 주소", example = "서울특별시 강남구 테헤란로 123")
        private String address;
        @Schema(description = "상세 주소", example = "101동 101호")
        private String detailAddress;
        @Schema(description = "수령인 연락처 (하이픈 포함)", example = "010-1234-5678")
        private String phoneNumber;
        @Schema(description = "배송 메모 — 입력하지 않았으면 null", example = "문 앞에 두세요", nullable = true)
        private String memo;

        // @JsonProperty("isDefault") 제거: Getter(isDefault())를 통해 JSON 키가 "default"로 생성됨
        @Schema(description = "기본 배송지 여부 — JSON 키는 default", example = "true")
        private boolean isDefault;

        public static Response from(DeliveryAddress entity) {
            return Response.builder()
                    .id(entity.getId())
                    .recipientName(entity.getRecipientName())
                    .zipCode(entity.getZipCode())
                    .address(entity.getAddress())
                    .detailAddress(entity.getDetailAddress())
                    .phoneNumber(entity.getPhoneNumber())
                    .memo(entity.getMemo())
                    .isDefault(entity.isDefault())
                    .build();
        }
    }
}
