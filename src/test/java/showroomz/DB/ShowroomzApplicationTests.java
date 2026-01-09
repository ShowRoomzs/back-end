package showroomz.DB;

import org.junit.jupiter.api.Tag; // import 추가
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("integration") // 핵심: 이 테스트를 'integration' 그룹으로 지정
@SpringBootTest
class ShowroomzApplicationTests {

    @Test
    void contextLoads() {
        // 이 테스트는 Spring Context가 뜰 때
        // Flyway 마이그레이션 + Hibernate Validate가 동시에 수행되므로
        // 설정이 틀렸다면 여기서 실패하게 됩니다.
    }
}