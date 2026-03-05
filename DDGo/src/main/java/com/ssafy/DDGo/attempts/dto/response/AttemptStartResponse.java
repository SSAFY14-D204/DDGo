package com.ssafy.DDGo.attempts.dto.response;

import com.ssafy.DDGo.attempts.domain.Attempt;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "시도(Attempt) 생성 완료 응답 데이터")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AttemptStartResponse {
    
    @Schema(description = "데이터베이스에 저장된 시도 엔티티의 고유 ID (PK)", example = "12")
    private Long attemptId;
    
    @Schema(description = "해당 챌린지 내에서의 시도 순서 번호 (1번부터 시작)", example = "1")
    private Integer attemptNo;

    public static AttemptStartResponse from(Attempt attempt) {
        return AttemptStartResponse.builder()
                .attemptId(attempt.getId())
                .attemptNo(attempt.getAttemptNo())
                .build();
    }
}
