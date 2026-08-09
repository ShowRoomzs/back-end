package showroomz.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * 테스트 간 격리를 <b>트랜잭션 롤백이 아니라 테이블 비우기</b>로 만든다.
 *
 * <p>테스트에 {@code @Transactional}을 걸면 요청 처리가 테스트 트랜잭션에 묶여 세션이 계속 열려 있으므로,
 * 지연 로딩 실패나 커밋 시점 제약 위반 같은 <b>운영에서만 터지는 문제</b>가 테스트에서 드러나지 않는다.
 * 통합 테스트는 매 요청이 실제로 커밋되는 쪽을 택하고, 정리는 여기서 한다.
 */
class DatabaseCleaner {

    private final JdbcTemplate jdbcTemplate;
    private List<String> tableNames;

    DatabaseCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void clear() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            for (String tableName : tableNames()) {
                jdbcTemplate.execute("TRUNCATE TABLE " + tableName + " RESTART IDENTITY");
            }
        } finally {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    /** 엔티티 목록을 손으로 나열하면 새 엔티티가 생길 때마다 낡는다 — 스키마에서 직접 읽는다. */
    private List<String> tableNames() {
        if (tableNames == null) {
            tableNames = jdbcTemplate.queryForList(
                    "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES "
                            + "WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_TYPE = 'BASE TABLE'",
                    String.class);
        }
        return tableNames;
    }
}
