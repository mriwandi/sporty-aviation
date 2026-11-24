package com.muhammadriwandi.sportyaviation.exception;

public class AirportNotFoundException extends RuntimeException {
    public AirportNotFoundException(String icaoCode) {
        super("Airport with ICAO code '" + icaoCode + "' not found");
    }
}
