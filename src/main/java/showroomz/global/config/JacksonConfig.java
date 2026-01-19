package showroomz.global.config;

import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

@Configuration
public class JacksonConfig {

    // 원하시는 포맷 (맨 뒤에 'Z' 문자 포함)
    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> {
            // 1. LocalDateTime을 직렬화(JSON 변환)할 때 사용할 포맷 지정
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT);
            builder.serializers(new LocalDateTimeSerializer(formatter));

            // 2. 기본 TimeZone을 UTC로 명시 (Date, ZonedDateTime 등의 타입에 영향)
            builder.timeZone(TimeZone.getTimeZone("UTC"));

            // 3. 날짜를 타임스탬프(숫자)가 아닌 문자열(String)로 출력하도록 설정
            builder.featuresToDisable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }
}
