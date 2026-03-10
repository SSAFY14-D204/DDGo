package com.ddgo.app.data.ml.mujoco

/**
 * MuJoCo 성능 테스트용 MJCF XML 모델 모음
 *
 * 복잡도별 3가지 모델 제공:
 *  - PENDULUM  : 단순 이중 진자 (nq=2, nv=2)  ← 베이스라인
 *  - CARTPOLE  : 카트폴 (nq=2, nv=2)          ← RL 기본 환경
 *  - HUMANOID  : 인체 모델 (nq=28, nv=27)     ← 클라이밍 분석 대상
 */
object MuJocoModels {

    // ─────────────────────────────────────────────────────────────────────
    // 1. 이중 진자 (Double Pendulum)
    //    - 가장 단순한 모델, 기준 성능 측정용
    // ─────────────────────────────────────────────────────────────────────
    val PENDULUM = """
        <mujoco model="double_pendulum">
          <option timestep="0.002" integrator="RK4"/>
          <worldbody>
            <light diffuse=".5 .5 .5" pos="0 0 3" dir="0 0 -1"/>
            <geom type="plane" size="1 1 0.1" rgba=".9 0 0 1"/>
            <body name="upper_arm" pos="0 0 1">
              <joint name="shoulder" type="hinge" axis="0 1 0" damping="0.1"/>
              <geom type="capsule" fromto="0 0 0 0 0 -0.4" size="0.04" rgba="0 .9 0 1"/>
              <body name="lower_arm" pos="0 0 -0.4">
                <joint name="elbow" type="hinge" axis="0 1 0" damping="0.1"/>
                <geom type="capsule" fromto="0 0 0 0 0 -0.4" size="0.031" rgba="0 .9 0 1"/>
              </body>
            </body>
          </worldbody>
        </mujoco>
    """.trimIndent()

    // ─────────────────────────────────────────────────────────────────────
    // 2. 카트폴 (CartPole)
    //    - RL 환경 호환성 확인용
    // ─────────────────────────────────────────────────────────────────────
    val CARTPOLE = """
        <mujoco model="cartpole">
          <option timestep="0.01" integrator="RK4"/>
          <worldbody>
            <geom name="floor" type="plane" pos="0 0 -0.05" size="4 4 0.1" rgba=".8 .8 .8 1"/>
            <body name="cart" pos="0 0 0">
              <joint name="slider" type="slide" axis="1 0 0" limited="true" range="-2 2"/>
              <geom type="box" size=".1 .1 .05" rgba=".7 .3 .1 1"/>
              <body name="pole" pos="0 0 .05">
                <joint name="hinge" type="hinge" axis="0 1 0"/>
                <geom type="capsule" fromto="0 0 0 0 0 .6" size=".045" rgba=".1 .7 .3 1"/>
                <geom type="sphere" pos="0 0 .6" size=".06" rgba=".1 .7 .3 1"/>
              </body>
            </body>
          </worldbody>
          <actuator>
            <motor name="slide" joint="slider" gear="100" ctrllimited="true" ctrlrange="-1 1"/>
          </actuator>
        </mujoco>
    """.trimIndent()

    // ─────────────────────────────────────────────────────────────────────
    // 3. 간소화 인체 모델 (Simplified Humanoid)
    //    - 클라이밍 동작 분석을 위한 현실적 복잡도
    //    - 상반신 위주 (어깨, 팔꿈치, 손목, 엉덩이, 무릎, 발목)
    // ─────────────────────────────────────────────────────────────────────
    val HUMANOID = """
        <mujoco model="climbing_humanoid">
          <option timestep="0.002" integrator="RK4" gravity="0 0 -9.81"/>
          <default>
            <joint damping="0.5" limited="true"/>
            <geom rgba=".7 .5 .3 1" contype="1" conaffinity="1" condim="3" friction="1 0.5 0.5"/>
          </default>
          <worldbody>
            <light diffuse=".8 .8 .8" pos="0 0 4" dir="0 0 -1"/>
            <geom name="floor" type="plane" size="5 5 0.1" rgba=".6 .6 .6 1"/>

            <!-- 골반 (루트) -->
            <body name="pelvis" pos="0 0 1.0">
              <freejoint name="root"/>
              <geom type="capsule" fromto="-.07 0 0 .07 0 0" size=".07" rgba=".5 .3 .1 1"/>

              <!-- 상체 -->
              <body name="torso" pos="0 0 .07">
                <joint name="torso_z" type="hinge" axis="0 0 1" range="-45 45"/>
                <joint name="torso_y" type="hinge" axis="0 1 0" range="-60 30"/>
                <geom type="capsule" fromto="0 0 0 0 0 .25" size=".065"/>

                <!-- 머리 -->
                <body name="head" pos="0 0 .26">
                  <geom type="sphere" size=".1"/>
                </body>

                <!-- 왼쪽 어깨 / 팔 -->
                <body name="left_upper_arm" pos="-.18 0 .22">
                  <joint name="left_shoulder_x" type="hinge" axis="1 0 0" range="-85 60"/>
                  <joint name="left_shoulder_y" type="hinge" axis="0 1 0" range="-85 170"/>
                  <geom type="capsule" fromto="0 0 0 -.17 0 0" size=".04"/>
                  <body name="left_lower_arm" pos="-.17 0 0">
                    <joint name="left_elbow" type="hinge" axis="0 0 1" range="-10 150"/>
                    <geom type="capsule" fromto="0 0 0 -.15 0 0" size=".031"/>
                    <body name="left_hand" pos="-.15 0 0">
                      <geom type="sphere" size=".04"/>
                    </body>
                  </body>
                </body>

                <!-- 오른쪽 어깨 / 팔 -->
                <body name="right_upper_arm" pos=".18 0 .22">
                  <joint name="right_shoulder_x" type="hinge" axis="1 0 0" range="-85 60"/>
                  <joint name="right_shoulder_y" type="hinge" axis="0 1 0" range="-170 85"/>
                  <geom type="capsule" fromto="0 0 0 .17 0 0" size=".04"/>
                  <body name="right_lower_arm" pos=".17 0 0">
                    <joint name="right_elbow" type="hinge" axis="0 0 1" range="-150 10"/>
                    <geom type="capsule" fromto="0 0 0 .15 0 0" size=".031"/>
                    <body name="right_hand" pos=".15 0 0">
                      <geom type="sphere" size=".04"/>
                    </body>
                  </body>
                </body>
              </body>

              <!-- 왼쪽 다리 -->
              <body name="left_thigh" pos="-.07 0 -.04">
                <joint name="left_hip_x"  type="hinge" axis="1 0 0" range="-25 25"/>
                <joint name="left_hip_y"  type="hinge" axis="0 1 0" range="-100 30"/>
                <joint name="left_hip_z"  type="hinge" axis="0 0 1" range="-60 35"/>
                <geom type="capsule" fromto="0 0 0 0 .01 -.34" size=".05"/>
                <body name="left_shin" pos="0 .01 -.34">
                  <joint name="left_knee" type="hinge" axis="0 1 0" range="-160 2"/>
                  <geom type="capsule" fromto="0 0 0 0 -.01 -.3" size=".04"/>
                  <body name="left_foot" pos="0 -.01 -.3">
                    <joint name="left_ankle_y" type="hinge" axis="0 1 0" range="-50 50"/>
                    <geom type="capsule" fromto="-.05 0 0 .1 0 0" size=".04"/>
                  </body>
                </body>
              </body>

              <!-- 오른쪽 다리 -->
              <body name="right_thigh" pos=".07 0 -.04">
                <joint name="right_hip_x"  type="hinge" axis="1 0 0" range="-25 25"/>
                <joint name="right_hip_y"  type="hinge" axis="0 1 0" range="-30 100"/>
                <joint name="right_hip_z"  type="hinge" axis="0 0 1" range="-35 60"/>
                <geom type="capsule" fromto="0 0 0 0 .01 -.34" size=".05"/>
                <body name="right_shin" pos="0 .01 -.34">
                  <joint name="right_knee" type="hinge" axis="0 1 0" range="-2 160"/>
                  <geom type="capsule" fromto="0 0 0 0 -.01 -.3" size=".04"/>
                  <body name="right_foot" pos="0 -.01 -.3">
                    <joint name="right_ankle_y" type="hinge" axis="0 1 0" range="-50 50"/>
                    <geom type="capsule" fromto="-.05 0 0 .1 0 0" size=".04"/>
                  </body>
                </body>
              </body>

            </body> <!-- /pelvis -->
          </worldbody>
        </mujoco>
    """.trimIndent()

    /** 모든 테스트 모델 목록 */
    val ALL: List<Pair<String, String>> = listOf(
        "Pendulum (simple)"   to PENDULUM,
        "CartPole (RL env)"   to CARTPOLE,
        "Humanoid (climbing)" to HUMANOID,
    )
}
