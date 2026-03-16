package com.ssafy.DDGo.gyms.dao;

import com.ssafy.DDGo.gyms.domain.ClimbingGymGrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClimbingGymGradeRepository extends JpaRepository<ClimbingGymGrade, Long> {
    
    List<ClimbingGymGrade> findByGymIdAndIsEnabledOrderBySortOrderAsc(Long gymId, Boolean isEnabled);
}
