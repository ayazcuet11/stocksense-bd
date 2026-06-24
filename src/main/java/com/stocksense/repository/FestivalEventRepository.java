package com.stocksense.repository;

import com.stocksense.domain.FestivalEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface FestivalEventRepository extends JpaRepository<FestivalEvent, Long> {

    @Query("SELECT f FROM FestivalEvent f WHERE f.gregorianDate BETWEEN :from AND :to ORDER BY f.gregorianDate")
    List<FestivalEvent> findUpcoming(LocalDate from, LocalDate to);
}
