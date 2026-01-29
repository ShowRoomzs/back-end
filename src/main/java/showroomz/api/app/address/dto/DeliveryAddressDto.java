package showroomz.api.app.address.dto;

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
    public static class Request {
        
        @NotBlank(message = "수령인 이름은 필수입니다.")
        @Size(max = 64, message = "이름은 64자 이내여야 합니다.")
        private String recipientName;

        @NotBlank(message = "우편번호는 필수입니다.")
        @Size(max = 10, message = "우편번호 형식이 올바르지 않습니다.")
        private String zipCode;

        @NotBlank(message = "주소는 필수입니다.")
        @Size(max = 255, message = "주소는 255자 이내여야 합니다.")
        private String address;

        @NotBlank(message = "상세 주소는 필수입니다.")
        @Size(max = 255, message = "상세 주소는 255자 이내여야 합니다.")
        private String detailAddress;

        @NotBlank(message = "전화번호는 필수입니다.")
        @Pattern(regexp = "^\\d{2,3}-\\d{3,4}-\\d{4}$", message = "전화번호 형식이 올바르지 않습니다. (예: 010-1234-5678)")
        private String phoneNumber;

        @JsonProperty("isDefault")
        private boolean isDefault;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private String recipientName;
        private String zipCode;
        private String address;
        private String detailAddress;
        private String phoneNumber;
        @JsonProperty("isDefault")
        private boolean isDefault;

        public static Response from(DeliveryAddress entity) {
            return Response.builder()
                    .id(entity.getId())
                    .recipientName(entity.getRecipientName())
                    .zipCode(entity.getZipCode())
                    .address(entity.getAddress())
                    .detailAddress(entity.getDetailAddress())
                    .phoneNumber(entity.getPhoneNumber())
                    .isDefault(entity.isDefault())
                    .build();
        }
    }
}
