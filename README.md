# Sporty Aviation API Wrapper

A production-ready Spring Boot microservice that wraps the public [AviationWeather.gov](https://aviationweather.gov/api/data) API to retrieve airport information based on 4-letter ICAO codes.

Designed with a strong focus on **scalability**, **resilience**, **extensibility**, and **observability**.

---

## Table of Contents

- [Features](#features)
- [Architecture & Design Decisions](#architecture--design-decisions)
  - [Service Layering & Extensibility](#service-layering--extensibility)
  - [Resilience Strategy](#resilience-strategy)
  - [Caching Strategy](#caching-strategy)
  - [High-Throughput Scalability](#high-throughput-scalability)
  - [Observability & Health Checks](#observability--health-checks)
  - [RFC 7807 Error Handling](#rfc-7807-error-handling)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
  - [Running with Maven](#running-with-maven)
  - [Running with Docker](#running-with-docker)
- [Running Automated Tests](#running-automated-tests)
- [API Documentation](#api-documentation)
  - [1. Get Airport by ICAO Code](#1-get-airport-by-icao-code)
  - [2. Actuator Endpoints](#2-actuator-endpoints)
- [AI Assistance Disclosure](#ai-assistance-disclosure)

---

## Features

- **ICAO Code Lookup**: Fetch comprehensive airport metadata (name, coordinates, elevation, IATA/FAA/WMO identifiers, site types) via `GET /api/v1/airports/{icaoCode}`.
- **Resilience4j Integration**: Configured with Circuit Breaker and Exponential Backoff Retries to protect upstream systems and maintain service availability during upstream outages.
- **Caffeine In-Memory Caching**: 1-hour TTL cache with statistics recording, preventing repeated calls for immutable airport data.
- **Virtual Threads (Project Loom)**: Enabled for lightweight, high-concurrency non-blocking thread scheduling over synchronous REST calls.
- **Provider Pattern**: Abstracted upstream API behind `AirportDataProvider` interface, making the service vendor-agnostic.
- **Standardized Error Responses**: Compliant with RFC 7807 `ProblemDetail` specification for all error scenarios (400, 404, 502, 503).
- **Comprehensive Actuator Metrics & Health**: Exposes circuit breaker states, cache hit/miss statistics, and standard JVM metrics.

---

## Architecture & Design Decisions

```
[ Client Request ]
        │
        ▼
[ AirportController ] ───────► Input Validation (@Pattern 4-letter alphabetic)
        │
        ▼
 [ AirportService ]   ───────► Caffeine Cache (@Cacheable)
        │                      (Cache hit returns immediately)
        ▼ (Cache miss)
[ AirportDataProvider ] (Interface)
        │
        ▼
[ AviationWeatherProvider ]
   ├── @CircuitBreaker (Resilience4j)
   ├── @Retry w/ Exponential Backoff
   └── RestClient (HTTP GET stationinfo)
        │
        ▼
[ AviationWeather.gov API ]
```

### Service Layering & Extensibility

- **Decoupled Providers**: The core business logic (`AirportService`) does not directly interact with `AviationWeather.gov`. Instead, it communicates through the `AirportDataProvider` interface. If a secondary aviation provider (e.g., OpenSky, FlightAware, FAA) needs to be integrated or substituted, only a new implementation of `AirportDataProvider` is created without modifying business or controller logic.
- **Separation of DTOs**: Internal upstream JSON models (`StationInfoEntry`) are decoupled from external API responses (`AirportResponse`). Changes to upstream data structures will never introduce breaking changes to downstream API consumers.

### Resilience Strategy

The service utilizes **Resilience4j** configured in `application.properties`:
1. **Aspect Ordering**:
   `Cache -> Circuit Breaker -> Retry -> HTTP Call`
   - Cache hits bypass the circuit breaker and retry logic completely.
   - If a call misses the cache, it enters the Circuit Breaker.
   - Retries occur inside the Circuit Breaker, ensuring individual retry failures contribute accurately to failure rates.
2. **Exponential Backoff Retry**:
   - `max-attempts: 3`
   - Initial wait: `300ms`, multiplier: `2.0`, with randomized jitter to prevent thundering herd problems.
   - Retries on network timeouts (`ResourceAccessException`), 5xx upstream errors, and `IOException`.
3. **Circuit Breaker**:
   - Sliding window of 10 calls (minimum 5 calls required).
   - 50% failure rate threshold triggers an `OPEN` state.
   - `OPEN` state lasts 15 seconds before transitioning to `HALF-OPEN` to test upstream recovery with 2 probe calls.
   - When open, requests fail fast with `503 Service Unavailable`, preventing resource exhaustion.

### Caching Strategy

- **Caffeine Cache**: Configured with a maximum capacity of 1,000 entries and a 1-hour time-to-live (`expireAfterWrite=1h`).
- **Selective Caching**: Only successful lookups are cached (`unless = "#result == null || !#result.isPresent()"`). Failed queries or unknown stations are not cached, allowing subsequent queries to discover newly added stations.
- **Case Normalization**: ICAO codes are converted to uppercase before querying the cache or upstream provider, ensuring cache key consistency regardless of client casing (`kjfk` vs `KJFK`).

### High-Throughput Scalability

- **Virtual Threads**: Enabled via `spring.threads.virtual.enabled=true`. Java 21 virtual threads enable millions of concurrent blocking I/O calls without thread starvation or the cognitive complexity of reactive streams.
- **Stateless Design**: All service components are completely stateless, allowing horizontal scaling behind standard load balancers without session replication.
- **Graceful Shutdown**: Enabled with a 15-second grace period (`server.shutdown=graceful`) to complete in-flight requests before container termination.

### Observability & Health Checks

- **Spring Boot Actuator**: Health endpoints expose upstream circuit breaker status:
  - `GET /actuator/health` displays system health and circuit breaker state.
  - `GET /actuator/metrics` exposes application metrics, including cache hits/misses (`cache.gets`) and circuit breaker call rates.
- **Structured Logging**: Diagnostic logging at INFO and DEBUG levels for tracking request flow, retries, and upstream errors.

### RFC 7807 Error Handling

All error responses are normalized using Spring 6's `ProblemDetail`:

| Scenario | HTTP Status | Detail Description |
|---|---|---|
| Invalid ICAO format | `400 Bad Request` | "ICAO code must consist of exactly 4 alphabetic characters" |
| Station not found upstream (HTTP 204) | `404 Not Found` | "Airport with ICAO code 'XXXX' not found" |
| Upstream communication / 5xx error | `502 Bad Gateway` | "Upstream aviation data service failed to respond normally." |
| Circuit breaker in OPEN state | `503 Service Unavailable` | "The upstream aviation service is temporarily unavailable. Please try again later." |

---

## Tech Stack

- **Java**: 21 (LTS)
- **Framework**: Spring Boot 3.4.5
- **Resilience**: Resilience4j 2.2.0 (Circuit Breaker, Retry, Actuator integration)
- **Caching**: Spring Cache + Caffeine 3.1.8
- **HTTP Client**: Spring Framework `RestClient` (with `SimpleClientHttpRequestFactory` timeouts)
- **Validation**: Jakarta Bean Validation (`spring-boot-starter-validation`)
- **Testing**: JUnit 5, AssertJ, WireMock Standalone 3.12.0
- **Containerization**: Docker (multi-stage build with Eclipse Temurin 21 Alpine)

---

## Prerequisites

- **Java 21** or later
- **Maven 3.8+** (or use the included `./mvnw` wrapper)
- **Docker** (optional, for containerized run)

---

## Getting Started

### Running with Maven

```bash
# Ensure JAVA_HOME points to Java 21
export JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || echo "$HOME/.jdks/jdk-21.0.13.1+2/Contents/Home")

# Build and run
./mvnw spring-boot:run
```

The application will start on port `8080`.

### Running with Docker

Build and run using the optimized multi-stage Dockerfile:

```bash
# Build Docker image
docker build -t sporty-aviation:latest .

# Run container
docker run -p 8080:8080 sporty-aviation:latest
```

---

## Running Automated Tests

The test suite includes full integration tests using **WireMock** to simulate upstream responses, cache behavior, retries, and error handling without external network dependencies:

```bash
./mvnw clean test
```

### Verified Test Scenarios:
1. `shouldReturnAirportDetails_WhenValidIcaoCodeProvided` — Validates 200 OK and response field mapping.
2. `shouldReturnCachedResult_WhenCalledSuccessively` — Verifies consecutive calls hit Caffeine cache and make zero additional upstream calls.
3. `shouldReturn404_WhenAirportNotFoundUpstream` — Validates upstream HTTP 204 translates into a 404 ProblemDetail.
4. `shouldReturn400_WhenIcaoCodeIsInvalid` — Validates format validation rejection (e.g. invalid length or alphanumeric).
5. `shouldRetryAndReturn502_WhenUpstreamFailsWith500` — Validates that 3 attempts are made before throwing 502 Bad Gateway.

---

## API Documentation

### 1. Get Airport by ICAO Code

Retrieves airport information for the specified 4-letter ICAO code.

- **URL**: `/api/v1/airports/{icaoCode}`
- **Method**: `GET`
- **Path Variable**: `icaoCode` (String, required, exactly 4 alphabetic characters)

#### Example Request:
```bash
curl -X GET http://localhost:8080/api/v1/airports/KJFK
```

#### Example 200 OK Response:
```json
{
  "icaoCode": "KJFK",
  "iataCode": "JFK",
  "faaCode": "JFK",
  "wmoCode": "74486",
  "name": "New York/JF Kennedy Intl",
  "latitude": 40.63916,
  "longitude": -73.76394,
  "elevationMeters": 3,
  "state": "NY",
  "country": "US",
  "siteTypes": [
    "METAR",
    "TAF"
  ]
}
```

#### Example 404 Not Found Response:
```json
{
  "type": "https://sportyaviation.api/errors/not-found",
  "title": "Airport Not Found",
  "status": 404,
  "detail": "Airport with ICAO code 'ZZZZ' not found",
  "instance": "/api/v1/airports/ZZZZ",
  "timestamp": "2026-11-24T13:00:00Z"
}
```

#### Example 400 Bad Request Response:
```json
{
  "type": "https://sportyaviation.api/errors/validation-error",
  "title": "Validation Failed",
  "status": 400,
  "detail": "getAirport.icaoCode: ICAO code must consist of exactly 4 alphabetic characters",
  "instance": "/api/v1/airports/JFK1",
  "timestamp": "2026-11-24T13:00:00Z"
}
```

### 2. Actuator Endpoints

- **Health Check**:
  ```bash
  curl http://localhost:8080/actuator/health
  ```
  Returns detailed health including the `aviationWeather` circuit breaker status (`UP`, `CIRCUIT_OPEN`, etc.).

- **Metrics**:
  ```bash
  curl http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.calls
  curl http://localhost:8080/actuator/metrics/cache.gets
  ```

---

## AI Assistance Disclosure

As per assignment requirements:
- AI tooling was utilized for rapid boilerplate generation, project scaffolding, and documentation structuring.
- All architectural decisions (provider abstraction, aspect ordering, resilience policies, caching rules, virtual thread adoption, and WireMock integration tests) were designed, reviewed, and verified for production readiness.
