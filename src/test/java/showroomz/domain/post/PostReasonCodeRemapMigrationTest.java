package showroomz.domain.post;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import showroomz.domain.post.type.PostReportReason;
import showroomz.domain.post.type.PostSuspensionReason;

import java.sql.Connection;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V192 — 조치 · 신고 사유 코드 재매핑 (54 설계 2-6 · 54 테스트 시나리오 20-3 MG-01 ~ MG-03).
 *
 * <p>통합 테스트 DB는 Flyway를 끄고 엔티티로 스키마를 만든다(enum 열에 새 코드만 들어간다). 그래서 이 테스트는
 * <b>옛 코드가 들어갈 수 있는 최소 스키마</b>를 따로 만들고 V192 스크립트만 돌린다. 실 MySQL에서의 적용 ·
 * 엔티티 정합(MG-04)은 {@code dbMigrationTest}의 몫이다.
 */
@DisplayName("V192 사유 코드 재매핑")
class PostReasonCodeRemapMigrationTest {

    private static final String SCRIPT = "db/migration/V192__remap_post_reason_codes.sql";

    private JdbcTemplate jdbc;
    private DriverManagerDataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:v192-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE post_suspension (suspension_id BIGINT PRIMARY KEY, reason_code VARCHAR(40) NOT NULL, "
                + "reason_detail VARCHAR(500), policy_ref VARCHAR(200), policy_clause_code VARCHAR(40))");
        jdbc.execute("CREATE TABLE post_report (post_report_id BIGINT PRIMARY KEY, reason_code VARCHAR(40) NOT NULL, "
                + "reason_detail VARCHAR(500))");
        jdbc.execute("CREATE TABLE post_notification_log (log_id BIGINT PRIMARY KEY, payload TEXT)");
    }

    @Test
    @DisplayName("[MG-01] 조치 — 의료 효능은 허위 · 과장 + 3조 4항, 대가관계 · 과장 광고는 허위 · 과장, 저작권은 기타 + 접두, 폭력 · 혐오는 욕설 · 혐오")
    void remapsSuspensions() throws Exception {
        suspension(1, "MEDICAL_CLAIM", null, null);
        suspension(2, "MEDICAL_CLAIM", null, "운영정책 제12조");
        suspension(3, "AD_DISCLOSURE", null, null);
        suspension(4, "AD_DISCLOSURE", "협찬 표기 없음", null);
        suspension(5, "MISLEADING_AD", "가격 과장", null);
        suspension(6, "COPYRIGHT", null, null);
        suspension(7, "COPYRIGHT", "사진 도용", null);
        suspension(8, "COPYRIGHT", "", null);
        suspension(9, "VIOLENCE_HATE", null, null);
        suspension(10, "SEXUAL_CONTENT", null, null);
        suspension(11, "PERSONAL_INFO", "전화번호 노출", null);
        suspension(12, "OTHER", "직접 입력", null);

        migrate();

        assertSuspension(1, "MISLEADING_INFO", null, "커뮤니티 운영정책 3조 4항 — 의약품 · 의료기기 오인 표현", "ARTICLE_3_4");
        assertSuspension(2, "MISLEADING_INFO", null, "운영정책 제12조", "ARTICLE_3_4");
        assertSuspension(3, "MISLEADING_INFO", "대가관계 미표시(구 사유 코드)", null, null);
        assertSuspension(4, "MISLEADING_INFO", "협찬 표기 없음", null, null);
        assertSuspension(5, "MISLEADING_INFO", "가격 과장", null, null);
        assertSuspension(6, "OTHER", "저작권 침해(구 사유 코드)", null, null);
        assertSuspension(7, "OTHER", "저작권 침해 · 사진 도용", null, null);
        assertSuspension(8, "OTHER", "저작권 침해(구 사유 코드)", null, null);
        assertSuspension(9, "ABUSE_HATE", null, null, null);
        assertSuspension(10, "SEXUAL_CONTENT", null, null, null);
        assertSuspension(11, "PERSONAL_INFO", "전화번호 노출", null, null);
        assertSuspension(12, "OTHER", "직접 입력", null, null);
        assertThat(jdbc.queryForList("SELECT reason_code FROM post_suspension", String.class))
                .allSatisfy(code -> assertThat(Arrays.stream(PostSuspensionReason.values()).map(Enum::name)).contains(code));
    }

    @Test
    @DisplayName("[MG-01] 신고 — 개인정보 노출은 소비자 사유에서 빠져 기타 + 접두로 옮긴다 · 결과는 신고 사유 6종 안에 있다")
    void remapsReports() throws Exception {
        report(1, "MEDICAL_CLAIM", null);
        report(2, "AD_DISCLOSURE", null);
        report(3, "COPYRIGHT", "도용");
        report(4, "PERSONAL_INFO", null);
        report(5, "PERSONAL_INFO", "전화번호");
        report(6, "VIOLENCE_HATE", null);
        report(7, "MISLEADING_AD", null);
        report(8, "SEXUAL_CONTENT", null);

        migrate();

        assertReport(1, "MISLEADING_INFO", null);
        assertReport(2, "MISLEADING_INFO", "대가관계 미표시(구 사유 코드)");
        assertReport(3, "OTHER", "저작권 침해 · 도용");
        assertReport(4, "OTHER", "개인정보 노출(구 사유 코드)");
        assertReport(5, "OTHER", "개인정보 노출 · 전화번호");
        assertReport(6, "ABUSE_HATE", null);
        assertReport(7, "MISLEADING_INFO", null);
        assertReport(8, "SEXUAL_CONTENT", null);
        assertThat(jdbc.queryForList("SELECT reason_code FROM post_report", String.class))
                .allSatisfy(code -> assertThat(Arrays.stream(PostReportReason.values()).map(Enum::name)).contains(code));
    }

    @Test
    @DisplayName("[MG-02] 접두를 붙여도 상세는 500자에서 잘린다")
    void truncatesTo500() throws Exception {
        suspension(1, "COPYRIGHT", "가".repeat(500), null);
        report(1, "PERSONAL_INFO", "나".repeat(500));

        migrate();

        String suspensionDetail = jdbc.queryForObject("SELECT reason_detail FROM post_suspension", String.class);
        String reportDetail = jdbc.queryForObject("SELECT reason_detail FROM post_report", String.class);
        assertThat(suspensionDetail).hasSize(500).startsWith("저작권 침해 · ");
        assertThat(reportDetail).hasSize(500).startsWith("개인정보 노출 · ");
    }

    @Test
    @DisplayName("[MG-03] 알림 이력 payload의 옛 코드는 고치지 않는다 — 「그때 그 통지」 · 두 번 돌려도 접두가 겹치지 않는다")
    void leavesPayloadAndIsRepeatable() throws Exception {
        String payload = "{\"reasonCode\":\"MEDICAL_CLAIM\",\"reasonLabel\":\"의료적 효능 표현\"}";
        jdbc.update("INSERT INTO post_notification_log (log_id, payload) VALUES (1, ?)", payload);
        suspension(1, "COPYRIGHT", "사진 도용", null);

        migrate();
        migrate();

        assertThat(jdbc.queryForObject("SELECT payload FROM post_notification_log", String.class)).isEqualTo(payload);
        assertSuspension(1, "OTHER", "저작권 침해 · 사진 도용", null, null);
    }

    // ------------------------------------------------------------------ 보조

    private void migrate() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(SCRIPT));
        }
    }

    private void suspension(long id, String code, String detail, String policyRef) {
        jdbc.update("INSERT INTO post_suspension (suspension_id, reason_code, reason_detail, policy_ref) VALUES (?, ?, ?, ?)",
                id, code, detail, policyRef);
    }

    private void report(long id, String code, String detail) {
        jdbc.update("INSERT INTO post_report (post_report_id, reason_code, reason_detail) VALUES (?, ?, ?)", id, code, detail);
    }

    private void assertSuspension(long id, String code, String detail, String policyRef, String clause) {
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM post_suspension WHERE suspension_id = ?", id);
        assertThat(row).as("suspension " + id)
                .containsEntry("REASON_CODE", code)
                .containsEntry("REASON_DETAIL", detail)
                .containsEntry("POLICY_REF", policyRef)
                .containsEntry("POLICY_CLAUSE_CODE", clause);
    }

    private void assertReport(long id, String code, String detail) {
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM post_report WHERE post_report_id = ?", id);
        assertThat(row).as("report " + id).containsEntry("REASON_CODE", code).containsEntry("REASON_DETAIL", detail);
    }
}
