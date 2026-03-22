package com.ssafy.DDGo.attempts.dto.response;

import com.ssafy.DDGo.attempts.domain.Attempt;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "영상 업로드 완료 응답")
public class VideoUploadCompleteResponse {

    @Schema(description = "시도 ID", example = "1")
    private Long attemptId;

    @Schema(description = "영상 업로드 여부", example = "true")
    private boolean isUploaded;

    @Schema(description = "현재 시도 상태 (PROCESSING 등으로 전환됨)", example = "PROCESSING")
    private String attemptStatus;

    @Schema(description = "업로드 완료 처리 시각")
    private LocalDateTime uploadedAt;

    public static VideoUploadCompleteResponse from(Attempt attempt, boolean isUploaded, LocalDateTime uploadedAt) {
        return VideoUploadCompleteResponse.builder()
                .attemptId(attempt.getId())
                .isUploaded(isUploaded)
                .attemptStatus(attempt.getAttemptStatus().name())
                .uploadedAt(uploadedAt)
                .build();
    }
}
