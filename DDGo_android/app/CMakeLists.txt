cmake_minimum_required(VERSION 3.22)
project("ddgo_mujoco" CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ─────────────────────────────────────────────────────────────────────────────
# FetchContent : 첫 빌드 시 MuJoCo 소스를 자동 다운로드 + 컴파일
#   → prebuilt .so 불필요, NDK만 있으면 동작
# ─────────────────────────────────────────────────────────────────────────────
include(FetchContent)

# GUI / 렌더 / 테스트 관련은 빌드에서 제외 (시뮬레이션 코어만)
set(MUJOCO_BUILD_EXAMPLES   OFF CACHE BOOL "" FORCE)
set(MUJOCO_BUILD_TESTS      OFF CACHE BOOL "" FORCE)
set(MUJOCO_BUILD_SIMULATE   OFF CACHE BOOL "" FORCE)
set(MUJOCO_SAMPLES_USE_GLFW OFF CACHE BOOL "" FORCE)
set(MUJOCO_ENABLE_RPATH     OFF CACHE BOOL "" FORCE)

# ── qhull 실행 파일 비활성화 (Android 크로스 컴파일 대비) ─────────────────────
# MuJoCo는 qhull 정적 라이브러리(libqhull_r)만 필요.
# qhullp/rbox 등 실행 파일은 Android에서 실행 불가 + qh_lib_check 링커 오류 유발.
set(QHULL_ENABLE_TESTING    OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    mujoco
    GIT_REPOSITORY https://github.com/google-deepmind/mujoco.git
    GIT_TAG        3.2.5      # 3.2.5: qsort_r Android 버그 공식 수정 (Discussion #1638)
    GIT_SHALLOW    TRUE
    GIT_PROGRESS   FALSE
)

# ── Android Bionic 호환성 패치 ────────────────────────────────────────────────
# glibc는 _GNU_SOURCE → _POSIX_C_SOURCE 자동 정의하지만, Bionic은 하지 않음.
# MuJoCo engine_util_errmem.c 의 localtime_r 분기가 올바로 선택되도록 강제 정의.
if(ANDROID)
    add_compile_definitions(_POSIX_C_SOURCE=200809L)
endif()

FetchContent_MakeAvailable(mujoco)

# ── qhull 실행 파일 빌드 제외 (크로스 컴파일 링커 오류 방지) ─────────────────
# qhull은 libqhull_r(정적 라이브러리) 외에 다수의 데스크탑용 실행 파일을 빌드.
# Android 크로스 컴파일 환경에서는 이 실행 파일들이 undefined symbol 오류 유발.
# MuJoCo는 libqhull_r 만 사용하므로 실행 파일 전체를 빌드 대상에서 제외.
if(ANDROID)
    # qhull이 생성하는 모든 실행 파일 타겟 (r-reentrant 및 p-non-reentrant 변종 포함)
    foreach(qhull_exe IN ITEMS
            qhull      qhullp
            rbox       rboxp
            qconvex    qconvexp
            qdelaunay  qdelaunayp
            qhalf      qhalfp
            qvoronoi   qvoronoip
            user_eg    user_egp
            user_eg2   user_eg2p
            user_eg3   user_eg3p)
        if(TARGET ${qhull_exe})
            set_target_properties(${qhull_exe} PROPERTIES
                EXCLUDE_FROM_ALL           TRUE
                EXCLUDE_FROM_DEFAULT_BUILD TRUE
            )
        endif()
    endforeach()
endif()

# ─────────────────────────────────────────────────────────────────────────────
# Android 시스템 라이브러리 탐색
# ─────────────────────────────────────────────────────────────────────────────
find_library(log-lib     log)
find_library(android-lib android)
find_library(EGL-lib     EGL)       # MuJoCo render 내부에서 사용
find_library(GLESv3-lib  GLESv3)    # MuJoCo render 내부에서 사용

# ─────────────────────────────────────────────────────────────────────────────
# DDGo MuJoCo JNI 브릿지
# ─────────────────────────────────────────────────────────────────────────────
add_library(ddgo_mujoco SHARED
    src/main/cpp/mujoco_jni.cpp
)

target_include_directories(ddgo_mujoco PRIVATE
    ${mujoco_SOURCE_DIR}/include     # FetchContent가 설정하는 소스 경로
)

target_link_libraries(ddgo_mujoco
    mujoco          # FetchContent로 빌드된 MuJoCo 코어
    ${android-lib}
    ${log-lib}
    ${EGL-lib}
    ${GLESv3-lib}
)
