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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

@Slf4j
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
            String internalUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .expiry((int) Duration.ofMinutes(15).getSeconds()) // 15분 유효
                            .build());
            return toPublicUrl(internalUrl);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Presigned URL 발급 중 오류가 발생했습니다. (MinIO 서버 연결 상태 확인 필요)");
        }
    }

    public String getVideoUrlForAttempt(Long attemptId) {
        return attemptVideoRepository.findByAttemptId(attemptId)
                .filter(AttemptVideo::isUploaded) // 업로드 완료된 영상만 Presigned URL 발급
                .map(video -> getPresignedGetUrl(video.getObjectKey()))
                .orElse(null);
    }

    private String getPresignedGetUrl(String objectKey) {
        try {
            String internalUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .expiry((int) Duration.ofHours(2).getSeconds()) // 조회용은 2시간 유효하게 발급
                            .build());
            return toPublicUrl(internalUrl);
        } catch (Exception e) {
            log.warn("영상 조회용 Presigned URL 발급 실패 (objectKey: {}): {}", objectKey, e.getMessage());
            return null; // 영상 URL 발급 실패 시 null 반환하여 조회 전체가 터지는 것 방지
        }
    }

    private String toPublicUrl(String internalUrl) {
        String publicBase = minioProperties.getPublicUrl();
        if (publicBase == null || publicBase.isBlank()) {
            return internalUrl;
        }

        try {
            URI internalUri = URI.create(internalUrl);
            URI publicUri = URI.create(publicBase);

            String publicPath = publicUri.getRawPath();
            if (publicPath == null) {
                publicPath = "";
            }
            if (publicPath.endsWith("/")) {
                publicPath = publicPath.substring(0, publicPath.length() - 1);
            }

            String rawPath = internalUri.getRawPath();
            String rawQuery = internalUri.getRawQuery();

            StringBuilder rewritten = new StringBuilder();
            rewritten.append(publicUri.getScheme())
                    .append("://")
                    .append(publicUri.getRawAuthority())
                    .append(publicPath)
                    .append(rawPath);

            if (rawQuery != null && !rawQuery.isBlank()) {
                rewritten.append("?").append(rawQuery);
            }

            return rewritten.toString();
        } catch (Exception e) {
            log.warn("URL 파싱 오류, 원본 URL 반환: {}", internalUrl);
            return internalUrl;
        }
    }

    private String getExtension(String originalFileName) {
        if (originalFileName == null || !originalFileName.contains(".")) {
            return "";
        }
        return originalFileName.substring(originalFileName.lastIndexOf("."));
    }

    @Transactional
    public com.ssafy.DDGo.attempts.dto.response.VideoUploadCompleteResponse completeVideoUpload(
            Long attemptId, String username, com.ssafy.DDGo.attempts.dto.request.VideoUploadCompleteRequest request) {
        
        // 1. 시도(Attempt) 조회
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new CustomException(ErrorCode.ATTEMPT_NOT_FOUND, "존재하지 않는 시도입니다. ID: " + attemptId));

        // 2. 권한 검증
        if (!attempt.getChallenge().getUser().getUsername().equals(username)) {
            throw new CustomException(ErrorCode.CHALLENGE_ACCESS_DENIED, "해당 시도에 대한 권한이 없습니다.");
        }

        // 3. AttemptVideo 조회
        AttemptVideo attemptVideo = attemptVideoRepository.findByAttemptId(attemptId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT_VALUE, "업로드 대기 중인 영상 정보를 찾을 수 없습니다. 먼저 Presigned URL을 발급받아 주세요."));

        // 4. 실제로 MinIO 스토리지에 영상이 올라와 있는지 검증 (statObject)
        String etag;
        try {
            io.minio.StatObjectResponse stat = minioClient.statObject(
                    io.minio.StatObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(attemptVideo.getObjectKey())
                            .build()
            );
            // 클라이언트가 명시적 ETag를 주지 않았다면 서버에서 가져온 실제 ETag로 대체
            if (request != null && request.getEtag() != null && !request.getEtag().isBlank()) {
                etag = request.getEtag();
            } else {
                etag = stat.etag();
            }

            // 파일 무결성(크기 및 타입) 검증 추가
            if (attemptVideo.getFileSize() != null && stat.size() != attemptVideo.getFileSize()) {
                log.warn("파일 크기 비정상 (attemptId: {}, DB: {}, MinIO: {})", attemptId, attemptVideo.getFileSize(), stat.size());
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "업로드된 파일의 크기 식별이 실패했습니다. 비정상적인 업로드입니다.");
            }
            if (attemptVideo.getContentType() != null && stat.contentType() != null) {
                // MinIO 자체 Fallback으로 application/octet-stream이 찍힐 수 있음은 허용. 단, 클라이언트가 명확히 다른 타입을 보냈다면 차단.
                if (!attemptVideo.getContentType().equals(stat.contentType()) && !"application/octet-stream".equals(stat.contentType())) {
                    log.warn("ContentType 비정상 (attemptId: {}, DB: {}, MinIO: {})", attemptId, attemptVideo.getContentType(), stat.contentType());
                    throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "업로드된 파일의 포맷 정보가 올바르지 않습니다.");
                }
            }
        } catch (CustomException e) {
            throw e; // 검증에 의한 CustomException은 그대로 Throw 되도록 패스
        } catch (Exception e) {
            log.error("MinIO statObject 검증 실패 (objectKey: {}): {}", attemptVideo.getObjectKey(), e.getMessage());
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "해당 영상이 MinIO 스토리지에 존재하지 않습니다. 정상적으로 업로드되었는지 확인해주세요.");
        }

        // 5. 업로드 완료 처리
        attemptVideo.markAsUploaded(etag);

        // 6. 진행 상태 변경 (UPLOADING 이거나 에러 상태에서만 PROCESSING으로 전환)
        if (attempt.getAttemptStatus() == AttemptStatus.UPLOADING || attempt.getAttemptStatus() == AttemptStatus.UPLOAD_FAILED) {
            attempt.updateStatus(AttemptStatus.PROCESSING);
            attempt.markAnalysisStarted(); // 분석 시작 타이머 기록
        }

        return com.ssafy.DDGo.attempts.dto.response.VideoUploadCompleteResponse.from(attempt, attemptVideo.isUploaded(), attemptVideo.getUploadedAt());
    }
}
