package com.ssafy.DDGo.gyms.dto.response;

import com.ssafy.DDGo.gyms.domain.ClimbingGym;
import com.ssafy.DDGo.gyms.domain.ClimbingGymGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
@Schema(description = "클라이밍장 Resolve(매칭/생성) 및 난이도 응답 DTO")
public class GymResolveResponse {

    @Schema(description = "매칭 여부(기존 암장을 찾았으면 true, 새로 만들었으면 false)", example = "true")
    private boolean matched;

    @Schema(description = "클라이밍장 ID", example = "12")
    private Long gymId;

    @Schema(description = "난이도 소스 (CURATED | STANDARD_FALLBACK)", example = "CURATED")
    private String gradeSource;

    @Schema(description = "자동 매칭 상태 여부", example = "VERIFIED")
    private String matchStatus;

    @Schema(description = "관리자 리뷰 필요 여부", example = "false")
    private boolean needsReview;

    @Schema(description = "클라이밍장 정보")
    private GymInfo gym;

    @Schema(description = "난이도 등급 목록")
    private List<GradeInfo> grades;

    @Getter
    @Builder
    public static class GymInfo {
        private Long id;
        private String displayName;
        private String region;
        private String logoBucket;
        private String logoObjectKey;
        private String brandLogoBucket;
        private String brandLogoObjectKey;

        public static GymInfo from(ClimbingGym gym) {
            return GymInfo.builder()
                    .id(gym.getId())
                    .displayName(gym.getDisplayName())
                    .region(gym.getRegion())
                    .logoBucket(gym.getLogoBucket())
                    .logoObjectKey(gym.getLogoObjectKey())
                    .brandLogoBucket(gym.getBrand() != null ? gym.getBrand().getLogoBucket() : null)
                    .brandLogoObjectKey(gym.getBrand() != null ? gym.getBrand().getLogoObjectKey() : null)
                    .build();
        }
    }

    @Getter
    @Builder
    public static class GradeInfo {
        private Long gymGradeId;
        private String colorName;
        private Integer sortOrder;
        private String colorHex;
        private String gradeLabel;

        public static GradeInfo from(ClimbingGymGrade grade) {
            return GradeInfo.builder()
                    .gymGradeId(grade.getId())
                    .colorName(grade.getColorName())
                    .sortOrder(grade.getSortOrder())
                    .colorHex(grade.getColorHex())
                    .gradeLabel(grade.getGradeLabel())
                    .build();
        }
    }

    public static GymResolveResponse of(boolean matched, ClimbingGym gym, List<ClimbingGymGrade> grades) {
        return GymResolveResponse.builder()
                .matched(matched)
                .gymId(gym.getId())
                .gradeSource(gym.getGradeSource().name())
                .matchStatus(gym.getMatchStatus().name())
                .needsReview(gym.getNeedsReview())
                .gym(GymInfo.from(gym))
                .grades(grades.stream().map(GradeInfo::from).collect(Collectors.toList()))
                .build();
    }
}
