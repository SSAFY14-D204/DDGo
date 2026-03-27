package com.ssafy.DDGo.challenges.scheduler;

import com.ssafy.DDGo.challenges.application.ChallengeAutoCloseService;
import com.ssafy.DDGo.global.config.StaleActiveAutoCloseProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChallengeAutoCloseScheduler {

    private final ChallengeAutoCloseService challengeAutoCloseService;
    private final StaleActiveAutoCloseProperties properties;

    @Scheduled(fixedDelayString = "${challenge.stale-active-auto-close.interval-ms:3600000}")
    public void closeStaleActiveChallenges() {
        if (!properties.isEnabled()) {
            return;
        }
        challengeAutoCloseService.closeStaleActiveChallenges();
    }
}
