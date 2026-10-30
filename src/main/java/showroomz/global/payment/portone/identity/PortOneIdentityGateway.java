package showroomz.global.payment.portone.identity;

import java.util.Optional;

/**
 * 포트원 V2 본인인증 조회 포트(57 설계서 0-11 · 2-1). 결제 {@code PortOnePaymentGateway} 와 분리한다 — 실패의 뜻이 다르고
 * 테스트 더블의 시나리오가 섞이면 둘 다 읽기 어렵다. 구현은 {@link PortOneIdentityV2Gateway}(SDK) · {@link FakeIdentityGateway}.
 */
public interface PortOneIdentityGateway {

    /** 앱이 SDK 창을 띄울 때 쓰는 상점 ID — 응답에 실어 내린다(앱에 박지 않는다). */
    String storeId();

    /** KCP 휴대폰 본인확인 채널 키 — 결제 채널과 다른 키다. */
    String channelKey();

    /**
     * 포트원 조회. 포트원이 모르는 id(SDK 창을 연 적 없음)는 {@code Optional.empty()}.
     *
     * @throws showroomz.global.payment.portone.PaymentGatewayException 타임아웃 · 통신 오류 · 해석 불가(결과 모름)
     * @throws showroomz.global.payment.portone.PaymentGatewayRejectedException 자격 · 채널 설정 오류(재시도 무의미)
     */
    Optional<IdentityVerificationResult> find(String identityVerificationId);
}
