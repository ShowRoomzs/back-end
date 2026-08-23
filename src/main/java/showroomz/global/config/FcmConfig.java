package showroomz.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import showroomz.domain.notification.infra.FcmPushSender;
import showroomz.domain.notification.service.LoggingPushSender;
import showroomz.domain.notification.service.PushSender;
import showroomz.global.config.properties.FcmProperties;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;

/**
 * 발송 어댑터를 <b>기동 시 한 번</b> 정한다.
 *
 * <p>{@code @ConditionalOnBean}/{@code @ConditionalOnMissingBean}으로 갈라 끼우지 않는 이유 —
 * 사용자 설정에서 그 조건들은 빈 정의 순서에 좌우된다. 설정을 제대로 넣었는데도 로그 어댑터가
 * 잡히면 "알림이 안 나가는데 에러도 없는" 상태가 되고, 원인을 찾기 매우 어렵다. 자격 증명이
 * 있는지 여기서 직접 보고 둘 중 하나만 만든다.
 *
 * <p>자격 증명이 없어도 <b>기동은 성공한다.</b> 로컬 개발과 CI에서 FCM 서비스 계정을 요구하면
 * 알림과 무관한 작업까지 막힌다.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class FcmConfig {

    private final FcmProperties fcmProperties;
    private final ResourceLoader resourceLoader = new DefaultResourceLoader();

    @Bean
    public PushSender pushSender() {
        if (!fcmProperties.isEnabled()) {
            log.warn("FCM 발송 비활성화(fcm.enabled=false) - 푸시는 로그로만 남는다");
            return new LoggingPushSender();
        }

        GoogleCredentials credentials = loadCredentials();
        if (credentials == null) {
            log.warn("FCM 자격 증명 없음(fcm.credentials-path / fcm.credentials-base64) - 푸시는 로그로만 남는다");
            return new LoggingPushSender();
        }

        FirebaseApp app = initializeApp(credentials);
        log.info("FCM 발송 활성화 - dryRun={}", fcmProperties.isDryRun());
        return new FcmPushSender(FirebaseMessaging.getInstance(app), fcmProperties.isDryRun());
    }

    /**
     * base64를 파일 경로보다 먼저 본다 — 운영에서 두 값이 모두 들어간 경우 환경변수 쪽이 최신이다.
     *
     * <p>읽기에 실패하면 예외를 올리지 않고 null을 돌려준다. 잘못된 자격 증명 때문에 서버 전체가
     * 뜨지 않는 것보다, 알림만 나가지 않고 나머지가 도는 편이 낫다. 대신 ERROR로 남긴다.
     */
    private GoogleCredentials loadCredentials() {
        try {
            String base64 = fcmProperties.getCredentialsBase64();
            if (base64 != null && !base64.isBlank()) {
                byte[] json = Base64.getDecoder().decode(base64.trim());
                return GoogleCredentials.fromStream(new ByteArrayInputStream(json));
            }

            String path = fcmProperties.getCredentialsPath();
            if (path == null || path.isBlank()) {
                return null;
            }
            Resource resource = resourceLoader.getResource(path.trim());
            if (!resource.exists()) {
                log.error("FCM 자격 증명 파일을 찾을 수 없다 - path={}", path);
                return null;
            }
            try (InputStream in = resource.getInputStream()) {
                return GoogleCredentials.fromStream(in);
            }
        } catch (IllegalArgumentException e) {
            log.error("FCM 자격 증명 base64 디코딩 실패 - 값 자체를 확인해야 한다", e);
            return null;
        } catch (Exception e) {
            log.error("FCM 자격 증명 로드 실패", e);
            return null;
        }
    }

    /**
     * 이미 초기화돼 있으면 그것을 쓴다.
     *
     * <p>{@code FirebaseApp}은 이름 기준 전역 레지스트리라 같은 이름으로 두 번 초기화하면 예외가 난다.
     * devtools 재기동과 통합 테스트의 컨텍스트 재생성이 정확히 그 상황을 만든다.
     */
    private FirebaseApp initializeApp(GoogleCredentials credentials) {
        for (FirebaseApp existing : FirebaseApp.getApps()) {
            if (FirebaseApp.DEFAULT_APP_NAME.equals(existing.getName())) {
                return existing;
            }
        }
        return FirebaseApp.initializeApp(FirebaseOptions.builder()
                .setCredentials(credentials)
                .build());
    }
}
