package com.ddgo.app.domain.model

class ChallengeAlreadyClosedException(
    message: String
) : IllegalStateException(message)
