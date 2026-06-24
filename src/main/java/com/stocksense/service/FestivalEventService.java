package com.stocksense.service;

import com.stocksense.domain.FestivalEvent;
import com.stocksense.repository.FestivalEventRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class FestivalEventService {

    private final FestivalEventRepository festivalEventRepository;

    public FestivalEventService(FestivalEventRepository festivalEventRepository) {
        this.festivalEventRepository = festivalEventRepository;
    }

    public List<FestivalEvent> listAll() {
        return festivalEventRepository.findAll();
    }

    public List<FestivalEvent> listUpcoming(int withinDays) {
        LocalDate today = LocalDate.now();
        return festivalEventRepository.findUpcoming(today, today.plusDays(withinDays));
    }
}
