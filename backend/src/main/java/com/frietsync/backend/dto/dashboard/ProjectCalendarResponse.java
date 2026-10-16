package com.frietsync.backend.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectCalendarResponse {
    private List<Event> events;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Event {
        private String type;
        private UUID id;
        private String title;
        private LocalDate startDate;
        private LocalDate endDate;
    }
}
