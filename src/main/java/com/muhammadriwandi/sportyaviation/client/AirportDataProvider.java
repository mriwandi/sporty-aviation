package com.muhammadriwandi.sportyaviation.client;

import com.muhammadriwandi.sportyaviation.dto.AirportResponse;
import java.util.Optional;

public interface AirportDataProvider {
    /**
     * Retrieve airport details by ICAO code.
     *
     * @param icaoCode 4-letter ICAO code (e.g. "KJFK")
     * @return Optional containing airport details if found, empty if not found
     */
    Optional<AirportResponse> fetchAirportByIcao(String icaoCode);
}
