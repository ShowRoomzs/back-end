package showroomz.api.common.company.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import showroomz.global.config.properties.CompanyProperties;

/** 운영자 사업자 정보(1021 설계서 10-1 · 기-35) — 빈 값은 null 로 내린다(「[확인 필요]」 · 「신고 예정」은 FE). */
@Schema(description = "운영자(통신판매중개자) 사업자 정보")
public record CompanyInfoResponse(
        @Schema(description = "상호", example = "쇼룸즈") String name,
        @Schema(description = "대표자", example = "소병서") String representative,
        @Schema(description = "사업자등록번호", example = "791-12-03353") String registrationNumber,
        @Schema(description = "주소", example = "경기도 용인시 수지구 죽전로 152, 글로컬산학협력단 B211-3호(죽전동, 단국대학교)") String address,
        @Schema(description = "통신판매업 신고번호 — null 이면 「신고 예정」", nullable = true) String mailOrderReportNumber,
        @Schema(description = "개인정보 보호책임자") PrivacyOfficer privacyOfficer,
        @Schema(description = "고객센터 — 값이 정해지지 않았으면 각 필드 null") CustomerService customerService,
        @Schema(example = "© 2026 Showroomz") String copyright) {

    public record PrivacyOfficer(
            @Schema(example = "소병서", nullable = true) String name,
            @Schema(example = "010-3325-6416", nullable = true) String phone) {
    }

    public record CustomerService(
            @Schema(nullable = true) String phone,
            @Schema(nullable = true) String email,
            @Schema(description = "운영 시간 안내 문구", nullable = true) String hours) {
    }

    public static CompanyInfoResponse of(CompanyProperties p) {
        return new CompanyInfoResponse(blankToNull(p.getName()), blankToNull(p.getRepresentative()),
                blankToNull(p.getRegistrationNumber()), blankToNull(p.getAddress()),
                blankToNull(p.getMailOrderReportNumber()),
                new PrivacyOfficer(blankToNull(p.getPrivacyOfficer().getName()),
                        blankToNull(p.getPrivacyOfficer().getPhone())),
                new CustomerService(blankToNull(p.getCs().getPhone()), blankToNull(p.getCs().getEmail()),
                        blankToNull(p.getCs().getHours())),
                blankToNull(p.getCopyright()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
