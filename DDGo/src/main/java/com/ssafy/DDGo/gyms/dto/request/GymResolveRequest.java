package com.ssafy.DDGo.gyms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Schema(description = "클라이밍장 식별 및 생성(Resolve) 요청 DTO")
public class GymResolveRequest {

    @NotBlank(message = "지도 제공자는 필수입니다. (KAKAO, NAVER, GOOGLE)")
    @Schema(description = "지도 제공자", example = "KAKAO")
    private String mapProvider;

    @NotBlank(message = "외부 장소 ID는 필수입니다.")
    @Schema(description = "외부 서비스의 장소 ID", example = "1234567890")
    private String externalPlaceId;

    @NotBlank(message = "장소 이름은 필수입니다.")
    @Schema(description = "장소 이름", example = "더클라임 연남점")
    private String placeName;

    @Schema(description = "구 지번 주소", example = "서울 마포구 연남동 123")
    private String addressName;

    @Schema(description = "도로명 주소", example = "서울 마포구 양화로 186")
    private String roadAddressName;

    @NotNull(message = "위도(Latitude)는 필수입니다.")
    @Schema(description = "위도", example = "37.556811")
    private BigDecimal latitude;

    @NotNull(message = "경도(Longitude)는 필수입니다.")
    @Schema(description = "경도", example = "126.923838")
    private BigDecimal longitude;
}
