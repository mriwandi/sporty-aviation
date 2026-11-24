package com.muhammadriwandi.sportyaviation.controller;

import com.muhammadriwandi.sportyaviation.dto.AirportResponse;
import com.muhammadriwandi.sportyaviation.exception.AirportNotFoundException;
import com.muhammadriwandi.sportyaviation.service.AirportService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/airports")
@Validated
public class AirportController {

    private final AirportService airportService;

    public AirportController(AirportService airportService) {
        this.airportService = airportService;
    }

    @GetMapping("/{icaoCode}")
    public ResponseEntity<AirportResponse> getAirport(
            @PathVariable
            @NotBlank(message = "ICAO code must not be blank")
            @Pattern(regexp = "^[a-zA-Z]{4}$", message = "ICAO code must consist of exactly 4 alphabetic characters")
            String icaoCode) {

        String normalizedCode = icaoCode.toUpperCase();
        AirportResponse response = airportService.getAirportByIcao(normalizedCode)
                .orElseThrow(() -> new AirportNotFoundException(normalizedCode));

        return ResponseEntity.ok(response);
    }
}
