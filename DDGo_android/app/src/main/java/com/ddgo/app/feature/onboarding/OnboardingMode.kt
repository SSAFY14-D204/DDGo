package com.ddgo.app.feature.onboarding

enum class OnboardingMode {
    INTRO,
    PROFILE,
    INTRO_AND_PROFILE;

    val includesIntro: Boolean
        get() = this == INTRO || this == INTRO_AND_PROFILE

    val includesProfileSetup: Boolean
        get() = this == PROFILE || this == INTRO_AND_PROFILE

    companion object {
        fun fromRouteArg(value: String?): OnboardingMode {
            return entries.firstOrNull { it.name == value } ?: INTRO
        }
    }
}
