package showroomz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import showroomz.config.properties.AppProperties;
import showroomz.config.properties.CorsProperties;

@SpringBootApplication
@EnableConfigurationProperties({AppProperties.class, CorsProperties.class})
public class ShowroomzApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShowroomzApplication.class, args);
	}
	
	@Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
