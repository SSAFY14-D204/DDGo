package com.ssafy.DDGo.gyms.dao;

import com.ssafy.DDGo.gyms.domain.ClimbingGym;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface ClimbingGymRepository extends JpaRepository<ClimbingGym, Long> {

    Optional<ClimbingGym> findByMapProviderAndExternalPlaceId(ClimbingGym.MapProvider mapProvider, String externalPlaceId);

    @Query("SELECT g FROM ClimbingGym g WHERE g.displayName = :name OR g.name = :name")
    List<ClimbingGym> findByDisplayNameOrName(@Param("name") String name);
}
