package com.muhammadriwandi.sportyaviation;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.muhammadriwandi.sportyaviation.dto.AirportResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Objects;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AirportIntegrationTest {

    static WireMockServer wireMockServer;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CacheManager cacheManager;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void setup() {
        wireMockServer.resetAll();
        // Clear cache between test runs to ensure isolation
        if (cacheManager.getCache("airports") != null) {
            Objects.requireNonNull(cacheManager.getCache("airports")).clear();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("aviation.weather.base-url", () -> wireMockServer.baseUrl());
    }

    @Test
    @DisplayName("Should return 200 with airport details when valid ICAO is requested")
    void shouldReturnAirportDetails_WhenValidIcaoCodeProvided() {
        String mockResponseJson = """
                [
                  {
                    "id": "KJFK",
                    "icaoId": "KJFK",
                    "iataId": "JFK",
                    "faaId": "JFK",
                    "wmoId": "74486",
                    "site": "New York/JF Kennedy Intl",
                    "lat": 40.63916,
                    "lon": -73.76394,
                    "elev": 3,
                    "state": "NY",
                    "country": "US",
                    "priority": 0,
                    "siteType": ["METAR", "TAF"]
                  }
                ]
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/stationinfo"))
                .withQueryParam("ids", equalTo("KJFK"))
                .withQueryParam("format", equalTo("json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(mockResponseJson)));

        ResponseEntity<AirportResponse> response = restTemplate.getForEntity(
                "/api/v1/airports/KJFK", AirportResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AirportResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.icaoCode()).isEqualTo("KJFK");
        assertThat(body.iataCode()).isEqualTo("JFK");
        assertThat(body.name()).isEqualTo("New York/JF Kennedy Intl");
        assertThat(body.latitude()).isEqualTo(40.63916);
        assertThat(body.longitude()).isEqualTo(-73.76394);
        assertThat(body.elevationMeters()).isEqualTo(3);
        assertThat(body.state()).isEqualTo("NY");
        assertThat(body.country()).isEqualTo("US");
        assertThat(body.siteTypes()).containsExactly("METAR", "TAF");
    }

    @Test
    @DisplayName("Should serve consecutive requests from cache and not call upstream again")
    void shouldReturnCachedResult_WhenCalledSuccessively() {
        String mockResponseJson = """
                [
                  {
                    "id": "EGLL",
                    "icaoId": "EGLL",
                    "iataId": "LHR",
                    "site": "London/Heathrow Intl",
                    "lat": 51.477,
                    "lon": -0.461,
                    "elev": 26,
                    "state": "EN",
                    "country": "GB",
                    "siteType": ["METAR"]
                  }
                ]
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/stationinfo"))
                .withQueryParam("ids", equalTo("EGLL"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(mockResponseJson)));

        // First call - populates cache
        ResponseEntity<AirportResponse> firstCall = restTemplate.getForEntity(
                "/api/v1/airports/EGLL", AirportResponse.class);
        assertThat(firstCall.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second call - should hit cache
        ResponseEntity<AirportResponse> secondCall = restTemplate.getForEntity(
                "/api/v1/airports/EGLL", AirportResponse.class);
        assertThat(secondCall.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondCall.getBody()).isNotNull();
        assertThat(secondCall.getBody().icaoCode()).isEqualTo("EGLL");

        // Verify upstream was only called once
        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/stationinfo"))
                .withQueryParam("ids", equalTo("EGLL")));
    }

    @Test
    @DisplayName("Should return 404 when upstream returns 204 No Content for unknown ICAO")
    void shouldReturn404_WhenAirportNotFoundUpstream() {
        wireMockServer.stubFor(get(urlPathEqualTo("/stationinfo"))
                .withQueryParam("ids", equalTo("ZZZZ"))
                .willReturn(aResponse().withStatus(204)));

        ResponseEntity<ProblemDetail> response = restTemplate.getForEntity(
                "/api/v1/airports/ZZZZ", ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Airport Not Found");
    }

    @Test
    @DisplayName("Should return 400 Bad Request when ICAO code does not match 4-letter format")
    void shouldReturn400_WhenIcaoCodeIsInvalid() {
        ResponseEntity<ProblemDetail> response = restTemplate.getForEntity(
                "/api/v1/airports/INVALID123", ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Validation Failed");
    }

    @Test
    @DisplayName("Should retry on upstream 500 error up to max attempts and return 502 Bad Gateway")
    void shouldRetryAndReturn502_WhenUpstreamFailsWith500() {
        wireMockServer.stubFor(get(urlPathEqualTo("/stationinfo"))
                .withQueryParam("ids", equalTo("KLAX"))
                .willReturn(aResponse().withStatus(500)));

        ResponseEntity<ProblemDetail> response = restTemplate.getForEntity(
                "/api/v1/airports/KLAX", ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Bad Gateway");

        // Configured max-attempts is 3: initial call + 2 retries
        wireMockServer.verify(3, getRequestedFor(urlPathEqualTo("/stationinfo"))
                .withQueryParam("ids", equalTo("KLAX")));
    }
}
