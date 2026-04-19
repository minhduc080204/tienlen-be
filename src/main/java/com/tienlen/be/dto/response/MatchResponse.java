package com.tienlen.be.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class MatchResponse {
    private Long id;
    private String roomType;
    private Long betToken;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String winners;
}
