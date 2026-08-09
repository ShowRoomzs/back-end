package showroomz.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * HTTP 진입점부터 DB까지 한 번에 태우는 통합 테스트 표식.
 *
 * <p><b>왜 프로퍼티를 여기서 못 박는가</b> — 스키마 검증용 CI 잡(`./gradlew integrationTest`)은
 * `SPRING_DATASOURCE_URL` 환경변수로 실 MySQL을 가리킨다. 환경변수는 `application-test.yml`보다
 * 우선순위가 높아서 yml에 datasource를 적으면 CI에서 조용히 MySQL로 붙는다. 반면 애노테이션의
 * {@code properties}는 환경변수보다 우선하므로, 여기 적어야 로컬·CI 어디서든 같은 인메모리 DB로 돈다.
 *
 * <p><b>H2 옵션</b> — 엔티티 테이블명이 `SELLER`/`MARKET`(대문자)인데 어드민 목록 네이티브 쿼리는
 * `brand_change_request`/`market`(소문자)을 쓴다. `DATABASE_TO_LOWER`+`CASE_INSENSITIVE_IDENTIFIERS`가
 * 둘을 같은 테이블로 해석해준다.
 *
 * <p><b>Flyway를 끄고 `create-drop`을 쓰는 이유</b> — 마이그레이션 SQL은 MySQL 전용 문법
 * (생성 컬럼 `pending_key` 등)이라 H2에서 돌지 않는다. 마이그레이션↔엔티티 정합은
 * {@code showroomz.DB.ShowroomzApplicationTests}가 실 MySQL에서 검증하는 몫으로 남긴다.
 * 그래서 이 테스트들은 PENDING 중복 차단을 <b>DB 유니크 제약이 아니라 서비스 로직으로</b> 검증한다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Tag("integration")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:showroomz-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                        + "CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                // 운영 기본값(open-in-view=true)보다 일부러 엄격하게 둔다 — 지연 로딩이 뷰까지 끌려가는
                // 코드를 여기서 잡는다. 여기서 통과하면 운영 설정에서도 통과한다(역은 성립하지 않는다).
                "spring.jpa.open-in-view=false",
                "spring.flyway.enabled=false",
                // 실 환경변수(.env)에 의존하지 않도록 외부 연동 값은 전부 더미로 고정한다.
                "jwt.secret=integration-test-token-secret-key-must-be-long-enough-for-hs256",
                "app.auth.tokenSecret=integration-test-token-secret-key-must-be-long-enough-for-hs256",
                "aws.s3.bucket=integration-test-bucket",
                "aws.s3.access-key=integration-test-access-key",
                "aws.s3.secret-key=integration-test-secret-key",
                "aws.s3.cloud-front-domain=",
                "sentry.enabled=false",
                "sentry.dsn=",
                "logging.level.org.hibernate.SQL=WARN"
        })
public @interface IntegrationTest {
}
