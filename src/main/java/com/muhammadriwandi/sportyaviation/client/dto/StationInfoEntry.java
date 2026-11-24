package com.muhammadriwandi.sportyaviation.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StationInfoEntry(
    String id,
    String icaoId,
    String iataId,
    String faaId,
    String wmoId,
    String site,
    Double lat,
    Double lon,
    Integer elev,
    String state,
    String country,
    Integer priority,
    List<String> siteType
) {}
