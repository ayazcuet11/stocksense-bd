package com.stocksense.controller;

import com.stocksense.domain.FestivalEvent;
import com.stocksense.service.FestivalEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/festivals")
public class FestivalEventController {

    private final FestivalEventService festivalEventService;

    @GetMapping
    public List<FestivalEvent> listAll() {
        return festivalEventService
                .listAll();
    }

    @GetMapping("/upcoming")
    public List<FestivalEvent> upcoming(@RequestParam(defaultValue = "90") int withinDays) {
        return festivalEventService
                .listUpcoming(withinDays);
    }
}
