package com.muhammadriwandi.sportyaviation.client;

import com.muhammadriwandi.sportyaviation.client.dto.StationInfoEntry;
import com.muhammadriwandi.sportyaviation.dto.AirportResponse;
import com.muhammadriwandi.sportyaviation.exception.UpstreamApiException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Component
public class AviationWeatherProvider implements AirportDataProvider {

    private static final Logger log = LoggerFactory.getLogger(AviationWeatherProvider.class);
    private static final ParameterizedTypeReference<List<StationInfoEntry>> STATION_LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public AviationWeatherProvider(
            RestClient.Builder restClientBuilder,
            @Value("${aviation.weather.base-url:https://aviationweather.gov/api/data}") String baseUrl,
            @Value("${aviation.weather.user-agent:sporty-aviation-wrapper/1.0}") String userAgent,
            @Value("${aviation.weather.connect-timeout-ms:2000}") int connectTimeout,
            @Value("${aviation.weather.read-timeout-ms:3000}") int readTimeout) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeout));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeout));

        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    @CircuitBreaker(name = "aviationWeather")
    @Retry(name = "aviationWeather")
    public Optional<AirportResponse> fetchAirportByIcao(String icaoCode) {
        log.debug("Calling upstream AviationWeather API for ICAO: {}", icaoCode);
        try {
            var responseEntity = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/stationinfo")
                            .queryParam("ids", icaoCode)
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        log.warn("Upstream returned 4xx client error: {} {}", resp.getStatusCode(), resp.getStatusText());
                        throw new UpstreamApiException("Upstream client error: " + resp.getStatusCode(), resp.getStatusCode().value());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        log.error("Upstream returned 5xx server error: {} {}", resp.getStatusCode(), resp.getStatusText());
                        throw new UpstreamApiException("Upstream server error: " + resp.getStatusCode(), resp.getStatusCode().value());
                    })
                    .toEntity(STATION_LIST_TYPE);

            if (responseEntity.getStatusCode().value() == 204 || responseEntity.getBody() == null || responseEntity.getBody().isEmpty()) {
                log.info("No airport data returned from upstream for ICAO: {}", icaoCode);
                return Optional.empty();
            }

            StationInfoEntry entry = responseEntity.getBody().getFirst();
            return Optional.of(mapToAirportResponse(entry));
        } catch (UpstreamApiException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            log.error("Network I/O failure calling upstream AviationWeather API: {}", ex.getMessage());
            throw new UpstreamApiException("Network I/O error communicating with upstream: " + ex.getMessage(), ex, 502);
        } catch (Exception ex) {
            log.error("Unexpected error fetching airport data for {}: {}", icaoCode, ex.getMessage(), ex);
            throw new UpstreamApiException("Failed to retrieve airport data: " + ex.getMessage(), ex, 502);
        }
    }

    private AirportResponse mapToAirportResponse(StationInfoEntry entry) {
        return new AirportResponse(
                entry.icaoId() != null && !entry.icaoId().isBlank() ? entry.icaoId() : entry.id(),
                entry.iataId(),
                entry.faaId(),
                entry.wmoId(),
                entry.site(),
                entry.lat(),
                entry.lon(),
                entry.elev(),
                entry.state(),
                entry.country(),
                entry.siteType() != null ? entry.siteType() : List.of()
        );
    }
}
