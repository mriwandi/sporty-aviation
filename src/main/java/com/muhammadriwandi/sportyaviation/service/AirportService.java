package com.muhammadriwandi.sportyaviation.service;

import com.muhammadriwandi.sportyaviation.client.AirportDataProvider;
import com.muhammadriwandi.sportyaviation.dto.AirportResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AirportService {

    private static final Logger log = LoggerFactory.getLogger(AirportService.class);

    private final AirportDataProvider airportDataProvider;

    public AirportService(AirportDataProvider airportDataProvider) {
        this.airportDataProvider = airportDataProvider;
    }

    @Cacheable(value = "airports", key = "#icaoCode", unless = "#result == null")
    public Optional<AirportResponse> getAirportByIcao(String icaoCode) {
        String normalizedIcao = icaoCode.trim().toUpperCase();
        log.info("Fetching airport details for ICAO: {}", normalizedIcao);
        return airportDataProvider.fetchAirportByIcao(normalizedIcao);
    }
}
