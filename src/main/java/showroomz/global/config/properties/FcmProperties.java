package showroomz.global.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * FCM 발송 설정.
 *
 * <p>자격 증명을 <b>두 가지 방법</b>으로 받는다 — 파일 경로와 base64 문자열이다. 로컬은 서비스 계정
 * JSON을 파일로 두는 게 편하고, 운영은 EC2에 비밀 파일을 올리는 대신 환경변수 하나로 끝내는 게
 * 안전하다. 둘 다 없으면 발송하지 않고 로그만 남긴다(서버는 정상 기동한다).
 *
 * <p>서비스 계정 JSON은 <b>절대 저장소에 넣지 않는다.</b> 그 키 하나로 프로젝트의 모든 사용자에게
 * 알림을 보낼 수 있다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "fcm")
public class FcmProperties {

    /** false면 자격 증명이 있어도 발송하지 않는다 — 사고 시 코드 배포 없이 끄는 스위치 */
    private boolean enabled = true;

    /** 서비스 계정 JSON 위치. {@code classpath:} · {@code file:} 접두사를 쓸 수 있다 */
    private String credentialsPath;

    /** 서비스 계정 JSON을 base64로 인코딩한 값 — 환경변수 배포용. {@link #credentialsPath}보다 우선한다 */
    private String credentialsBase64;

    /**
     * FCM에 요청은 보내되 실제 기기로는 전달하지 않는다(검증 모드).
     *
     * <p>토큰 유효성과 페이로드 형식은 그대로 검증되므로, 운영 데이터로 발송 경로를 확인하면서
     * 사용자에게 알림이 가지는 않게 할 때 쓴다.
     */
    private boolean dryRun = false;
}
