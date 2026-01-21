package showroomz.global.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "aws.s3")
public class S3Properties {
    private String bucket;
    private String region;
    // private String accessKey; // 삭제: IAM Role/OIDC 사용 시 불필요
    // private String secretKey; // 삭제: IAM Role/OIDC 사용 시 불필요
    private String cloudFrontDomain;
}

