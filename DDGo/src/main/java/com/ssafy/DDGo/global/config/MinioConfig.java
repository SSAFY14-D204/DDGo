package com.ssafy.DDGo.global.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MinioConfig {

    private final MinioProperties minioProperties;

    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
                .endpoint(minioProperties.getUrl())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .build();

        try {
            boolean isExist = client
                    .bucketExists(BucketExistsArgs.builder().bucket(minioProperties.getBucket()).build());
            if (!isExist) {
                client.makeBucket(MakeBucketArgs.builder().bucket(minioProperties.getBucket()).build());
                log.info("MinIO 버킷 생성 완료: {}", minioProperties.getBucket());
            }
        } catch (Exception e) {
            log.warn("MinIO 버킷 초기화 실패: {}", e.getMessage());
        }

        return client;
    }
}
