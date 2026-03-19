package com.ddgo.app.data.mapper

import com.ddgo.app.data.remote.pose.PoseDto
import com.ddgo.app.data.remote.pose.PoseSequenceDto
import com.ddgo.app.domain.model.Pose
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Pose domain 모델을 서버 전송용 DTO/JSON으로 변환하는 매퍼입니다.
 */
fun Pose.toPoseDto(): PoseDto = PoseDto(
    frameTimeMs = frameTimeMs,
    landmarksPx = landmarksPx.mapValues { (_, point) ->
        PoseDto.Point2dDto(
            x = point.x,
            y = point.y
        )
    },
    worldLandmarksSample = worldLandmarksSample.mapValues { (_, point) ->
        PoseDto.Point3dDto(
            x = point.x,
            y = point.y,
            z = point.z
        )
    }
)

fun List<Pose>.toPoseSequenceDto(): PoseSequenceDto = PoseSequenceDto(
    poses = map(Pose::toPoseDto)
)

fun PoseSequenceDto.toJson(json: Json): String =
    json.encodeToString(PoseSequenceDto.serializer(), this)

fun List<Pose>.toPoseJson(json: Json): String =
    toPoseSequenceDto().toJson(json)
