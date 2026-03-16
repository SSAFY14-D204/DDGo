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
@Schema(description = "클라이밍장 정보 및 난이도 등급 목록 응답 DTO")
public class GymGradesResponse {

    @Schema(description = "클라이밍장 정보")
    private GymResolveResponse.GymInfo gym;

    @Schema(description = "난이도 등급 목록")
    private List<GymResolveResponse.GradeInfo> grades;

    public static GymGradesResponse of(ClimbingGym gym, List<ClimbingGymGrade> grades) {
        return GymGradesResponse.builder()
                .gym(GymResolveResponse.GymInfo.from(gym))
                .grades(grades.stream().map(GymResolveResponse.GradeInfo::from).collect(Collectors.toList()))
                .build();
    }
}
