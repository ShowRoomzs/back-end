package showroomz.api.admin.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import showroomz.api.admin.user.dto.AdminUserDto;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.history.entity.UserConsentHistory;
import showroomz.domain.history.repository.UserConsentHistoryRepository;
import showroomz.domain.history.type.ConsentType;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.terms.entity.TermsVersion;
import showroomz.domain.terms.repository.TermsVersionRepository;
import showroomz.domain.terms.type.TermsTarget;
import showroomz.domain.terms.type.TermsType;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.KstDates;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 어드민 소비자 약관 동의 내역(58 계획 5-29). 동의 기록({@code users} 동의 열 · {@code user_consent_history})에는 약관 버전이 없다 —
 * 버전은 「동의 시각에 시행 중이던 버전」(시행일 ≤ 동의 날짜 중 가장 늦은 것)으로 파생한다.
 *
 * <p>항목 대응: 이용 약관 · 개인정보처리방침 = 가입 동의(가입 시각) · 마케팅 동의 = {@link ConsentType#MARKETING}(마지막 변경 시각).
 * 「운영정책」은 열람 전용이라 넣지 않는다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserConsentReader {

    private static final List<TermsType> CONSENT_TYPES =
            List.of(TermsType.TERMS_OF_SERVICE, TermsType.PRIVACY_POLICY, TermsType.MARKETING_CONSENT);

    private final UserRepository userRepository;
    private final UserConsentHistoryRepository consentHistoryRepository;
    private final TermsVersionRepository termsVersionRepository;

    public AdminUserDto.ConsentList read(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<TermsVersion> versions = termsVersionRepository.findAllByDocumentTypesAndTargets(CONSENT_TYPES,
                Set.of(TermsTarget.USER, TermsTarget.ALL));

        LocalDateTime signedUpAt = user.getCreatedAt();
        LocalDateTime marketingAt = consentHistoryRepository
                .findFirstByUser_IdAndConsentTypeOrderByCreatedAtDescIdDesc(userId, ConsentType.MARKETING)
                .map(UserConsentHistory::getCreatedAt)
                .orElse(user.getMarketingAgreeChangedAt() != null ? user.getMarketingAgreeChangedAt()
                        : user.isMarketingAgree() ? signedUpAt : null);

        return new AdminUserDto.ConsentList(List.of(
                row(TermsType.TERMS_OF_SERVICE, user.isServiceAgree(), user.isServiceAgree() ? signedUpAt : null, versions),
                row(TermsType.PRIVACY_POLICY, user.isPrivacyAgree(), user.isPrivacyAgree() ? signedUpAt : null, versions),
                row(TermsType.MARKETING_CONSENT, user.isMarketingAgree(), marketingAt, versions)));
    }

    private static AdminUserDto.ConsentRow row(TermsType type, boolean agreed, LocalDateTime at, List<TermsVersion> versions) {
        TermsVersion inEffect = at == null ? null : inEffectOn(type, KstDates.toKstDate(at), versions);
        return new AdminUserDto.ConsentRow(type,
                inEffect != null ? inEffect.getDocument().getName() : type.getDisplayName(),
                inEffect != null ? inEffect.getVersionNumber() : null, agreed, at);
    }

    /** 시행일이 그 날짜 이하인 버전 중 가장 늦은 것 — 소비자 문서(USER)가 전체 문서(ALL)보다 앞선다. */
    private static TermsVersion inEffectOn(TermsType type, LocalDate date, List<TermsVersion> versions) {
        return versions.stream()
                .filter(v -> v.getDocument().getType() == type && !v.getEffectiveDate().isAfter(date))
                .max(Comparator.comparing(TermsVersion::getEffectiveDate)
                        .thenComparing(v -> v.getDocument().getTarget() == TermsTarget.USER)
                        .thenComparing(TermsVersion::getId))
                .orElse(null);
    }
}
