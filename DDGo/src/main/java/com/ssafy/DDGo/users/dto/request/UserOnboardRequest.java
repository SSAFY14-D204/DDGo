package com.ssafy.DDGo.users.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserOnboardRequest {

    // 정규식으로 'M' 또는 'F' 만 허용 (원하는 다른 형식이라면 변경 가능)
    @Pattern(regexp = "^[MF]$", message = "성별은 'M' 또는 'F'로 입력해주세요.")
    private String sex;

    @Positive(message = "키는 양수여야 합니다.")
    private Integer heightCm;

    @Positive(message = "몸무게는 양수여야 합니다.")
    private Integer weightKg;

    @Positive(message = "윙스팬은 양수여야 합니다.")
    private Integer wingspanCm;
}
