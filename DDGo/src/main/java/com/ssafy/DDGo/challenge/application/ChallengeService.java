package com.ssafy.DDGo.challenge.application;

import com.ssafy.DDGo.challenge.dao.ChallengeRepository;
import com.ssafy.DDGo.challenge.domain.Challenge;
import com.ssafy.DDGo.challenge.domain.ChallengeStatus;
import com.ssafy.DDGo.challenge.dto.request.ChallengeCreateRequest;
import com.ssafy.DDGo.challenge.dto.response.ChallengeCreateResponse;
import com.ssafy.DDGo.global.exception.CustomException;
import com.ssafy.DDGo.global.exception.ErrorCode;
import com.ssafy.DDGo.users.dao.UserRepository;
import com.ssafy.DDGo.users.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChallengeService {

    private final ChallengeRepository challengeRepository;
    private final UserRepository userRepository;

    @Transactional
    public ChallengeCreateResponse createChallenge(String username, ChallengeCreateRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));

        Challenge challenge = Challenge.builder()
                .user(user)
                .gymName(request.getGymName())
                .problemColor(request.getProblemColor())
                .gradeLabel(request.getGradeLabel())
                .challengeStatus(ChallengeStatus.ACTIVE)
                .startedAt(request.getStartedAt())
                .build();

        // DB INSERT → 트리거(trg_challenges_init_attempt_counter)가 자동으로
        // challenge_attempt_counters row를 생성
        challengeRepository.save(challenge);

        return ChallengeCreateResponse.from(challenge);
    }
}
