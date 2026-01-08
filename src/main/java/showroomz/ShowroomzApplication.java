package showroomz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.web.client.RestTemplate;

import showroomz.global.config.properties.AppProperties;
import showroomz.global.config.properties.CorsProperties;
import showroomz.global.config.properties.S3Properties;

@SpringBootApplication
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
}
