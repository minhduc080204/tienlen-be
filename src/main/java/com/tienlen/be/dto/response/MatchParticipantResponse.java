package com.tienlen.be.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MatchParticipantResponse {
    private Long id;
    private Long userId;
    private String name;
    private Integer rank;
    private Long tokenChange;
    private MatchResponse match;
}
