package com.ssafy.DDGo.challenges.scheduler;

import com.ssafy.DDGo.challenges.application.ChallengeAutoCloseService;
import com.ssafy.DDGo.global.config.StaleActiveAutoCloseProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChallengeAutoCloseSchedulerTest {

    @Mock
    private ChallengeAutoCloseService challengeAutoCloseService;

    @Mock
    private StaleActiveAutoCloseProperties properties;

    @InjectMocks
    private ChallengeAutoCloseScheduler challengeAutoCloseScheduler;

    @Test
    @DisplayName("자동 종료가 비활성화되면 스케줄러는 정리 작업을 호출하지 않는다")
    void closeStaleActiveChallenges_skipsWhenDisabled() {
        when(properties.isEnabled()).thenReturn(false);

        challengeAutoCloseScheduler.closeStaleActiveChallenges();

        verify(challengeAutoCloseService, never()).closeStaleActiveChallenges();
    }

    @Test
    @DisplayName("자동 종료가 활성화되면 스케줄러는 정리 작업을 호출한다")
    void closeStaleActiveChallenges_runsWhenEnabled() {
        when(properties.isEnabled()).thenReturn(true);

        challengeAutoCloseScheduler.closeStaleActiveChallenges();

        verify(challengeAutoCloseService).closeStaleActiveChallenges();
    }
}
