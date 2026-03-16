package com.ssafy.DDGo.gyms.api;

import com.ssafy.DDGo.global.common.ApiResponse;
import com.ssafy.DDGo.global.exception.CustomException;
import com.ssafy.DDGo.global.exception.ErrorCode;
import com.ssafy.DDGo.gyms.application.GymService;
import com.ssafy.DDGo.gyms.dao.ClimbingGymRepository;
import com.ssafy.DDGo.gyms.domain.ClimbingGym;
import com.ssafy.DDGo.gyms.domain.ClimbingGymGrade;
import com.ssafy.DDGo.gyms.dto.request.GymResolveRequest;
import com.ssafy.DDGo.gyms.dto.response.GymGradesResponse;
import com.ssafy.DDGo.gyms.dto.response.GymResolveResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/gyms")
@RequiredArgsConstructor
@Tag(name = "Climbing Gym API", description = "클라이밍 암장 및 난이도 관련 API")
public class GymController {

    private final GymService gymService;
    private final ClimbingGymRepository gymRepository;

    @PostMapping("/resolve")
    @Operation(summary = "암장 매칭 및 생성 (Resolve)", description = "지도에서 선택한 장소를 서버 DB의 암장과 매칭하거나, 없을 경우 새로 생성합니다.")
    public ResponseEntity<ApiResponse<GymResolveResponse>> resolveGym(
            @Valid @RequestBody GymResolveRequest request) {

        GymResolveResponse response = gymService.resolveGym(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{gymId}/grades")
    @Operation(summary = "특정 암장의 난이도 목록 조회", description = "암장 ID를 받아 해당 암장의 기본 정보와 활성화된 난이도 목록을 정렬하여 반환합니다.")
    public ResponseEntity<ApiResponse<GymGradesResponse>> getGymGrades(
            @Parameter(description = "암장 ID") @PathVariable("gymId") Long gymId) {

        ClimbingGym gym = gymRepository.findById(gymId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT_VALUE));

        if (Boolean.FALSE.equals(gym.getIsActive())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        List<ClimbingGymGrade> grades = gymService.getGymGradesOptimized(gymId);
        return ResponseEntity.ok(ApiResponse.success(GymGradesResponse.of(gym, grades)));
    }
}
