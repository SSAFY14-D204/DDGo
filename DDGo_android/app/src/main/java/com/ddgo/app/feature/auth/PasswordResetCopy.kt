package com.ddgo.app.feature.auth

internal enum class PasswordResetStage {
    RequestEmail,
    UpdatePassword
}

internal object PasswordResetCopy {

    const val BackButtonDescription = "뒤로가기"

    const val HeroBadgeStart = "PASSWORD RESET"
    const val HeroBadgeContinue = "ALMOST THERE"

    const val StepOneEyebrow = "STEP 1"
    const val StepTwoEyebrow = "STEP 2"

    const val EmailStepTitle = "가입한 이메일을 입력해 주세요"
    const val EmailStepSubtitle = "재설정 링크를 바로 보내드릴게요."
    const val EmailPlaceholder = "name@example.com"

    const val LinkStepTitle = "메일 링크를 붙여 넣어 주세요"
    const val LinkStepSubtitle = "링크 전체를 붙여 넣어도 자동으로 읽어와요."
    const val LinkedStepTitle = "새 비밀번호를 입력해 주세요"
    const val LinkedStepSubtitle = "링크 확인은 이미 끝났어요."

    const val TokenPlaceholder = "재설정 링크 또는 token"
    const val NewPasswordPlaceholder = "새 비밀번호"
    const val ConfirmPasswordPlaceholder = "새 비밀번호 확인"

    const val SendResetMailAction = "재설정 메일 보내기"
    const val ResendResetMailAction = "메일 다시 보내기"
    const val CompleteResetAction = "새 비밀번호로 변경"

    const val AutoDetectModeAction = "자동 인식 상태로 돌아가기"
    const val EditEmailAction = "다른 이메일 입력"
    const val EditLinkAction = "다른 링크 입력"

    const val MailSentTitle = "메일을 보냈어요"
    const val LinkVerifiedTitle = "링크를 확인했어요"
    const val LinkVerifiedDescription = "앱이 재설정 링크를 자동으로 읽어왔어요."
    const val ManualLinkDescription = "메일 링크를 붙여 넣으면 바로 이어서 진행할 수 있어요."

    const val TokenDetectedTitle = "링크 확인 완료"
    const val TokenDetectedDescription = "자동으로 링크를 읽어왔어요."

    const val PasswordGuideTitle = "비밀번호 가이드"
    const val PasswordGuideLength = "8~64자"
    const val PasswordGuideMix = "2종 이상 조합"
    const val PasswordGuidePattern = "이메일, 닉네임, 쉬운 패턴 제외"

    const val ProgressRequestMail = "메일 받기"
    const val ProgressResetPassword = "비밀번호 변경"
    const val ProgressCompleted = "완료"
    const val ProgressActive = "진행 중"
    const val ProgressUpcoming = "다음"

    fun heroBadge(stage: PasswordResetStage): String {
        return if (stage == PasswordResetStage.RequestEmail) {
            HeroBadgeStart
        } else {
            HeroBadgeContinue
        }
    }

    fun heroTitle(stage: PasswordResetStage, hasIncomingToken: Boolean): String {
        return when {
            hasIncomingToken -> "링크 확인이 끝났어요"
            stage == PasswordResetStage.UpdatePassword -> "이제 새 비밀번호만 정하면 돼요"
            else -> "이메일 한 번으로 다시 들어올 수 있어요"
        }
    }

    fun heroDescription(stage: PasswordResetStage, hasIncomingToken: Boolean): String {
        return when {
            hasIncomingToken -> "이제 새 비밀번호만 입력하면 됩니다."
            stage == PasswordResetStage.UpdatePassword -> "메일을 열고 이어서 진행하거나, 링크를 붙여 넣어도 괜찮아요."
            else -> "필요한 단계만 차례대로 보여드릴게요."
        }
    }

    fun statusTitle(requestedEmail: String?): String {
        return if (requestedEmail != null) {
            MailSentTitle
        } else {
            LinkVerifiedTitle
        }
    }

    fun statusBody(requestedEmail: String?, hasIncomingToken: Boolean): String {
        return when {
            requestedEmail != null -> requestedEmail
            hasIncomingToken -> LinkVerifiedDescription
            else -> ManualLinkDescription
        }
    }
}
