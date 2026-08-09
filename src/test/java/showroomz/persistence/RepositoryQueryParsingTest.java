package showroomz.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 리포지토리의 모든 @Query(JPQL)가 <b>실제로 파싱되는지</b> 검증한다.
 *
 * <p>왜 필요한가 — JPQL은 문자열이라 컴파일러가 검증하지 않고, 오타는 애플리케이션 기동 시점에야
 * 터진다. 이 프로젝트는 `ddl-auto: validate` + 실 MySQL 구성이라 로컬에서 부팅 검증을 돌리기 어렵다.
 *
 * <p>DB 연결 없이 도는 이유 — Hibernate는 HQL을 <b>메타모델만으로</b> 파싱한다. dialect를 명시하고
 * JDBC 메타데이터 조회를 끄면 커넥션을 열지 않으므로, DataSource 없이 EntityManagerFactory를 띄워
 * `createQuery()`로 파싱만 시킬 수 있다.
 *
 * <p>쿼리 문자열을 테스트에 복사하지 않고 <b>리플렉션으로 @Query를 읽어</b> 검사하므로, 새 쿼리가
 * 추가돼도 이 테스트가 자동으로 덮는다(테스트가 낡지 않는다).
 */
class RepositoryQueryParsingTest {

    private static LocalContainerEntityManagerFactoryBean factoryBean;
    private static EntityManagerFactory entityManagerFactory;

    @BeforeAll
    static void bootMetamodel() {
        Properties properties = new Properties();
        properties.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        // 커넥션 없이 부팅하기 위한 두 설정 — 이게 없으면 Hibernate가 JDBC 메타데이터를 조회하려 한다.
        properties.put("hibernate.boot.allow_jdbc_metadata_access", "false");
        properties.put("jakarta.persistence.schema-generation.database.action", "none");

        factoryBean = new LocalContainerEntityManagerFactoryBean();
        factoryBean.setPersistenceUnitName("query-parsing-check");
        factoryBean.setPackagesToScan("showroomz");
        factoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factoryBean.setJpaProperties(properties);
        factoryBean.afterPropertiesSet();

        entityManagerFactory = factoryBean.getObject();
    }

    @AfterAll
    static void shutdown() {
        if (factoryBean != null) {
            factoryBean.destroy();
        }
    }

    @TestFactory
    Stream<DynamicTest> everyJpqlQueryParses() {
        return findAnnotatedQueries().stream()
                .map(q -> DynamicTest.dynamicTest(q.label(), () -> {
                    try (EntityManager em = entityManagerFactory.createEntityManager()) {
                        assertThatCode(() -> em.createQuery(q.jpql())).doesNotThrowAnyException();
                    }
                }));
    }

    private record AnnotatedQuery(String label, String jpql) {
    }

    private List<AnnotatedQuery> findAnnotatedQueries() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                        return beanDefinition.getMetadata().isInterface();
                    }
                };
        scanner.addIncludeFilter(new AssignableTypeFilter(Repository.class));

        List<AnnotatedQuery> queries = new ArrayList<>();
        scanner.findCandidateComponents("showroomz").forEach(definition -> {
            Class<?> repositoryType;
            try {
                repositoryType = Class.forName(definition.getBeanClassName());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
            for (Method method : repositoryType.getDeclaredMethods()) {
                Query query = method.getAnnotation(Query.class);
                if (query == null || !isPlainJpql(query)) {
                    continue;
                }
                queries.add(new AnnotatedQuery(
                        repositoryType.getSimpleName() + "." + method.getName(), query.value()));
            }
        });
        return queries;
    }

    /**
     * 순수 JPQL만 남긴다 — 아래 세 부류는 Hibernate가 직접 파싱하지 않으므로 제외해야 오탐이 안 난다.
     * <ul>
     *   <li>네이티브 쿼리 — 애초에 JPQL이 아니다</li>
     *   <li>SpEL(`#{...}`) — 런타임에 치환된 뒤에야 유효한 JPQL이 된다</li>
     *   <li>`LIKE %:param%` — Spring Data 고유 문법으로, 표준 JPQL에는 없다.
     *       Spring Data가 바인딩 시 와일드카드를 값 쪽으로 옮겨준 뒤 Hibernate에 넘긴다</li>
     * </ul>
     */
    private boolean isPlainJpql(Query query) {
        String jpql = query.value();
        return !query.nativeQuery() && !jpql.contains("#{") && !jpql.contains("%:");
    }
}
