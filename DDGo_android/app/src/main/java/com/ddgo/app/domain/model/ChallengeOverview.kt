package com.ddgo.app.domain.model

data class ChallengeOverview(
    val challengeId: Long,
    val gymId: Long?,
    val gymName: String,
    val problemColor: String,
    val gradeLabel: String?,
    val challengeStatus: String,
    val challengeResult: String?,
    val doneAttemptCount: Int,
    val startedAt: String?,
    val endedAt: String?,
    val createdAt: String
)
