package showroomz;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import io.sentry.Sentry;
import jakarta.annotation.PostConstruct;
import showroomz.global.config.properties.AppProperties;
import showroomz.global.config.properties.CorsProperties;
import showroomz.global.config.properties.S3Properties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({AppProperties.class, CorsProperties.class, S3Properties.class})
public class ShowroomzApplication {
	@Value("${sentry.dsn:}") // 설정 파일에서 dsn을 가져와봄
    private String dsn;
	public static void main(String[] args) {
		SpringApplication.run(ShowroomzApplication.class, args);
	}
	
	@Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

	@PostConstruct
    public void checkSentry() {
		System.out.println("========== SENTRY MANUAL INIT START ==========");
        
        // Sentry를 수동으로 켭니다.
        Sentry.init(options -> {
            options.setDsn(dsn);
            options.setTracesSampleRate(1.0);
            options.setEnvironment("dev"); // 환경 설정
        });
        
        System.out.println("========== SENTRY MANUAL INIT END ==========");
    }
}
