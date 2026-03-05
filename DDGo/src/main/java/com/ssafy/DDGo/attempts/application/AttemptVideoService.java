package com.ssafy.DDGo.attempts.application;

import com.ssafy.DDGo.attempts.dao.AttemptRepository;
import com.ssafy.DDGo.attempts.dao.AttemptVideoRepository;
import com.ssafy.DDGo.attempts.domain.Attempt;
import com.ssafy.DDGo.attempts.domain.AttemptStatus;
import com.ssafy.DDGo.attempts.domain.AttemptVideo;
import com.ssafy.DDGo.attempts.dto.request.GenerateVideoUrlRequest;
import com.ssafy.DDGo.attempts.dto.response.GenerateVideoUrlResponse;
import com.ssafy.DDGo.global.config.MinioProperties;
import com.ssafy.DDGo.global.exception.CustomException;
import com.ssafy.DDGo.global.exception.ErrorCode;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttemptVideoService {

    private final AttemptRepository attemptRepository;
    private final AttemptVideoRepository attemptVideoRepository;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @Transactional
    public GenerateVideoUrlResponse generatePresignedUrl(Long attemptId, String username,
            GenerateVideoUrlRequest request) {
        // 1. 시도(Attempt) 조회
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new CustomException(ErrorCode.ATTEMPT_NOT_FOUND, "존재하지 않는 시도입니다. ID: " + attemptId));

        // 2. 권한 검증 (해당 시도를 만든 챌린지의 주인이 맞는지)
        if (!attempt.getChallenge().getUser().getUsername().equals(username)) {
            throw new CustomException(ErrorCode.CHALLENGE_ACCESS_DENIED, "해당 시도에 대한 권한이 없습니다.");
        }

        // 3. 상태 검증 (UPLOADING 상태에서만 영상 업로드 가능)
        if (attempt.getAttemptStatus() != AttemptStatus.UPLOADING) {
            throw new CustomException(ErrorCode.INVALID_ATTEMPT_STATUS,
                    "현재 영상 업로드가 가능한 상태가 아닙니다. 상태: " + attempt.getAttemptStatus());
        }

        // 4. Object Key 생성 (attempts/{challengeId}/{attemptId}_{UUID}.{ext})
        String extension = getExtension(request.getOriginalFileName());
        String objectKey = String.format("attempts/%d/%d_%s%s",
                attempt.getChallenge().getId(),
                attempt.getId(),
                UUID.randomUUID().toString(),
                extension);

        // 5. Presigned URL 발급
        String presignedUrl = getPresignedUrl(objectKey);

        // 6. AttemptVideo 엔티티 저장 또는 업데이트
        AttemptVideo attemptVideo = attemptVideoRepository.findByAttemptId(attemptId)
                .orElse(null);

        if (attemptVideo != null) {
            // 이미 발급받은 이력이 있으면 메타데이터만 갱신 (URL 재발급)
            attemptVideo.updateMetadata(request.getOriginalFileName(), objectKey, request.getContentType(),
                    request.getFileSize());
        } else {
            // 신규 생성
            attemptVideo = AttemptVideo.builder()
                    .attempt(attempt)
                    .originalFileName(request.getOriginalFileName())
                    .bucket(minioProperties.getBucket())
                    .objectKey(objectKey)
                    .contentType(request.getContentType())
                    .fileSize(request.getFileSize())
                    .build();
            attemptVideoRepository.save(attemptVideo);
        }

        return new GenerateVideoUrlResponse(presignedUrl, objectKey);
    }

    private String getPresignedUrl(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .expiry((int) Duration.ofMinutes(15).getSeconds()) // 15분 유효
                            .build());
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Presigned URL 발급 중 오류가 발생했습니다. (MinIO 서버 연결 상태 확인 필요)");
        }
    }

    public String getVideoUrlForAttempt(Long attemptId) {
        return attemptVideoRepository.findByAttemptId(attemptId)
                .map(video -> getPresignedGetUrl(video.getObjectKey()))
                .orElse(null);
    }

    private String getPresignedGetUrl(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .expiry((int) Duration.ofHours(2).getSeconds()) // 조회용은 2시간 유효하게 발급
                            .build());
        } catch (Exception e) {
            return null; // 영상 URL 발급 실패 시 null 반환하여 조회 전체가 터지는 것 방지
        }
    }

    private String getExtension(String originalFileName) {
        if (originalFileName == null || !originalFileName.contains(".")) {
            return "";
        }
        return originalFileName.substring(originalFileName.lastIndexOf("."));
    }
}
