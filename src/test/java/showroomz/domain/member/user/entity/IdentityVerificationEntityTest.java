package showroomz.domain.member.user.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import showroomz.api.app.auth.entity.ProviderType;
import showroomz.api.app.auth.entity.RoleType;
import showroomz.domain.member.user.type.IdentityFailReason;
import showroomz.domain.member.user.type.IdentityVerificationPurpose;
import showroomz.domain.member.user.type.IdentityVerificationStatus;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 본인인증 행 · 회원 행의 상태 전이와 개인정보 보존 규칙(57 테스트 시나리오 EN 절) — 통합 테스트가 시각 경계 · 길이 경계를 일일이
 * 재현하기 어려운 것만 여기서 본다.
 */
@DisplayName("[57 EN] 본인인증 엔티티 — 만료 경계 · 전이 · 개인정보 비움")
class IdentityVerificationEntityTest {

    private static final LocalDateTime REQUESTED = LocalDateTime.of(2026, 10, 24, 10, 0, 0);
    private static final LocalDateTime EXPIRES = REQUESTED.plusMinutes(10);

    private static IdentityVerification readyRow() {
        return IdentityVerification.ready("idv01TEST", 7L, IdentityVerificationPurpose.SIGNUP, "channel-key", REQUESTED, EXPIRES);
    }

    private static IdentityVerification verifiedRow() {
        IdentityVerification row = readyRow();
        row.verified("홍길동", "1998-04-12", "FEMALE", "01000000000", "SKT", false, "ci-enc", "di-enc", "pgtx", REQUESTED.plusMinutes(1));
        return row;
    }

    private static void assertPurged(IdentityVerification row) {
        assertThat(row.getVerifiedName()).isNull();
        assertThat(row.getVerifiedBirthDate()).isNull();
        assertThat(row.getVerifiedGender()).isNull();
        assertThat(row.getVerifiedPhone()).isNull();
        assertThat(row.getVerifiedOperator()).isNull();
        assertThat(row.getVerifiedForeigner()).isNull();
        assertThat(row.getVerifiedCiEnc()).isNull();
        assertThat(row.getVerifiedDiEnc()).isNull();
    }

    @Nested
    @DisplayName("생성 · 소유")
    class Creation {

        @Test
        @DisplayName("EN-01 ready — READY · 요청자 · 용도 · 채널 키 · 요청 · 만료 시각만 있고 개인정보는 없다")
        void readyHasNoPersonalData() {
            IdentityVerification row = readyRow();

            assertThat(row.getId()).isEqualTo("idv01TEST");
            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.READY);
            assertThat(row.getUserId()).isEqualTo(7L);
            assertThat(row.getPurpose()).isEqualTo(IdentityVerificationPurpose.SIGNUP);
            assertThat(row.getChannelKey()).isEqualTo("channel-key");
            assertThat(row.getRequestedAt()).isEqualTo(REQUESTED);
            assertThat(row.getExpiresAt()).isEqualTo(EXPIRES);
            assertThat(row.getFailReason()).isNull();
            assertPurged(row);
        }

        @Test
        @DisplayName("EN-02 isOwnedBy — 같은 회원만 true · 다른 회원 · null 은 false")
        void ownership() {
            IdentityVerification row = readyRow();

            assertThat(row.isOwnedBy(7L)).isTrue();
            assertThat(row.isOwnedBy(8L)).isFalse();
            assertThat(row.isOwnedBy(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("만료 경계 (0-7 — 저장하지 않고 읽을 때 판정)")
    class Expiry {

        @Test
        @DisplayName("EN-03 READY — 만료 1초 전은 유효 · 만료 시각 정각부터 만료")
        void readyBoundary() {
            IdentityVerification row = readyRow();

            assertThat(row.isExpired(EXPIRES.minusSeconds(1))).isFalse();
            assertThat(row.isExpired(EXPIRES)).isTrue();
            assertThat(row.isExpired(EXPIRES.plusSeconds(1))).isTrue();
        }

        @Test
        @DisplayName("EN-04 VERIFIED — 같은 경계 · isUsable 은 만료 전에만 true")
        void verifiedBoundary() {
            IdentityVerification row = verifiedRow();

            assertThat(row.isExpired(EXPIRES.minusSeconds(1))).isFalse();
            assertThat(row.isUsable(EXPIRES.minusSeconds(1))).isTrue();
            assertThat(row.isExpired(EXPIRES)).isTrue();
            assertThat(row.isUsable(EXPIRES)).isFalse();
        }

        @Test
        @DisplayName("EN-05 READY 는 만료 전이어도 소비할 수 없다(isUsable false)")
        void readyIsNotUsable() {
            assertThat(readyRow().isUsable(REQUESTED)).isFalse();
        }

        @Test
        @DisplayName("EN-06 FAILED · CONSUMED 는 만료로 읽지 않는다 — 이미 닫힌 행이다")
        void closedRowsNeverExpire() {
            IdentityVerification failed = readyRow();
            failed.failed(IdentityFailReason.PG_FAILED, "CS24", "msg", null);
            IdentityVerification consumed = verifiedRow();
            consumed.consume(REQUESTED.plusMinutes(2));

            assertThat(failed.isExpired(EXPIRES.plusDays(1))).isFalse();
            assertThat(consumed.isExpired(EXPIRES.plusDays(1))).isFalse();
            assertThat(consumed.isUsable(REQUESTED.plusMinutes(3))).isFalse();
        }
    }

    @Nested
    @DisplayName("전이")
    class Transitions {

        @Test
        @DisplayName("EN-07 verified — VERIFIED · 개인정보 · 암호문 · pgTxId · verifiedAt 채움")
        void verifiedFillsColumns() {
            IdentityVerification row = verifiedRow();

            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.VERIFIED);
            assertThat(row.getVerifiedName()).isEqualTo("홍길동");
            assertThat(row.getVerifiedBirthDate()).isEqualTo("1998-04-12");
            assertThat(row.getVerifiedGender()).isEqualTo("FEMALE");
            assertThat(row.getVerifiedPhone()).isEqualTo("01000000000");
            assertThat(row.getVerifiedOperator()).isEqualTo("SKT");
            assertThat(row.getVerifiedForeigner()).isFalse();
            assertThat(row.getVerifiedCiEnc()).isEqualTo("ci-enc");
            assertThat(row.getVerifiedDiEnc()).isEqualTo("di-enc");
            assertThat(row.getPgTxId()).isEqualTo("pgtx");
            assertThat(row.getVerifiedAt()).isEqualTo(REQUESTED.plusMinutes(1));
        }

        @Test
        @DisplayName("EN-08 failed — 앞서 VERIFIED 였어도 개인정보를 비운다 · 사유 · pg 코드 · pgTxId 보존")
        void failedPurgesEvenAfterVerified() {
            IdentityVerification row = verifiedRow();

            row.failed(IdentityFailReason.NOT_COMPLETED, null, null, null);

            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.FAILED);
            assertThat(row.getFailReason()).isEqualTo(IdentityFailReason.NOT_COMPLETED);
            assertThat(row.getPgTxId()).isEqualTo("pgtx");          // null 로 덮지 않는다
            assertPurged(row);
        }

        @Test
        @DisplayName("EN-09 failed — pg_code 64자 · pg_message 255자를 넘으면 자른다(DB 열 길이)")
        void failedTruncatesPgFields() {
            IdentityVerification row = readyRow();

            row.failed(IdentityFailReason.PG_FAILED, "C".repeat(100), "M".repeat(300), "pgtx-2");

            assertThat(row.getPgCode()).hasSize(64);
            assertThat(row.getPgMessage()).hasSize(255);
            assertThat(row.getPgTxId()).isEqualTo("pgtx-2");
        }

        @Test
        @DisplayName("EN-10 consume — CONSUMED · 소비 시각 · 개인정보 비움(값은 Users 로 갔다)")
        void consumePurges() {
            IdentityVerification row = verifiedRow();
            LocalDateTime at = REQUESTED.plusMinutes(3);

            row.consume(at);

            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.CONSUMED);
            assertThat(row.getConsumedAt()).isEqualTo(at);
            assertThat(row.getVerifiedAt()).isNotNull();             // 언제 인증했는지는 남는다
            assertPurged(row);
        }

        @Test
        @DisplayName("EN-11 purgePersonalData — 상태는 그대로 두고 개인정보만 지운다(탈퇴 · 보존 기간)")
        void purgeKeepsStatus() {
            IdentityVerification row = verifiedRow();

            row.purgePersonalData();

            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.VERIFIED);
            assertPurged(row);
        }
    }

    @Nested
    @DisplayName("앱이 보낸 SDK 값 기록 (3-2 — 판정에 쓰지 않는다)")
    class ClientResult {

        @Test
        @DisplayName("EN-12 txId · pgCode 기록 · code 는 pg_message 에 client: 접두로")
        void recordsClientValues() {
            IdentityVerification row = readyRow();

            row.recordClientResult("iv-tx-1", "FAILURE_TYPE_PG", "CS24");

            assertThat(row.getIdentityVerificationTxId()).isEqualTo("iv-tx-1");
            assertThat(row.getPgCode()).isEqualTo("CS24");
            assertThat(row.getPgMessage()).isEqualTo("client:FAILURE_TYPE_PG");
            assertThat(row.getStatus()).isEqualTo(IdentityVerificationStatus.READY);
        }

        @Test
        @DisplayName("EN-13 이미 있는 pg_code · pg_message 를 덮지 않는다 — 포트원 조회 결과가 정본")
        void doesNotOverwriteGatewayValues() {
            IdentityVerification row = readyRow();
            row.failed(IdentityFailReason.PG_FAILED, "CS24", "정보조회가 올바르지 않습니다.", null);

            row.recordClientResult("iv-tx-2", "OTHER", "CLIENT");

            assertThat(row.getPgCode()).isEqualTo("CS24");
            assertThat(row.getPgMessage()).isEqualTo("정보조회가 올바르지 않습니다.");
            assertThat(row.getIdentityVerificationTxId()).isEqualTo("iv-tx-2");
        }

        @Test
        @DisplayName("EN-14 빈 값 · null 은 무시 · 64자 넘는 txId 는 자른다")
        void ignoresBlankAndTruncates() {
            IdentityVerification row = readyRow();

            row.recordClientResult("  ", null, "");
            assertThat(row.getIdentityVerificationTxId()).isNull();
            assertThat(row.getPgCode()).isNull();
            assertThat(row.getPgMessage()).isNull();

            row.recordClientResult("T".repeat(80), null, null);
            assertThat(row.getIdentityVerificationTxId()).hasSize(64);
        }
    }

    @Nested
    @DisplayName("Users — 본인인증 반영 · 탈퇴 파기")
    class UsersIdentity {

        private Users user() {
            LocalDateTime now = LocalDateTime.now();
            return new Users("kim", "닉", "kim@showroomz.test", "Y", null, ProviderType.LOCAL, RoleType.USER, now, now);
        }

        @Test
        @DisplayName("EN-15 applyIdentity — 기존 5값 + 통신사 · 외국인 · CI · DI 암호문 · DI 해시")
        void applyIdentitySetsAll() {
            Users user = user();
            LocalDateTime at = LocalDateTime.of(2026, 10, 24, 10, 3);

            user.applyIdentity("홍길동", "1998-04-12", "FEMALE", "01000000000", "KT_MVNO", true, "ci-enc", "di-enc", "di-hash", at);

            assertThat(user.getName()).isEqualTo("홍길동");
            assertThat(user.getBirthday()).isEqualTo("1998-04-12");
            assertThat(user.getGender()).isEqualTo("FEMALE");
            assertThat(user.getPhoneNumber()).isEqualTo("01000000000");
            assertThat(user.getIdentityVerifiedAt()).isEqualTo(at);
            assertThat(user.getIdentityOperator()).isEqualTo("KT_MVNO");
            assertThat(user.getIdentityForeigner()).isTrue();
            assertThat(user.getIdentityCiEnc()).isEqualTo("ci-enc");
            assertThat(user.getIdentityDiEnc()).isEqualTo("di-enc");
            assertThat(user.getIdentityDiHash()).isEqualTo("di-hash");
        }

        @Test
        @DisplayName("EN-16 purgeOnWithdrawal — 본인인증 값과 CI · DI · 해시 · 통신사 · 외국인 여부를 모두 비운다")
        void withdrawalPurgesIdentity() {
            Users user = user();
            user.applyIdentity("홍길동", "1998-04-12", "FEMALE", "01000000000", "SKT", false, "ci-enc", "di-enc", "di-hash",
                    LocalDateTime.now());

            user.purgeOnWithdrawal();

            assertThat(user.getName()).isNull();
            assertThat(user.getBirthday()).isNull();
            assertThat(user.getGender()).isNull();
            assertThat(user.getPhoneNumber()).isNull();
            assertThat(user.getIdentityVerifiedAt()).isNull();
            assertThat(user.getIdentityCiEnc()).isNull();
            assertThat(user.getIdentityDiEnc()).isNull();
            assertThat(user.getIdentityDiHash()).isNull();
            assertThat(user.getIdentityOperator()).isNull();
            assertThat(user.getIdentityForeigner()).isNull();
        }
    }
}
