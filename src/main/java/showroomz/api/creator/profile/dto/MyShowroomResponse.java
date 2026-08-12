package showroomz.api.creator.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import showroomz.domain.market.type.SnsType;
import showroomz.domain.member.creator.entity.Creator;
import showroomz.domain.member.creator.type.CreatorBusinessType;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "내 쇼룸 정보 조회 응답")
public class MyShowroomResponse {

    @Schema(description = "쇼룸(크리에이터) ID", example = "5")
    private final Long creatorId;

    @Schema(description = "쇼룸명", example = "감성 룩북")
    private final String showroomName;

    @Schema(description = "SNS 플랫폼 유형", example = "INSTAGRAM")
    private final SnsType snsType;

    @Schema(description = "채널 URL", example = "https://instagram.com/example")
    private final String channelUrl;

    @Schema(description = "SNS 계정 아이디", example = "my_channel")
    private final String accountId;

    @Schema(description = "팔로워 수", example = "12000")
    private final Integer followerCount;

    @Schema(description = "비즈니스 이메일", example = "creator@example.com")
    private final String businessEmail;

    @Schema(description = "본인확인 실명", example = "홍길동")
    private final String realName;

    @Schema(description = "생년월일", example = "1995-01-01")
    private final String birthday;

    @Schema(description = "휴대폰 번호", example = "010-1234-5678")
    private final String phoneNumber;

    @Schema(description = "사업자 유형", example = "INDIVIDUAL")
    private final CreatorBusinessType businessType;

    @Schema(description = "사업자등록번호 (사업자인 경우)", example = "123-45-67890")
    private final String businessRegistrationNumber;

    @Schema(description = "사업자등록증 이미지 URL (사업자인 경우)")
    private final String businessLicenseImageUrl;

    @Schema(description = "은행명", example = "국민은행")
    private final String bankName;

    @Schema(description = "계좌번호 (뒤 6자리만 노출)", example = "********1234")
    private final String maskedAccountNumber;

    @Schema(description = "통장 사본 이미지 URL")
    private final String bankbookImageUrl;

    @Schema(description = "연결코드", example = "AB3K7M9X")
    private final String connectionCode;

    @Schema(description = "가입일", example = "2026-01-01T10:00:00")
    private final LocalDateTime createdAt;

    public static MyShowroomResponse from(Creator creator) {
        return MyShowroomResponse.builder()
                .creatorId(creator.getId())
                .showroomName(creator.getShowroomName())
                .snsType(creator.getSnsType())
                .channelUrl(creator.getChannelUrl())
                .accountId(creator.getAccountId())
                .followerCount(creator.getFollowerCount())
                .businessEmail(creator.getBusinessEmail())
                .realName(creator.getRealName())
                .birthday(creator.getBirthday())
                .phoneNumber(creator.getPhoneNumber())
                .businessType(creator.getBusinessType())
                .businessRegistrationNumber(creator.getBusinessRegistrationNumber())
                .businessLicenseImageUrl(creator.getBusinessLicenseImageUrl())
                .bankName(creator.getBankName())
                .maskedAccountNumber(maskAccountNumber(creator.getAccountNumber()))
                .bankbookImageUrl(creator.getBankbookImageUrl())
                .connectionCode(creator.getConnectionCode())
                .createdAt(creator.getCreatedAt())
                .build();
    }

    /** 계좌번호 뒤 6자리만 남기고 마스킹한다(§16-7 파트너센터 마스킹 정책과 동일 기준). */
    private static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null) {
            return null;
        }
        int visibleSuffixLength = 6;
        if (accountNumber.length() <= visibleSuffixLength) {
            return "*".repeat(accountNumber.length());
        }
        int maskedLength = accountNumber.length() - visibleSuffixLength;
        return "*".repeat(maskedLength) + accountNumber.substring(maskedLength);
    }
}
