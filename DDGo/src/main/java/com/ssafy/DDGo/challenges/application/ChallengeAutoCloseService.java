package com.ssafy.DDGo.challenges.application;

import com.ssafy.DDGo.attempts.dao.AttemptRepository;
import com.ssafy.DDGo.attempts.dao.ChallengeDoneAttemptCountProjection;
import com.ssafy.DDGo.challenges.dao.ChallengeRepository;
import com.ssafy.DDGo.challenges.domain.ChallengeResult;
import com.ssafy.DDGo.global.config.StaleActiveAutoCloseProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChallengeAutoCloseService {

    private final ChallengeRepository challengeRepository;
    private final AttemptRepository attemptRepository;
    private final TransactionTemplate transactionTemplate;
    private final StaleActiveAutoCloseProperties properties;

    public ChallengeAutoCloseService(
            ChallengeRepository challengeRepository,
            AttemptRepository attemptRepository,
            TransactionTemplate transactionTemplate,
            StaleActiveAutoCloseProperties properties) {
        this.challengeRepository = challengeRepository;
        this.attemptRepository = attemptRepository;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
    }

    public AutoCloseRunSummary closeStaleActiveChallenges() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(properties.getStaleAfterHours());
        AutoCloseRunSummary summary = new AutoCloseRunSummary();
        int batchSize = properties.getBatchSize();

        while (true) {
            List<Long> challengeIds = challengeRepository.findStaleActiveChallengeIds(
                    cutoff,
                    PageRequest.of(0, batchSize));
            if (challengeIds.isEmpty()) {
                break;
            }

            summary.addScanned(challengeIds.size());
            Map<Long, Integer> doneAttemptCountByChallengeId = countDoneAttempts(challengeIds);

            for (Long challengeId : challengeIds) {
                closeChallengeSafely(
                        challengeId,
                        resolveResult(doneAttemptCountByChallengeId.getOrDefault(challengeId, 0)),
                        summary);
            }

            if (challengeIds.size() < batchSize) {
                break;
            }
        }

        log.info(
                "Stale active challenge auto-close finished: scanned={}, closedUnknown={}, closedFail={}, skippedAlreadyClosed={}, failed={}, staleAfterHours={}, batchSize={}",
                summary.getScanned(),
                summary.getClosedUnknown(),
                summary.getClosedFail(),
                summary.getSkippedAlreadyClosed(),
                summary.getFailed(),
                properties.getStaleAfterHours(),
                batchSize);
        return summary;
    }

    private Map<Long, Integer> countDoneAttempts(List<Long> challengeIds) {
        return attemptRepository.countDoneAttemptsByChallengeIds(challengeIds)
                .stream()
                .collect(Collectors.toMap(
                        ChallengeDoneAttemptCountProjection::getChallengeId,
                        projection -> Math.toIntExact(projection.getDoneAttemptCount() != null
                                ? projection.getDoneAttemptCount()
                                : 0L)));
    }

    private void closeChallengeSafely(Long challengeId, ChallengeResult result, AutoCloseRunSummary summary) {
        try {
            Boolean closed = transactionTemplate.execute(status ->
                    challengeRepository.closeIfActive(challengeId, result, LocalDateTime.now()) > 0);
            if (Boolean.TRUE.equals(closed)) {
                summary.recordClosed(result);
            } else {
                summary.incrementSkippedAlreadyClosed();
            }
        } catch (RuntimeException exception) {
            summary.incrementFailed();
            log.warn(
                    "Failed to auto-close stale challenge: challengeId={}, result={}",
                    challengeId,
                    result,
                    exception);
        }
    }

    private ChallengeResult resolveResult(int doneAttemptCount) {
        return doneAttemptCount > 0 ? ChallengeResult.FAIL : ChallengeResult.UNKNOWN;
    }

    public static final class AutoCloseRunSummary {
        private int scanned;
        private int closedUnknown;
        private int closedFail;
        private int skippedAlreadyClosed;
        private int failed;

        public int getScanned() {
            return scanned;
        }

        public int getClosedUnknown() {
            return closedUnknown;
        }

        public int getClosedFail() {
            return closedFail;
        }

        public int getSkippedAlreadyClosed() {
            return skippedAlreadyClosed;
        }

        public int getFailed() {
            return failed;
        }

        private void addScanned(int count) {
            scanned += count;
        }

        private void recordClosed(ChallengeResult result) {
            if (result == ChallengeResult.FAIL) {
                closedFail++;
                return;
            }
            closedUnknown++;
        }

        private void incrementSkippedAlreadyClosed() {
            skippedAlreadyClosed++;
        }

        private void incrementFailed() {
            failed++;
        }
    }
}
