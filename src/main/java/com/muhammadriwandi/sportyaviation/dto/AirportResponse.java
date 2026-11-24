package com.muhammadriwandi.sportyaviation.dto;

import java.io.Serializable;
import java.util.List;

public record AirportResponse(
    String icaoCode,
    String iataCode,
    String faaCode,
    String wmoCode,
    String name,
    Double latitude,
    Double longitude,
    Integer elevationMeters,
    String state,
    String country,
    List<String> siteTypes
) implements Serializable {}
