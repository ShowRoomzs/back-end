package showroomz.api.app.setting.service;

import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.time.format.DateTimeFormatter;
import showroomz.domain.notification.type.NotificationType;
import showroomz.domain.notification.type.NotificationTargetType;
import showroomz.domain.notification.type.NotificationImageKind;
import showroomz.domain.notification.type.NotificationActorType;
import showroomz.domain.notification.service.NotificationPublisher;
import showroomz.domain.notification.service.NotificationCommand;
import showroomz.domain.member.user.type.NotificationToggle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import showroomz.api.app.auth.service.IdentityVerificationService;
import showroomz.api.app.auth.service.IdentityVerificationService.IdentityVerification;
import showroomz.api.app.setting.DTO.AccountInfoResponse;
import showroomz.api.app.setting.DTO.IdentityReverifyRequest;
import showroomz.api.app.setting.DTO.NotificationSettingRequest;
import showroomz.api.app.setting.DTO.NotificationSettingResponse;
import showroomz.api.app.user.repository.UserRepository;
import showroomz.domain.history.entity.UserConsentHistory;
import showroomz.domain.history.repository.UserConsentHistoryRepository;
import showroomz.domain.history.type.ConsentType;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.member.user.type.UserStatus;
import showroomz.domain.member.user.vo.NotificationSetting;
import showroomz.global.error.exception.BusinessException;
import showroomz.global.error.exception.ErrorCode;
import showroomz.global.utils.MaskingUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SettingService {

    private final UserRepository userRepository;
    private final UserConsentHistoryRepository userConsentHistoryRepository;
    private final IdentityVerificationService identityVerificationService;
    private final NotificationPublisher notificationPublisher;

    private static final DateTimeFormatter CONSENT_AT = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    /**
     * C15 알림 설정 조회.
     * 광고성 정보 수신은 가입 시 [선택] 동의(users.marketingAgree)를 그대로 보여준다.
     */
    @Transactional(readOnly = true)
    public NotificationSettingResponse getNotificationSettings(String username) {
        Users user = getActiveUser(username);

        NotificationSetting setting = user.getNotificationSetting();
        boolean followPostPushAgree = setting == null || setting.isFollowPostPushAgree();
        boolean followGroupBuy = setting != null && setting.isFollowGroupBuyPushAgree();
        boolean ending = setting != null && setting.isGroupBuyEndingPushAgree();

        List<NotificationSettingResponse.Toggle> toggles = new ArrayList<>();
        for (NotificationToggle toggle : NotificationToggle.values()) {
            boolean on;
            LocalDateTime agreedAt;
            if (toggle == NotificationToggle.BENEFIT) {
                on = user.isMarketingAgree();
                agreedAt = user.getMarketingAgreeChangedAt();
            } else if (setting == null) {
                on = toggle == NotificationToggle.FOLLOW_POST;
                agreedAt = null;
            } else {
                on = setting.isOn(toggle);
                agreedAt = setting.agreedAt(toggle);
            }
            toggles.add(new NotificationSettingResponse.Toggle(toggle, toggle.getLabel(), toggle.getDescription(), on,
                    on ? agreedAt : null));
        }
        return new NotificationSettingResponse(followPostPushAgree, user.isMarketingAgree(),
                user.getMarketingAgreeChangedAt(), followGroupBuy, ending, toggles);
    }

    /**
     * C15 알림 설정 변경.
     * 광고성 정보 수신은 값이 실제로 바뀐 경우에만 동의/철회 이력을 남긴다 — 같은 값을 다시 보냈다고
     * 철회 일시가 갱신되면 통지 근거가 어긋난다.
     */
    @Transactional
    public void updateNotificationSettings(String username, NotificationSettingRequest request) {
        Users user = getActiveUser(username);

        LocalDateTime now = LocalDateTime.now();
        Map<NotificationToggle, Boolean> requested = new LinkedHashMap<>();
        requested.put(NotificationToggle.FOLLOW_GROUP_BUY, request.getFollowGroupBuyPushAgree());
        requested.put(NotificationToggle.FOLLOW_POST, request.getFollowPostPushAgree());
        requested.put(NotificationToggle.GROUP_BUY_ENDING, request.getGroupBuyEndingPushAgree());
        requested.put(NotificationToggle.BENEFIT, request.getMarketingAgree());

        List<String> results = new ArrayList<>();
        for (Map.Entry<NotificationToggle, Boolean> entry : requested.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            NotificationToggle toggle = entry.getKey();
            boolean on = entry.getValue();
            // 켜면 광고성 수신 동의 — 값이 실제로 바뀔 때만 이력을 남긴다(같은 값 재전송으로 동의 일시가 바뀌면 근거가 어긋난다).
            if (user.updateNotificationToggle(toggle, on, now)) {
                userConsentHistoryRepository.save(UserConsentHistory.builder()
                        .user(user)
                        .consentType(toggle.getConsentType())
                        .agreed(on)
                        .build());
                results.add(toggle.getLabel() + (on ? " 수신에 동의" : " 수신을 철회"));
            }
        }
        // 처리 결과 통지(정보통신망법 §50 ④ · C15 「처리 결과를 알림으로 알려드려요」) — 한 번에 바꾼 토글은 한 건으로.
        if (!results.isEmpty() && notificationPublisher != null) {
            notificationPublisher.publish(NotificationCommand.toUser(user.getId(), NotificationType.MARKETING_CONSENT_CHANGED)
                    .actorType(NotificationActorType.SYSTEM)
                    .title(String.join(" · ", results) + "했어요 · " + now.format(CONSENT_AT))
                    .emphasis(results.get(0))
                    .imageKind(NotificationImageKind.ICON)
                    .targetType(NotificationTargetType.SETTINGS).build());
        }

        user.setModifiedAt(now);
    }

    /**
     * C15-2 회원정보 조회 — 본인인증 결과라 마스킹해서 내려준다.
     */
    @Transactional(readOnly = true)
    public AccountInfoResponse getAccountInfo(String username) {
        Users user = getActiveUser(username);

        return new AccountInfoResponse(
                MaskingUtils.maskName(user.getName()),
                MaskingUtils.maskBirthday(user.getBirthday()),
                MaskingUtils.maskPhoneNumber(user.getPhoneNumber()),
                user.getIdentityVerifiedAt()
        );
    }

    /**
     * C15-2 회원정보 변경 — PASS 재인증으로 이름·생년월일·성별·휴대폰번호를 갱신한다.
     * 값을 직접 입력받지 않고 인증 결과로 덮어쓰므로 요청 본문에는 동의 여부만 담긴다.
     */
    @Transactional
    public AccountInfoResponse reverifyIdentity(String username, IdentityReverifyRequest request) {
        Users user = getActiveUser(username);

        if (!request.isAgreeConsent()) {
            throw new BusinessException(ErrorCode.IDENTITY_CONSENT_REQUIRED);
        }

        // 동의는 가입 시 동의와 별개의 새 수집 행위라 매번 이력에 남긴다.
        userConsentHistoryRepository.save(UserConsentHistory.builder()
                .user(user)
                .consentType(ConsentType.IDENTITY_VERIFICATION)
                .agreed(true)
                .build());

        // TODO(PASS 연동): 인증 결과가 기존 명의와 다를 때(타인 명의) 처리 정책이 정해지면 여기서 분기한다.
        IdentityVerification verification = identityVerificationService.verify(username);
        user.updateIdentity(
                verification.getName(),
                verification.getBirthday(),
                verification.getGender(),
                verification.getPhoneNumber(),
                verification.getVerifiedAt()
        );

        return new AccountInfoResponse(
                MaskingUtils.maskName(user.getName()),
                MaskingUtils.maskBirthday(user.getBirthday()),
                MaskingUtils.maskPhoneNumber(user.getPhoneNumber()),
                user.getIdentityVerifiedAt()
        );
    }

    private Users getActiveUser(String username) {
        Users user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.USER_WITHDRAWN);
        }
        return user;
    }
}
