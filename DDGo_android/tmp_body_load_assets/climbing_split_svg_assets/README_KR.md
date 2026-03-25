# 분할 SVG 에셋

원본 이미지에서 추출한 전면/후면 바디맵을 부위별 SVG로 분리했습니다.

## 구성
- `front/full_canvas/*.svg` : 전면용. 모두 같은 캔버스(315x734)라서 그대로 겹쳐 그리기 좋습니다.
- `front/cropped/*.svg` : 전면용 타이트 크롭 버전
- `back/full_canvas/*.svg` : 후면용. 모두 같은 캔버스(316x735)라서 그대로 겹쳐 그리기 좋습니다.
- `back/cropped/*.svg` : 후면용 타이트 크롭 버전
- `front_base.svg`, `back_base.svg` : 연한 회색 바디 베이스
- `front_guides.svg`, `back_guides.svg` : 흰색 경계선/가이드
- `combined/front_regions.svg`, `combined/back_regions.svg` : base/region/guide를 한 파일에 모아둔 버전
- `region_meta.json` : 원본 좌표와 매핑 정보

## 파일명 기준
좌우는 해부학 기준이 아니라 **이미지에 보이는 기준(screen-left / screen-right)** 입니다.

예:
- `front_left_arm.svg` = 전면 그림에서 왼쪽에 보이는 팔
- `back_right_leg.svg` = 후면 그림에서 오른쪽에 보이는 다리

## 권장 사용법
1. `front_base.svg` 또는 `back_base.svg`를 먼저 표시
2. 필요한 부위 SVG만 tint/color 변경해서 overlay
3. `front_guides.svg` 또는 `back_guides.svg`를 맨 위에 올려 경계선 유지

## 참고
- 이 파일들은 제공된 PNG를 기준으로 벡터화한 결과입니다.
- 앱에서 부위별 시각화용으로 쓰기엔 충분하지만, 브랜드용 최종 일러스트로 쓰려면 디자이너가 한 번 더 곡선을 다듬는 것이 좋습니다.
