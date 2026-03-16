package com.ssafy.DDGo.gyms.application;

import com.ssafy.DDGo.gyms.dao.ClimbingGymGradeRepository;
import com.ssafy.DDGo.gyms.dao.ClimbingGymRepository;
import com.ssafy.DDGo.gyms.domain.ClimbingGym;
import com.ssafy.DDGo.gyms.domain.ClimbingGymGrade;
import com.ssafy.DDGo.gyms.dto.request.GymResolveRequest;
import com.ssafy.DDGo.gyms.dto.response.GymResolveResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GymService {

    private final ClimbingGymRepository gymRepository;
    private final ClimbingGymGradeRepository gymGradeRepository;

    @Transactional
    public GymResolveResponse resolveGym(GymResolveRequest request) {
        
        // 1. Try exact match by provider and external ID
        Optional<ClimbingGym> exactMatch = findByExternalId(request);
        if (exactMatch.isPresent()) {
            ClimbingGym gym = exactMatch.get();
            updateGymMapInfoIfEmpty(gym, request);
            List<ClimbingGymGrade> grades = getGymGradesOptimized(gym.getId());
            return GymResolveResponse.of(true, gym, grades);
        }

        // 2. Try matching by raw place name normalization
        String normalizedName = normalizePlaceName(request.getPlaceName());
        List<ClimbingGym> nameMatches = gymRepository.findByDisplayNameOrName(normalizedName);
        
        if (!nameMatches.isEmpty()) {
            ClimbingGym gym = nameMatches.get(0); // For MVP, grab the first one
            updateGymMapInfoIfEmpty(gym, request);
            List<ClimbingGymGrade> grades = getGymGradesOptimized(gym.getId());
            return GymResolveResponse.of(true, gym, grades);
        }

        // 3. Fallback: Create new Gym and Grades
        ClimbingGym newGym = createFallbackGym(request);
        List<ClimbingGymGrade> fallbackGrades = createFallbackGrades(newGym);

        return GymResolveResponse.of(false, newGym, fallbackGrades);
    }

    private Optional<ClimbingGym> findByExternalId(GymResolveRequest request) {
        try {
            ClimbingGym.MapProvider provider = ClimbingGym.MapProvider.valueOf(request.getMapProvider().toUpperCase());
            return gymRepository.findByMapProviderAndExternalPlaceId(provider, request.getExternalPlaceId());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid MapProvider: {}", request.getMapProvider());
            return Optional.empty();
        }
    }

    private void updateGymMapInfoIfEmpty(ClimbingGym gym, GymResolveRequest request) {
        try {
            ClimbingGym.MapProvider provider = ClimbingGym.MapProvider.valueOf(request.getMapProvider().toUpperCase());
            gym.updateMapInfo(
                    provider,
                    request.getExternalPlaceId(),
                    request.getAddressName(),
                    request.getRoadAddressName(),
                    request.getLatitude(),
                    request.getLongitude()
            );
        } catch (IllegalArgumentException ignored) {}
    }

    private String normalizePlaceName(String placeName) {
        if (placeName == null) return "";
        return placeName.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("·", "")
                .replaceAll("[^a-zA-Z0-9가-힣\\s]", "");
    }

    private ClimbingGym createFallbackGym(GymResolveRequest request) {
        ClimbingGym.MapProvider provider;
        try {
            provider = ClimbingGym.MapProvider.valueOf(request.getMapProvider().toUpperCase());
        } catch (IllegalArgumentException e) {
            provider = ClimbingGym.MapProvider.MANUAL;
        }

        String safeName = request.getPlaceName() != null && !request.getPlaceName().isBlank() 
                ? request.getPlaceName() : "Unknown Gym";
        
        // Ensure displayName is unique by appending externalPlaceId if available
        String safeDisplayName = safeName;
        if (request.getExternalPlaceId() != null && !request.getExternalPlaceId().isBlank()) {
            safeDisplayName += " (" + request.getExternalPlaceId() + ")";
        }

        ClimbingGym gym = ClimbingGym.builder()
                .mapProvider(provider)
                .externalPlaceId(request.getExternalPlaceId())
                .placeNameRaw(request.getPlaceName())
                .name(safeName)
                .displayName(safeDisplayName)
                .region(extractRegion(request.getAddressName()))
                .addressName(request.getAddressName())
                .roadAddressName(request.getRoadAddressName())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .gymSource(ClimbingGym.GymSource.MAP_IMPORTED)
                .gradeSource(ClimbingGym.GradeSource.STANDARD_FALLBACK)
                .matchStatus(ClimbingGym.MatchStatus.UNMATCHED_IMPORTED)
                .needsReview(true)
                .isActive(true)
                .build();

        return gymRepository.save(gym);
    }

    private List<ClimbingGymGrade> createFallbackGrades(ClimbingGym gym) {
        List<ClimbingGymGrade> fallbackGrades = Arrays.asList(
                createGrade(gym, "빨강", 1, "#FF3B30"),
                createGrade(gym, "주황", 2, "#FF9500"),
                createGrade(gym, "노랑", 3, "#FFD60A"),
                createGrade(gym, "초록", 4, "#34C759"),
                createGrade(gym, "파랑", 5, "#007AFF"),
                createGrade(gym, "남색", 6, "#5856D6"),
                createGrade(gym, "보라", 7, "#AF52DE"),
                createGrade(gym, "갈색", 8, "#A2845E"),
                createGrade(gym, "핑크", 9, "#FF2D55"),
                createGrade(gym, "흰색", 10, "#FFFFFF"),
                createGrade(gym, "회색", 11, "#8E8E93"),
                createGrade(gym, "검정", 12, "#1C1C1E")
        );

        return gymGradeRepository.saveAll(fallbackGrades);
    }

    private ClimbingGymGrade createGrade(ClimbingGym gym, String colorName, int sortOrder, String colorHex) {
        return ClimbingGymGrade.builder()
                .gym(gym)
                .colorName(colorName)
                .sortOrder(sortOrder)
                .colorHex(colorHex)
                .isEnabled(true)
                .build();
    }

    private String extractRegion(String address) {
        if (address == null || address.isEmpty()) return null;
        String[] parts = address.split(" ");
        if (parts.length >= 2) {
            return parts[0] + " " + parts[1]; // e.g. "서울 마포구"
        }
        return parts[0];
    }
    
    @Transactional(readOnly = true)
    public List<ClimbingGymGrade> getGymGradesOptimized(Long gymId) {
        return gymGradeRepository.findByGymIdAndIsEnabledOrderBySortOrderAsc(gymId, true);
    }
}
