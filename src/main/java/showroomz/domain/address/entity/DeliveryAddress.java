package showroomz.domain.address.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import showroomz.domain.common.BaseTimeEntity;
import showroomz.domain.member.user.entity.Users;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "DELIVERY_ADDRESS")
public class DeliveryAddress extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ADDRESS_ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    private Users user;

    @Column(name = "RECIPIENT_NAME", length = 64, nullable = false)
    private String recipientName; // 수령인 이름

    @Column(name = "ZIP_CODE", length = 10, nullable = false)
    private String zipCode;

    @Column(name = "ADDRESS", length = 255, nullable = false)
    private String address;

    @Column(name = "DETAIL_ADDRESS", length = 255, nullable = false)
    private String detailAddress;

    @Column(name = "PHONE_NUMBER", length = 20, nullable = false)
    private String phoneNumber;

    @Column(name = "IS_DEFAULT", nullable = false)
    private boolean isDefault;

    @Builder
    public DeliveryAddress(Users user, String recipientName, String zipCode, String address, String detailAddress, String phoneNumber, boolean isDefault) {
        this.user = user;
        this.recipientName = recipientName;
        this.zipCode = zipCode;
        this.address = address;
        this.detailAddress = detailAddress;
        this.phoneNumber = phoneNumber;
        this.isDefault = isDefault;
    }

    // 정보 수정을 위한 메서드
    public void updateAddress(String recipientName, String zipCode, String address, String detailAddress, String phoneNumber, boolean isDefault) {
        this.recipientName = recipientName;
        this.zipCode = zipCode;
        this.address = address;
        this.detailAddress = detailAddress;
        this.phoneNumber = phoneNumber;
        this.isDefault = isDefault;
    }

    // 기본 배송지 설정/해제 편의 메서드
    public void updateDefaultStatus(boolean isDefault) {
        this.isDefault = isDefault;
    }
}
