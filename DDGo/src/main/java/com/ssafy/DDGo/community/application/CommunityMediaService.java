package com.ssafy.DDGo.community.application;

import com.ssafy.DDGo.community.dto.request.CommunityPostVideoItemRequest;
import com.ssafy.DDGo.community.dto.request.CommunityVideoUploadUrlRequest;
import com.ssafy.DDGo.community.dto.response.CommunityVideoUploadUrlResponse;
import com.ssafy.DDGo.global.config.MinioProperties;
import com.ssafy.DDGo.global.exception.CustomException;
import com.ssafy.DDGo.global.exception.ErrorCode;
import com.ssafy.DDGo.users.dao.UserRepository;
import com.ssafy.DDGo.users.domain.User;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityMediaService {

    private final UserRepository userRepository;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public CommunityVideoUploadUrlResponse generateVideoUploadUrls(String username, CommunityVideoUploadUrlRequest request) {
        User user = getUser(username);
        List<CommunityVideoUploadUrlResponse.VideoUploadTicket> tickets = request.getVideos().stream()
                .map(item -> {
                    String objectKey = generateObjectKey(user.getId(), item.getOriginalFileName());
                    return CommunityVideoUploadUrlResponse.VideoUploadTicket.builder()
                            .originalFileName(item.getOriginalFileName())
                            .objectKey(objectKey)
                            .uploadUrl(generatePutUrl(objectKey))
                            .build();
                })
                .toList();
        return CommunityVideoUploadUrlResponse.builder()
                .videos(tickets)
                .build();
    }

    public void validateOwnedUploadedVideos(Long userId, List<CommunityPostVideoItemRequest> videos) {
        String prefix = buildUserPrefix(userId);
        for (CommunityPostVideoItemRequest video : videos) {
            if (!video.getObjectKey().startsWith(prefix)) {
                throw new CustomException(ErrorCode.INVALID_COMMUNITY_MEDIA, "본인이 발급받은 영상만 첨부할 수 있습니다.");
            }
            assertObjectExists(video.getObjectKey());
        }
    }

    public String getPlaybackUrl(String objectKey) {
        try {
            String internalUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .expiry((int) Duration.ofHours(2).getSeconds())
                            .build());
            return toPublicUrl(internalUrl);
        } catch (Exception e) {
            log.warn("커뮤니티 영상 GET presigned URL 발급 실패: {}", e.getMessage());
            return null;
        }
    }

    public String getBucket() {
        return minioProperties.getBucket();
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private String generateObjectKey(Long userId, String originalFileName) {
        return buildUserPrefix(userId) + UUID.randomUUID() + getExtension(originalFileName);
    }

    private String buildUserPrefix(Long userId) {
        return "community/posts/" + userId + "/";
    }

    private String generatePutUrl(String objectKey) {
        try {
            String internalUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(minioProperties.getBucket())
                            .object(objectKey)
                            .expiry((int) Duration.ofMinutes(15).getSeconds())
                            .build());
            return toPublicUrl(internalUrl);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INVALID_COMMUNITY_MEDIA, "커뮤니티 영상 업로드 URL 발급에 실패했습니다.");
        }
    }

    private void assertObjectExists(String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INVALID_COMMUNITY_MEDIA, "업로드가 완료되지 않은 영상은 저장할 수 없습니다.");
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
            String publicPath = publicUri.getRawPath() == null ? "" : publicUri.getRawPath();
            if (publicPath.endsWith("/")) {
                publicPath = publicPath.substring(0, publicPath.length() - 1);
            }
            StringBuilder rewritten = new StringBuilder()
                    .append(publicUri.getScheme())
                    .append("://")
                    .append(publicUri.getRawAuthority())
                    .append(publicPath)
                    .append(internalUri.getRawPath());
            if (internalUri.getRawQuery() != null && !internalUri.getRawQuery().isBlank()) {
                rewritten.append("?").append(internalUri.getRawQuery());
            }
            return rewritten.toString();
        } catch (Exception e) {
            log.warn("커뮤니티 영상 URL public 변환 실패: {}", e.getMessage());
            return internalUrl;
        }
    }

    private String getExtension(String originalFileName) {
        if (originalFileName == null || !originalFileName.contains(".")) {
            return "";
        }
        return originalFileName.substring(originalFileName.lastIndexOf("."));
    }
}
