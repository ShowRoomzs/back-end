package showroomz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import io.sentry.SentryOptions;
import showroomz.global.config.properties.AppProperties;
import showroomz.global.config.properties.CorsProperties;
import showroomz.global.config.properties.S3Properties;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableJpaAuditing
@EnableConfigurationProperties({AppProperties.class, CorsProperties.class, S3Properties.class})
public class ShowroomzApplication {
	public static void main(String[] args) {
		SpringApplication.run(ShowroomzApplication.class, args);
	}
	
	@Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

	/**
	 * Sentry BeforeSend 콜백 Bean
	 * Spring Boot Starter가 자동으로 감지하여 적용합니다.
	 * 봇이 발생시키는 예외를 Sentry에 전송하지 않도록 필터링합니다.
	 */
	@Bean
	public SentryOptions.BeforeSendCallback sentryBeforeSendCallback() {
		return (event, hint) -> {
			// event에서 예외 확인
			if (event.getThrowable() != null) {
				Throwable throwable = event.getThrowable();
				
				// HttpMediaTypeNotAcceptableException과 HttpRequestMethodNotSupportedException 필터링
				if (throwable instanceof HttpMediaTypeNotAcceptableException ||
					throwable instanceof HttpRequestMethodNotSupportedException) {
					System.out.println("🤖 봇 예외 필터링: " + throwable.getClass().getSimpleName());
					return null; // null을 반환하면 Sentry에 전송되지 않음
				}
			}
			
			// 예외 타입 이름으로도 확인 (이중 체크)
			if (event.getExceptions() != null && !event.getExceptions().isEmpty()) {
				String type = event.getExceptions().get(0).getType();
				if (type != null && (type.contains("HttpMediaTypeNotAcceptableException") ||
									 type.contains("HttpRequestMethodNotSupportedException"))) {
					System.out.println("🤖 봇 예외 필터링 (타입 기반): " + type);
					return null;
				}
			}
			
			return event; // 다른 예외는 정상적으로 전송
		};
    }
}
