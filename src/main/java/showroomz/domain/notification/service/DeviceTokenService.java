package showroomz.domain.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import showroomz.domain.member.user.entity.Users;
import showroomz.domain.notification.entity.DeviceToken;
import showroomz.domain.notification.repository.DeviceTokenRepository;
import showroomz.domain.notification.type.DevicePlatform;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * 기기 토큰 등록·해제.
 *
 * <p>등록은 <b>로그인 부수 작업</b>이라 실패해도 로그인을 깨뜨리지 않는다. 알림을 못 받는 것은
 * 불편이지만 로그인이 안 되는 것은 장애다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;

    /**
     * 로그인 시 호출 — 이미 있는 토큰이면 지금 로그인한 사람에게 소유권을 넘긴다.
     *
     * <p>지우고 다시 넣지 않는 이유 — 토큰에 유니크가 걸려 있어 같은 트랜잭션 안에서
     * DELETE 후 INSERT를 하면 플러시 순서에 따라 중복 키로 터진다.
     */
    @Transactional
    public void register(Users user, String token, String platform) {
        if (user == null || token == null || token.isBlank()) {
            return;
        }
        String trimmed = token.trim();
        if (trimmed.length() > DeviceToken.MAX_TOKEN_LENGTH) {
            log.warn("FCM 토큰 길이 초과로 등록하지 않음 - userId={}, length={}", user.getId(), trimmed.length());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        DevicePlatform devicePlatform = DevicePlatform.from(platform);

        deviceTokenRepository.findByToken(trimmed)
                .ifPresentOrElse(
                        existing -> existing.reassignTo(user, devicePlatform, now),
                        () -> deviceTokenRepository.save(new DeviceToken(user, trimmed, devicePlatform, now)));
    }

    /**
     * 로그아웃 시 호출 — 그 기기로만 알림을 끊는다.
     *
     * <p>회원의 토큰을 전부 지우지 않는다. 폰과 태블릿에 같은 계정으로 로그인해 둔 사람이
     * 한쪽에서 로그아웃했다고 다른 쪽 알림까지 끊기면 안 된다.
     */
    @Transactional
    public void unregister(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        deviceTokenRepository.deleteByToken(token.trim());
    }

    /** C15-4 탈퇴 — 남은 기기 전부에서 알림을 끊는다 */
    @Transactional
    public void unregisterAll(Users user) {
        deviceTokenRepository.deleteByUser(user);
    }

    /**
     * FCM이 "없는 토큰"이라고 답한 것들을 정리한다.
     *
     * <p>발송 트랜잭션과 분리하지 않는다 — 발송은 이미 끝났고 이 삭제는 다음 발송을 가볍게 하려는
     * 뒷정리라, 실패해도 다음 발송에서 같은 응답을 받아 다시 지울 기회가 온다.
     */
    @Transactional
    public void purgeInvalid(Collection<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        int deleted = deviceTokenRepository.deleteByTokenIn(tokens);
        log.info("만료 FCM 토큰 정리 - {}건", deleted);
    }
}
