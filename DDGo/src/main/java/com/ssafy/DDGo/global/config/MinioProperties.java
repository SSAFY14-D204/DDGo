package com.ssafy.DDGo.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {
    private String url;          // 서버 내부 통신용 URL (예: http://minio:9000)
    private String publicUrl;    // 클라이언트 노출용 외부 퍼블릭 URL (예: https://api.ddgo.com/minio)
    private String accessKey;
    private String secretKey;
    private String bucket;
}
