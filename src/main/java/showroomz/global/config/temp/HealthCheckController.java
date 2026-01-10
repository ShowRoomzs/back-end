package showroomz.global.config.temp;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

@RestController
public class HealthCheckController {

    @PersistenceContext // 스프링이 자동으로 주입해줍니다.
    private EntityManager entityManager;
    
    @GetMapping("/")
    @Hidden
    public String healthCheck() {
        return "Showroomz server is running! CI/CD Success! 🚀";
    }

    @GetMapping("/test/sentry-error")
    @Hidden
    public String testSentryError() {
        throw new RuntimeException("Sentry 테스트용 500 에러입니다!");
    }

    // @GetMapping("/test/db-error")
    // @Transactional
    // @Hidden
    // public void testDbError() {
    //     // 존재하지 않는 테이블을 조회하여 강제로 DB 에러 발생 (BadSqlGrammarException)
    //     entityManager.createNativeQuery("SELECT * FROM non_existent_table_1234").getResultList();
    // }

    // @GetMapping("/test/sentry-check")
    // @Hidden
    // public String testSentryCheck() {
    //     // Sentry 체크용 커스텀 예외 발생
    //     throw new IllegalStateException("Sentry 체크용 IllegalStateException 발생 - " + System.currentTimeMillis());
    // }
}