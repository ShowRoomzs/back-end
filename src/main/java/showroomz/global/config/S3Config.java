package showroomz.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import showroomz.global.config.properties.S3Properties;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@RequiredArgsConstructor
public class S3Config {

    private final S3Properties s3Properties;

    @Bean
    public S3Client s3Client() {
        // [변경] AccessKey, SecretKey를 직접 주입받지 않고,
        // 실행 환경(EC2 IAM Role, 환경변수 등)에서 자동으로 자격 증명을 가져오는 DefaultCredentialsProvider 사용
        return S3Client.builder()
                .region(Region.of(s3Properties.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /** §4 — 첨부 Presigned URL 발급 전용(§4-1). S3Client와 별개 빈으로, 실제 업로드는 FE→S3 직접 PUT이라
     *  서버는 서명만 담당한다. */
    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(s3Properties.getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}

