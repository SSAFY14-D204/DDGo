package com.ssafy.DDGo.community.application;

import com.ssafy.DDGo.community.dto.request.CommunityPostVideoItemRequest;
import com.ssafy.DDGo.community.dto.request.CommunityVideoUploadUrlRequest;
import com.ssafy.DDGo.community.dto.response.CommunityVideoUploadUrlResponse;
import com.ssafy.DDGo.global.config.MinioProperties;
import com.ssafy.DDGo.global.exception.CustomException;
import com.ssafy.DDGo.global.exception.ErrorCode;
import com.ssafy.DDGo.users.dao.UserRepository;
import com.ssafy.DDGo.users.domain.User;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityMediaService {

    private static final String THUMBNAIL_CONTENT_TYPE = "image/jpeg";
    private static final String THUMBNAIL_SUFFIX = "_thumbnail.jpg";
    private static final int THUMBNAIL_MAX_EDGE = 320;

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
            assertObjectExistsAndMatches(video);
        }
    }

    public String getPlaybackUrl(String objectKey) {
        return getObjectUrl(objectKey);
    }

    public void prepareThumbnail(String videoObjectKey) {
        if (videoObjectKey == null || videoObjectKey.isBlank()) {
            return;
        }
        ensureThumbnailExists(videoObjectKey, buildThumbnailObjectKey(videoObjectKey));
    }

    public String getThumbnailUrl(String videoObjectKey) {
        if (videoObjectKey == null || videoObjectKey.isBlank()) {
            return null;
        }

        String thumbnailObjectKey = buildThumbnailObjectKey(videoObjectKey);
        if (!ensureThumbnailExists(videoObjectKey, thumbnailObjectKey)) {
            return null;
        }
        return getObjectUrl(thumbnailObjectKey);
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

    private String getObjectUrl(String objectKey) {
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
            log.warn("커뮤니티 미디어 GET presigned URL 발급 실패 (objectKey: {}): {}", objectKey, e.getMessage());
            return null;
        }
    }

    private void assertObjectExistsAndMatches(CommunityPostVideoItemRequest video) {
        try {
            io.minio.StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(video.getObjectKey())
                    .build());

            if (video.getFileSize() != null && stat.size() != video.getFileSize()) {
                throw new CustomException(ErrorCode.INVALID_COMMUNITY_MEDIA, "업로드한 영상의 파일 크기 무결성 검증에 실패했습니다.");
            }
            if (video.getContentType() != null && stat.contentType() != null) {
                if (!video.getContentType().equals(stat.contentType()) && !"application/octet-stream".equals(stat.contentType())) {
                    throw new CustomException(ErrorCode.INVALID_COMMUNITY_MEDIA, "업로드한 영상의 형식이 올바르지 않습니다.");
                }
            }
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INVALID_COMMUNITY_MEDIA, "업로드가 완료되지 않았거나 손상된 영상입니다. 정상 업로드 여부를 확인해 주세요.");
        }
    }

    private boolean ensureThumbnailExists(String videoObjectKey, String thumbnailObjectKey) {
        if (objectExists(thumbnailObjectKey)) {
            return true;
        }
        return createThumbnail(videoObjectKey, thumbnailObjectKey);
    }

    private boolean createThumbnail(String videoObjectKey, String thumbnailObjectKey) {
        Path tempVideoPath = null;
        try (InputStream videoStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(minioProperties.getBucket())
                .object(videoObjectKey)
                .build())) {
            tempVideoPath = Files.createTempFile("community-video-thumbnail-", getTempExtension(videoObjectKey));
            Files.copy(videoStream, tempVideoPath, StandardCopyOption.REPLACE_EXISTING);

            byte[] thumbnailBytes = extractThumbnailBytes(tempVideoPath);
            if (thumbnailBytes.length == 0) {
                return false;
            }

            try (ByteArrayInputStream thumbnailStream = new ByteArrayInputStream(thumbnailBytes)) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(minioProperties.getBucket())
                        .object(thumbnailObjectKey)
                        .stream(thumbnailStream, thumbnailBytes.length, -1)
                        .contentType(THUMBNAIL_CONTENT_TYPE)
                        .build());
            }
            return true;
        } catch (Exception e) {
            log.warn("커뮤니티 영상 썸네일 생성 실패 (objectKey: {}): {}", videoObjectKey, e.getMessage());
            return false;
        } finally {
            if (tempVideoPath != null) {
                try {
                    Files.deleteIfExists(tempVideoPath);
                } catch (Exception e) {
                    log.debug("임시 썸네일 작업 파일 삭제 실패 (path: {}): {}", tempVideoPath, e.getMessage());
                }
            }
        }
    }

    private byte[] extractThumbnailBytes(Path videoPath) throws Exception {
        try (SeekableByteChannel channel = NIOUtils.readableChannel(videoPath.toFile());
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            FrameGrab frameGrab = FrameGrab.createFrameGrab(channel);
            Picture picture = frameGrab.getNativeFrame();
            if (picture == null) {
                return new byte[0];
            }

            BufferedImage sourceImage = AWTUtil.toBufferedImage(picture);
            BufferedImage thumbnailImage = resizeThumbnail(sourceImage);
            ImageIO.write(thumbnailImage, "jpg", outputStream);
            return outputStream.toByteArray();
        }
    }

    private BufferedImage resizeThumbnail(BufferedImage sourceImage) {
        int width = sourceImage.getWidth();
        int height = sourceImage.getHeight();
        int maxEdge = Math.max(width, height);
        if (maxEdge <= THUMBNAIL_MAX_EDGE) {
            return sourceImage;
        }

        double scale = (double) THUMBNAIL_MAX_EDGE / maxEdge;
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(sourceImage, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private boolean objectExists(String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey)
                    .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String buildThumbnailObjectKey(String videoObjectKey) {
        int extensionIndex = videoObjectKey.lastIndexOf('.');
        String baseObjectKey = extensionIndex >= 0
                ? videoObjectKey.substring(0, extensionIndex)
                : videoObjectKey;
        return baseObjectKey + THUMBNAIL_SUFFIX;
    }

    private String getTempExtension(String objectKey) {
        int extensionIndex = objectKey.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == objectKey.length() - 1) {
            return ".tmp";
        }
        return objectKey.substring(extensionIndex);
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
            log.warn("커뮤니티 미디어 URL public 변환 실패: {}", e.getMessage());
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
