package showroomz.global.payment.portone.identity;

import java.time.LocalDateTime;

/**
 * 포트원 본인인증 조회 결과(57 설계서 2-1) — SDK 타입을 서비스 계층에 흘리지 않기 위한 우리 모델.
 * {@code customer} 는 VERIFIED 일 때만, {@code failure} 는 FAILED 일 때만 있다.
 */
public record IdentityVerificationResult(
        String id,
        Status status,
        VerifiedCustomer customer,
        Failure failure,
        String pgTxId,
        LocalDateTime verifiedAt) {

    public enum Status { READY, VERIFIED, FAILED }

    /** 가이드(R-2): phoneNumber 는 숫자만 · birthDate 는 yyyy-MM-dd · gender 는 MALE · FEMALE · OTHER. CI · DI 는 로그에 싣지 않는다. */
    public record VerifiedCustomer(String name, String birthDate, String gender, String phoneNumber,
                                   String operator, Boolean foreigner, String ci, String di) {
        @Override
        public String toString() {
            return "VerifiedCustomer[masked]";
        }
    }

    public record Failure(String reason, String pgCode, String pgMessage) {}

    public static IdentityVerificationResult ready(String id) {
        return new IdentityVerificationResult(id, Status.READY, null, null, null, null);
    }

    public static IdentityVerificationResult verified(String id, VerifiedCustomer customer, String pgTxId, LocalDateTime verifiedAt) {
        return new IdentityVerificationResult(id, Status.VERIFIED, customer, null, pgTxId, verifiedAt);
    }

    public static IdentityVerificationResult failed(String id, Failure failure, String pgTxId) {
        return new IdentityVerificationResult(id, Status.FAILED, null, failure, pgTxId, null);
    }

    public boolean isVerified() {
        return status == Status.VERIFIED;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }
}
