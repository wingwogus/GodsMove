# 농장 날씨 기능 확장 — 구현 계획

Design doc: `docs/superpowers/specs/2026-07-14-farm-weather-history-detail-design.md`
(read for full context/rationale; this file is the task breakdown for execution).

## Global Constraints

These apply to every task below. Violating any of these is a spec failure.

- **Never touch `farming`/`FarmingRecord` files.** No file under
  `com.chamchamcham.*.farming`, no `FarmingRecord*` class, no
  `FarmingRecordController`/`FarmingRecordService`/DTOs. A different session is
  actively modifying that domain concurrently; touching it creates merge
  conflicts and is out of scope regardless.
- Module layering: `api -> application -> domain`. `domain` must not depend on
  `application`/`api`. `application` must not depend on `api`. Follow existing
  package conventions: `application/weather/*` for ports/results/commands,
  `api/weather/*` for adapters/config, `api/farm/controller`+`api/farm/dto` for
  controller/DTOs.
- Controllers stay thin (bind, validate via `@Valid`, map to commands, delegate,
  shape the HTTP response). Business rules live in `@Service` application
  classes. Application services throw `BusinessException(ErrorCode.X)` for
  expected failures and never return `ResponseEntity`/`ApiResponse`/API DTOs.
- Reuse existing patterns instead of inventing new ones:
  - Adapter constructor style: primary constructor takes `RestClient` + config
    values (testable), secondary `@Autowired` constructor builds the
    `RestClient` from `RestClient.Builder` + `@ConfigurationProperties`, with a
    `SimpleClientHttpRequestFactory` for timeouts. See
    `api/src/main/kotlin/com/chamchamcham/api/weather/KmaWeatherProvider.kt`.
  - Pre-encoded `URI.create("$baseUrl/$path?serviceKey=$serviceKey&...")` to
    avoid double-encoding the data.go.kr service key (same file,
    `requestItems`).
  - Response envelope: `response.header.resultCode`/`resultMsg`,
    `response.body.items.item` — same shape used by `KmaWeatherProvider`'s
    private `KmaResponse`/`KmaResponseBody`/`KmaBody`/`KmaItems`/`KmaItem`
    classes.
  - Pure-function helpers as `object`s with no I/O, like
    `api/src/main/kotlin/com/chamchamcham/api/weather/GeoToGridConverter.kt`
    and `KmaBaseTimeResolver.kt` — mirror this style for new pure functions.
  - `*Result`/`*Command` object-with-nested-data-class convention: see
    `application/src/main/kotlin/com/chamchamcham/application/farm/FarmResult.kt`
    or similar existing `*Result.kt` files.
- Error handling: hard provider failure (network exception, non-`"00"` result
  code other than the specific "no data" codes named in a task) →
  `BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)`. Genuine "no data
  for this query" → return `null` from the port method, never throw.
- Kotlin style: `val` over `var`, `data class` for DTOs/commands/results,
  explicit names matching existing conventions in the `weather` package.
- Every task must leave `./gradlew test` (run from `backend/`) green before
  being marked done. Add regression tests before changing existing parsing
  behavior (`AGENTS.md` requirement).
- Commit at the end of each task with a Conventional Commits message, e.g.
  `feat(weather): 습도/풍속 파싱 추가`. One task = one commit (or a small tight
  series if the implementer's own TDD red/green steps warrant it — but squash
  to a single coherent commit before finishing the task).

Key existing files each task will touch or must be aware of (do not paste this
list to the reviewer as if it were the spec — it's orientation, the task brief
has the actual requirements):
- `backend/application/src/main/kotlin/com/chamchamcham/application/weather/WeatherProvider.kt`
- `backend/application/src/main/kotlin/com/chamchamcham/application/weather/WeatherSnapshot.kt`
- `backend/application/src/main/kotlin/com/chamchamcham/application/weather/FarmWeatherService.kt`
- `backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`
- `backend/api/src/main/kotlin/com/chamchamcham/api/weather/KmaWeatherProvider.kt`
- `backend/api/src/main/kotlin/com/chamchamcham/api/weather/GeoToGridConverter.kt`
- `backend/api/src/main/kotlin/com/chamchamcham/api/weather/KmaBaseTimeResolver.kt`
- `backend/api/src/main/kotlin/com/chamchamcham/api/weather/WeatherKmaProperties.kt` / `WeatherKmaConfig.kt`
- `backend/api/src/main/kotlin/com/chamchamcham/api/farm/controller/FarmWeatherController.kt`
- `backend/api/src/main/kotlin/com/chamchamcham/api/farm/dto/WeatherResponses.kt`
- `backend/domain/src/main/kotlin/com/chamchamcham/domain/farm/Farm.kt`
- `backend/domain/src/main/kotlin/com/chamchamcham/domain/farm/FarmRepository.kt`
- Tests: `backend/application/src/test/kotlin/com/chamchamcham/application/weather/FarmWeatherServiceTest.kt`,
  `backend/api/src/test/kotlin/com/chamchamcham/api/weather/KmaWeatherProviderTest.kt`,
  `backend/api/src/test/kotlin/com/chamchamcham/api/farm/controller/FarmWeatherControllerTest.kt`
- YAML config: `backend/api/src/main/resources/application-{local,dev,prod}.yml`,
  `backend/api/src/test/resources/application-test.yml` (all four have an
  existing `weather.kma.*` block to mirror).

Tasks 1-4 and 5-8 touch overlapping files in places (`FarmWeatherService.kt`,
`FarmWeatherController.kt`, `WeatherResponses.kt`, `ErrorCode.kt`) — **execute
tasks strictly in numeric order, one at a time, never in parallel.**

---

## Task 1: `ErrorCode` additions

Add two new entries to
`backend/application/src/main/kotlin/com/chamchamcham/application/exception/ErrorCode.kt`:

```kotlin
WEATHER_DATE_IN_FUTURE("WEATHER_003", "error.weather_date_in_future", 400),
WEATHER_DAILY_DATA_NOT_FOUND("WEATHER_004", "error.weather_daily_data_not_found", 404),
```

Place them near the existing `WEATHER_001`/`WEATHER_002` entries
(`WEATHER_LOCATION_REQUIRED`, `WEATHER_PROVIDER_UNAVAILABLE`) to keep the enum
grouped by feature, matching the existing file's ordering convention.

No new external calls, no new classes. This is a zero-risk prerequisite for
later tasks — nothing else in the codebase references these two codes yet, so
there's nothing to break.

**Verification:** `./gradlew :application:compileKotlin` succeeds (from
`backend/`). No new tests needed for an enum addition with no behavior yet,
but confirm `./gradlew test` (full suite) stays green.

---

## Task 2: Humidity/wind speed parsing (no new external calls)

Extend `WeatherSnapshot` and `KmaWeatherProvider` to expose humidity and wind
speed, which are already present in the ultra-short-term-observation payload
`KmaWeatherProvider` fetches today but currently discards.

1. `backend/application/src/main/kotlin/com/chamchamcham/application/weather/WeatherSnapshot.kt`:
   add two new fields with default `null`, keeping the existing three as-is:
   ```kotlin
   data class WeatherSnapshot(
       val temperature: Int,
       val skyCondition: String,
       val observedAt: LocalDateTime,
       val humidity: Int? = null,
       val windSpeed: Double? = null
   )
   ```
2. `backend/api/src/main/kotlin/com/chamchamcham/api/weather/KmaWeatherProvider.kt`:
   in `fetchCurrentWeather`, the `ncst` list (from `getUltraSrtNcst`) already
   contains `REH` (humidity, %, integer string) and `WSD` (wind speed, m/s,
   decimal string) category rows for the current observation — parse them the
   same way `temperature` is parsed from `T1H` (`firstOrNull { it.category ==
   "REH" }?.obsrValue?.toDoubleOrNull()?.roundToInt()` for humidity;
   `?.toDoubleOrNull()` without rounding for wind speed since it's a `Double`
   field). Unlike temperature, missing/unparseable humidity or wind speed must
   **not** throw — default to `null` (temperature stays the only hard-required
   field, since that's the existing tested failure behavior for
   `WEATHER_PROVIDER_UNAVAILABLE` and must not change).
3. Pass the parsed values into the `WeatherSnapshot(...)` construction at the
   end of `fetchCurrentWeather`.

**Test requirements** (`backend/api/src/test/kotlin/com/chamchamcham/api/weather/KmaWeatherProviderTest.kt`):
- The existing NCST fixture JSON already includes `REH`/`WSD` values (confirmed
  present) — add assertions that `snapshot.humidity`/`snapshot.windSpeed`
  match those fixture values in the existing success test.
- Add a case where the NCST fixture is missing `REH`/`WSD` rows entirely (or
  has an unparseable value) and assert the snapshot still succeeds with
  `humidity`/`windSpeed` as `null` (i.e. these two fields never cause
  `WEATHER_PROVIDER_UNAVAILABLE`).
- Do not modify or weaken the existing test asserting that a missing/invalid
  `T1H` (temperature) still throws `WEATHER_PROVIDER_UNAVAILABLE`.

Report file: `.superpowers/sdd/task-2-report.md`.

---

## Task 3: Result-type refactor + address + default-farm support

This task changes `FarmWeatherService.getCurrentWeather`'s return type and
adds farm-optional endpoints. It's the largest task in this plan — read
carefully, it touches five files plus two test files.

### 3a. New result type

Create `backend/application/src/main/kotlin/com/chamchamcham/application/weather/FarmWeatherResult.kt`:
```kotlin
package com.chamchamcham.application.weather

import java.time.LocalDate

object FarmWeatherResult {
    data class CurrentDetail(
        val snapshot: WeatherSnapshot,
        val roadAddress: String,
        val precipitationProbability: Int?,
        val forecast: List<DailyForecast>,
        val uvIndex: Int?
    )
}

data class DailyForecast(
    val date: LocalDate,
    val minTemperature: Int?,
    val maxTemperature: Int?,
    val skyCondition: String?
)
```
(`DailyForecast` is used by Task 4 too — define it now so Task 4 doesn't need
to touch this file again. Leave `precipitationProbability`/`forecast`/
`uvIndex` as fields that Task 4 and the (separate, spike-gated) UV work will
populate — for this task, `FarmWeatherService` should construct
`CurrentDetail` with `precipitationProbability = null`, `forecast =
emptyList()`, `uvIndex = null`.)

### 3b. `FarmWeatherService` changes

`backend/application/src/main/kotlin/com/chamchamcham/application/weather/FarmWeatherService.kt`:

- Inject `FarmRepository` is already present. Change
  `getCurrentWeather(memberId: UUID, farmId: UUID)`'s return type from
  `WeatherSnapshot` to `FarmWeatherResult.CurrentDetail`, keeping the existing
  ownership check (`findByIdAndOwnerId` → `FARM_NOT_FOUND`) and location check
  (`WEATHER_LOCATION_REQUIRED`) unchanged. After calling
  `weatherProvider.fetchCurrentWeather(latitude, longitude)`, wrap the result:
  ```kotlin
  return FarmWeatherResult.CurrentDetail(
      snapshot = snapshot,
      roadAddress = farm.roadAddress,
      precipitationProbability = null,
      forecast = emptyList(),
      uvIndex = null
  )
  ```
- Add a default-farm resolution path. In
  `backend/domain/src/main/kotlin/com/chamchamcham/domain/farm/FarmRepository.kt`
  add:
  ```kotlin
  fun findFirstByOwnerIdOrderByCreatedAtAsc(ownerId: UUID): Farm?
  ```
  (`Farm` extends `BaseTimeEntity`, which has a public-getter `createdAt:
  LocalDateTime` — confirmed usable in a JPA derived query.)
- In `FarmWeatherService`, add:
  ```kotlin
  private fun resolveDefaultFarm(memberId: UUID): Farm =
      farmRepository.findFirstByOwnerIdOrderByCreatedAtAsc(memberId)
          ?: throw BusinessException(ErrorCode.FARM_NOT_FOUND)

  fun getCurrentWeather(memberId: UUID): FarmWeatherResult.CurrentDetail =
      getCurrentWeather(memberId, resolveDefaultFarm(memberId).id!!)
  ```
  Do not change the signature of the existing `getCurrentWeather(memberId,
  farmId)` — this is a new overload, additive only. (`getDailyWeather`'s
  default-farm overload is added in Task 7, once `getDailyWeather` itself
  exists — don't add it here.)

### 3c. Response DTO + controller

`backend/api/src/main/kotlin/com/chamchamcham/api/farm/dto/WeatherResponses.kt`:
add an `address: String` field to `CurrentWeatherResponse`, and change
`from(...)` to accept `FarmWeatherResult.CurrentDetail` instead of
`WeatherSnapshot`:
```kotlin
data class CurrentWeatherResponse(
    val temperature: Int,
    val weatherCondition: String,
    val observedAt: LocalDateTime,
    val address: String
) {
    companion object {
        fun from(result: FarmWeatherResult.CurrentDetail): CurrentWeatherResponse =
            CurrentWeatherResponse(
                temperature = result.snapshot.temperature,
                weatherCondition = result.snapshot.skyCondition,
                observedAt = result.snapshot.observedAt,
                address = result.roadAddress
            )
    }
}
```
(Leave `precipitationProbability`/`forecast`/`uvIndex` off the response DTO
for this task — Task 4 adds `precipitationProbability`/`forecast` to this same
class; the UV field is added by the separate spike-gated UV task. Don't
pre-add empty fields for work that hasn't landed yet.)

`backend/api/src/main/kotlin/com/chamchamcham/api/farm/controller/FarmWeatherController.kt`:
update the existing `getCurrentWeather` handler's call site to
`CurrentWeatherResponse.from(result)` (the variable was previously named
`snapshot`; rename to `result` for clarity). Then add a farm-optional sibling
endpoint:
```kotlin
@GetMapping("/weather")
fun getCurrentWeatherForDefaultFarm(
    @AuthenticationPrincipal memberId: String?
): ResponseEntity<ApiResponse<WeatherResponses.CurrentWeatherResponse>> {
    val result = farmWeatherService.getCurrentWeather(parseMemberId(memberId))
    return ResponseEntity.ok(ApiResponse.ok(WeatherResponses.CurrentWeatherResponse.from(result)))
}
```
This maps to `GET /api/v1/farms/weather` (two path segments after `/farms/`,
vs. the existing `/farms/{farmId}/weather`'s three segments) — no routing
ambiguity with the existing `{farmId}` endpoint.

### 3d. Update existing tests

- `backend/application/src/test/kotlin/com/chamchamcham/application/weather/FarmWeatherServiceTest.kt`:
  update existing `getCurrentWeather` tests to assert against
  `FarmWeatherResult.CurrentDetail` (checking `.snapshot` and `.roadAddress`)
  instead of a raw `WeatherSnapshot`. Add a new test for the default-farm
  overload: given a member with farms, `getCurrentWeather(memberId)` resolves
  the earliest-created farm and returns its weather; given a member with no
  farms, throws `FARM_NOT_FOUND`.
- `backend/api/src/test/kotlin/com/chamchamcham/api/farm/controller/FarmWeatherControllerTest.kt`:
  update the existing test to mock `FarmWeatherService.getCurrentWeather(memberId,
  farmId)` returning a `FarmWeatherResult.CurrentDetail` and assert the
  response JSON includes `address`. Add a test for the new `GET
  /api/v1/farms/weather` endpoint (mocking the no-farmId overload).

**Verification:** `./gradlew :application:test :api:test --tests "*weather*" --tests "*Weather*"` green, then full `./gradlew test`.

Report file: `.superpowers/sdd/task-3-report.md`.

---

## Task 4: 5-day forecast panel + precipitation probability

Depends on Task 3 (uses `FarmWeatherResult.CurrentDetail`/`DailyForecast`
already defined there).

### Background the implementer needs (KMA `getVilageFcst` quirks, verified against official KMA API guide docs — do not re-derive, just apply)

- `getVilageFcst` is the **same product** (`VilageFcstInfoService_2.0`) as the
  already-integrated `getUltraSrtNcst`/`getUltraSrtFcst` — same base URL, same
  `serviceKey`, same `nx`/`ny` grid from `GeoToGridConverter`. Just a different
  `path` segment passed to the existing `requestItems` helper.
- Category codes relevant here: `TMP` (1시간기온), `TMN` (일최저기온, only ever
  at `fcstTime="0600"`), `TMX` (일최고기온, only ever at `fcstTime="1500"`),
  `SKY`, `PTY`, `POP` (강수확률, %).
- **Critical: TMN/TMX presence for "today" and for the 5th day depends on
  `base_time`.** The issuance schedule is 02/05/08/11/14/17/20/23 (KST). A call
  made using `base_time` 02–14 covers today through day+3 (4 calendar days
  total); a call using `base_time` 17/20/23 covers today through day+4 (5
  calendar days total) but **loses today's TMN (only present at base_time=02)
  and today's TMX (only present at base_time 02/05/08/11)**. There is no
  single `base_time` that guarantees both "today has TMN+TMX" and "5 days are
  present" — this is an inherent characteristic of the API's publishing
  schedule, not a bug to route around.
- `PTY` values from `getVilageFcst` are only ever `{0=없음, 1=비, 2=비/눈,
  3=눈, 4=소나기}` — codes 5/6/7 (빗방울류) belong to the ultra-short-term
  operations only and never appear here. Reusing the existing
  `KmaWeatherProvider.skyConditionOf(sky, pty)` companion function for this
  operation's values is safe (it's a superset), but add a one-line comment at
  the `getVilageFcst`-parsing call site noting this operation only emits
  `{0,1,2,3,4}`.

### Design

1. `backend/api/src/main/kotlin/com/chamchamcham/api/weather/KmaBaseTimeResolver.kt`:
   add a new function (do not modify the existing ncst/fcst resolvers) that,
   given a reference `LocalDateTime`, returns the most recently-published
   `base_date`/`base_time` from the schedule `{02, 05, 08, 11, 14, 17, 20,
   23}` (mirror the existing resolver's style — publication normally lags the
   nominal hour by ~10 minutes for `getVilageFcst`; follow whatever lag
   convention the existing ncst/fcst resolvers already use for consistency
   rather than inventing a new rule).
2. `backend/application/src/main/kotlin/com/chamchamcham/application/weather/WeatherForecast.kt`:
   ```kotlin
   data class WeatherForecast(
       val precipitationProbability: Int?,
       val dailyForecasts: List<DailyForecast>
   )
   ```
   (`DailyForecast` already exists from Task 3.)
3. `backend/application/src/main/kotlin/com/chamchamcham/application/weather/WeatherProvider.kt`:
   add `fun fetchForecastPanel(latitude: Double, longitude: Double):
   WeatherForecast`.
4. `KmaWeatherProvider.fetchForecastPanel`: call `requestItems(path =
   "getVilageFcst", base = <new resolver>, nx, ny)`, then:
   - Group items by `fcstDate`.
   - For each date: `minTemperature` = the `TMN`@`0600` value if present, else
     the minimum of all `TMP` values for that date, else `null`.
     `maxTemperature` = the `TMX`@`1500` value if present, else the maximum of
     all `TMP` values for that date, else `null`. `skyCondition` = `SKY`+`PTY`
     nearest to midday (`fcstTime` closest to `"1200"`) via `skyConditionOf`,
     else `null`.
   - Return every date present in the response as a `DailyForecast` (this
     will be 4 or 5 entries depending on when the call happens — do not pad to
     a fixed 5; a shorter list is the correct, expected output at some times
     of day).
   - `precipitationProbability`: pick the `POP` value nearest to the current
     time among today's entries (same nearest-time selection style as the
     existing `resolveSkyCondition`'s `minByOrNull` pattern).
5. `FarmWeatherService.getCurrentWeather(memberId, farmId)`: after building the
   snapshot, call `runCatching { weatherProvider.fetchForecastPanel(latitude,
   longitude) }.getOrNull()` and merge into the `CurrentDetail` construction
   (`precipitationProbability = forecast?.precipitationProbability`, `forecast
   = forecast?.dailyForecasts ?: emptyList()`) — a failure here must not
   propagate and must not affect the snapshot/address portion of the
   response.
6. `WeatherResponses.CurrentWeatherResponse`: add `precipitationProbability:
   Int?` and `forecast: List<DailyWeatherResponse>` (reuse or define a small
   response-side `DailyWeatherResponse(date, weatherCondition,
   minTemperature, maxTemperature)` nested type in the same file — note this
   name will collide conceptually with the past-date `DailyWeatherResponse`
   from Task 8; if Task 8 hasn't landed yet when you do this task, name this
   one clearly, e.g. `ForecastDayResponse`, to avoid a future collision. Check
   what Task 8 actually named its class before picking — if unsure, use
   `ForecastDayResponse` for this task's list-item type).

### Tests

- `KmaWeatherProviderTest`: new fixtures/tests covering: (a) a `base_time`
  02-14 response → verify 4 dates returned; (b) a `base_time` 17/20/23
  response → verify 5 dates returned, with today missing TMN/TMX and thus
  falling back to TMP-derived min/max; (c) precipitation probability
  selection.
- `FarmWeatherServiceTest`: forecast-provider failure (`runCatching` path)
  still returns a successful `CurrentDetail` with `forecast = emptyList()`
  and `precipitationProbability = null`.
- `FarmWeatherControllerTest`: response JSON includes `forecast`/
  `precipitationProbability` fields.

**Verification:** `./gradlew :application:test :api:test --tests "*weather*" --tests "*Weather*"` green, then full `./gradlew test`.

Report file: `.superpowers/sdd/task-4-report.md`.

---

## Task 5: ASOS nearest-station lookup (pure functions, no I/O, no external calls)

Independent of Tasks 1-4 — safe to implement in isolation and test standalone.

1. `backend/api/src/main/kotlin/com/chamchamcham/api/weather/AsosStation.kt`:
   ```kotlin
   data class AsosStation(val id: String, val name: String, val latitude: Double, val longitude: Double)
   ```
2. `backend/api/src/main/kotlin/com/chamchamcham/api/weather/AsosStations.kt`:
   a static `object AsosStations { val ALL: List<AsosStation> = listOf(...) }`
   containing South Korea's ~100 official ASOS station IDs, names, and
   lat/lng. **You must source real coordinates** — look up the KMA
   observation station metadata (data.kma.go.kr publishes station lists with
   lat/lng per `stnId`) rather than inventing placeholder values. If you
   cannot find an authoritative source for the full list in this task,
   report `NEEDS_CONTEXT` rather than fabricating coordinates — this data
   must be real since it's used to compute which station's weather gets
   attributed to a farm.
3. `backend/api/src/main/kotlin/com/chamchamcham/api/weather/NearestAsosStationResolver.kt`:
   pure function, mirroring `GeoToGridConverter`'s style (an `object` with a
   single entry-point function, no Spring annotations, no I/O):
   ```kotlin
   object NearestAsosStationResolver {
       fun resolve(latitude: Double, longitude: Double, stations: List<AsosStation> = AsosStations.ALL): AsosStation
   }
   ```
   Use haversine distance; iterate all ~100 stations and take the minimum (no
   spatial index needed at this scale).
4. `backend/api/src/main/kotlin/com/chamchamcham/api/weather/AsosSkyConditionMapper.kt`:
   pure function approximating sky condition from ASOS daily fields:
   ```kotlin
   object AsosSkyConditionMapper {
       fun of(avgTca: Double?, sumRn: Double?): String
   }
   ```
   Logic: `sumRn != null && sumRn > 0.0` → `"비"`. Otherwise, by `avgTca`
   (average total cloud cover, 0–10 scale): `null` → `"정보없음"`; `0.0..2.0` →
   `"맑음"`; `2.0..7.0` → `"구름많음"` (exclusive lower bound already covered by
   the previous range); `7.0..10.0` → `"흐림"`. Match the vocabulary used by
   the existing `KmaWeatherProvider.skyConditionOf` (맑음/구름많음/흐림/비 etc.)
   for consistency, but this is a distinct function operating on different
   input fields — do not try to unify them into one function.

### Tests

Pure JUnit style, no Spring context, mirroring
`backend/api/src/test/kotlin/com/chamchamcham/api/weather/GeoToGridConverterTest.kt`
and `KmaBaseTimeResolverTest.kt`:
- `NearestAsosStationResolverTest`: given a small fixed list of 2-3 stations
  with known coordinates, assert the nearest one is picked for a few sample
  points.
- `AsosSkyConditionMapperTest`: table-driven cases across the cloud-cover
  bands and the rain-override case, plus the `avgTca == null` case.

**Verification:** `./gradlew :api:test --tests "*Asos*"` green, then full `./gradlew test`.

Report file: `.superpowers/sdd/task-5-report.md`.

---

## Task 6: ASOS config + adapter (network I/O, JSON quirks, error handling)

Depends on Task 5 (`AsosStation`, `NearestAsosStationResolver`,
`AsosSkyConditionMapper`). Independent of Tasks 1-4.

### Background the implementer needs (verified against the official KMA ASOS daily API guide — do not re-derive)

- Operation: `AsosDalyInfoService/getWthrDataList`, base URL
  `https://apis.data.go.kr/1360000/AsosDalyInfoService`. Params:
  `serviceKey`, `dataCd=ASOS`, `dateCd=DAY`, `startDt=YYYYMMDD`,
  `endDt=YYYYMMDD`, `stnIds=<station id>`, `pageNo=1`, `numOfRows`,
  `dataType=JSON` — same conventions as `KmaWeatherProvider.requestItems`
  (pre-encoded `URI.create`, do not double-encode the service key).
- Response envelope matches the existing pattern:
  `response.header.resultCode`/`resultMsg`, `response.body.items.item`.
- Confirmed response fields (all except `stnId`/`stnNm`/`tm` are optional per
  the official spec — treat as nullable, and be aware the JSON key can be
  **entirely absent**, not just `null`, when a station's sensor doesn't
  measure that value): `minTa` (최저기온, °C), `maxTa` (최고기온, °C), `avgRhm`
  (평균습도, %), `avgWs` (평균풍속, m/s), `sumRn` (일강수량, mm), `avgTca`
  (평균전운량, 0-10 scale — confirmed present in the daily API, not
  hourly-only).
- **Known JSON quirk (data.go.kr-wide, confirmed via official guide + 2
  independent sources): when the result set has exactly 1 row, `item` is
  serialized as a single JSON object, not a one-element array; 2+ rows come
  back as an array.** Since this adapter always queries a single date
  (`startDt == endDt`), a single-object response is the *likely* case, not an
  edge case — it must be handled correctly, not just defensively guessed at.
  The recommended fix: give this adapter's `RestClient` (or the underlying
  `ObjectMapper`/`Jackson2ObjectMapperBuilder`) a dedicated Jackson
  configuration with `DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY`
  enabled, scoped only to this adapter's `RestClient` bean (do not enable this
  globally — it must not affect `KmaWeatherProvider`'s or any other adapter's
  parsing).
- **`resultCode` handling**: the official error-code table defines
  `resultCode="03"` as `NODATA_ERROR` ("데이터없음 에러"). Whether a genuine
  "valid station + valid date but no observation that day" case actually comes
  back as `resultCode="03"` or as `resultCode="00"` with an empty `items` list
  is **not confirmed** by any source found during design — treat **both**
  as "no data for this date" (return `null` from the port), and treat any
  other non-`"00"` `resultCode`, or a transport/network exception, as a hard
  failure (`BusinessException(ErrorCode.WEATHER_PROVIDER_UNAVAILABLE)`).

### Design

1. New `@ConfigurationProperties(prefix = "weather.asos")` class
   `WeatherAsosProperties` + `@Configuration` class `WeatherAsosConfig`,
   mirroring `WeatherKmaProperties`/`WeatherKmaConfig` exactly (same field
   names: `baseUrl`, `serviceKey`, `connectTimeoutMillis`,
   `readTimeoutMillis`).
2. Add to all four YAML files (mirror the existing `weather.kma` block
   exactly, same env-var-with-default style already used per profile):
   `backend/api/src/main/resources/application-local.yml`,
   `application-dev.yml`, `application-prod.yml`,
   `backend/api/src/test/resources/application-test.yml`:
   ```yaml
   weather:
     asos:
       base-url: https://apis.data.go.kr/1360000/AsosDalyInfoService
       service-key: ${KMA_SERVICE_KEY}          # test yml: a literal test value, matching how weather.kma.service-key is set there
       connect-timeout-millis: ${KMA_CONNECT_TIMEOUT_MILLIS:2000}
       read-timeout-millis: ${KMA_READ_TIMEOUT_MILLIS:2000}
   ```
3. New port `backend/application/src/main/kotlin/com/chamchamcham/application/weather/HistoricalWeatherProvider.kt`:
   ```kotlin
   interface HistoricalWeatherProvider {
       fun fetchDailySummary(latitude: Double, longitude: Double, date: LocalDate): DailyWeatherSummary?
   }
   ```
4. New `backend/application/src/main/kotlin/com/chamchamcham/application/weather/DailyWeatherSummary.kt`:
   ```kotlin
   data class DailyWeatherSummary(
       val date: LocalDate,
       val skyCondition: String,
       val minTemperature: Int,
       val maxTemperature: Int
   )
   ```
5. New `backend/api/src/main/kotlin/com/chamchamcham/api/weather/KmaAsosWeatherProvider.kt`
   implementing `HistoricalWeatherProvider`, following `KmaWeatherProvider`'s
   constructor/URI-building conventions exactly (primary constructor +
   `@Autowired` secondary constructor building its own dedicated `RestClient`
   with the single-value-as-array Jackson feature enabled). Flow:
   `NearestAsosStationResolver.resolve(latitude, longitude)` → build request →
   parse → `minTa`/`maxTa` → `Int` (round if needed) → `AsosSkyConditionMapper.of(avgTca,
   sumRn)` → return `DailyWeatherSummary`, or `null` per the no-data rules
   above, or throw `WEATHER_PROVIDER_UNAVAILABLE` for hard failures.

### Tests

New `backend/api/src/test/kotlin/com/chamchamcham/api/weather/KmaAsosWeatherProviderTest.kt`,
`MockRestServiceServer`-based like `KmaWeatherProviderTest`:
- Single-object JSON response (the common case) parses correctly.
- Multi-item array JSON response parses correctly (defensive coverage even
  though this adapter always queries one day).
- `resultCode="03"` → `null`.
- `resultCode="00"` + empty `items` → `null`.
- Any other `resultCode`, and a transport exception → both throw
  `BusinessException(WEATHER_PROVIDER_UNAVAILABLE)`.
- A response missing optional fields (e.g. no `avgTca` key at all) still
  parses without throwing, producing a `DailyWeatherSummary` with a
  best-effort `skyCondition` from `AsosSkyConditionMapper`.

**Verification:** `./gradlew :api:test --tests "*Asos*"` green, then full `./gradlew test`.

Report file: `.superpowers/sdd/task-6-report.md`.

---

## Task 7: Past-date application service + validation

Depends on Task 6 (`HistoricalWeatherProvider`, `DailyWeatherSummary`) and
Task 1 (`ErrorCode` entries). Independent of Tasks 2-4.

In `backend/application/src/main/kotlin/com/chamchamcham/application/weather/FarmWeatherService.kt`,
inject `HistoricalWeatherProvider` (constructor injection, alongside the
existing `WeatherProvider`) and add:

```kotlin
fun getDailyWeather(memberId: UUID, farmId: UUID, date: LocalDate): DailyWeatherSummary {
    val farm = farmRepository.findByIdAndOwnerId(farmId, memberId)
        ?: throw BusinessException(ErrorCode.FARM_NOT_FOUND)

    val latitude = farm.latitude
    val longitude = farm.longitude
    if (latitude == null || longitude == null) {
        throw BusinessException(ErrorCode.WEATHER_LOCATION_REQUIRED)
    }
    if (date.isAfter(LocalDate.now())) {
        throw BusinessException(ErrorCode.WEATHER_DATE_IN_FUTURE)
    }

    return historicalWeatherProvider.fetchDailySummary(latitude, longitude, date)
        ?: throw BusinessException(ErrorCode.WEATHER_DAILY_DATA_NOT_FOUND)
}

fun getDailyWeather(memberId: UUID, date: LocalDate): DailyWeatherSummary =
    getDailyWeather(memberId, resolveDefaultFarm(memberId).id!!, date)
```

(`resolveDefaultFarm` already exists from Task 3 — reuse it, do not duplicate
its logic.)

`date.isAfter(LocalDate.now())` belongs here (application layer), not on a
request DTO, because it depends on server-side current time at call time —
it's not a static shape constraint decidable from the request alone (same
reasoning as the existing `latitude`/`longitude` null check living in this
service rather than the controller).

### Tests

`FarmWeatherServiceTest`: add cases for `getDailyWeather` — farm-not-found,
farm-missing-coordinates, future-date rejection (`WEATHER_DATE_IN_FUTURE`),
provider-returns-null (`WEATHER_DAILY_DATA_NOT_FOUND`), success mapping, and
the default-farm overload (delegates to `resolveDefaultFarm` then the
farmId-based method).

**Verification:** `./gradlew :application:test --tests "*Weather*"` green, then full `./gradlew test`.

Report file: `.superpowers/sdd/task-7-report.md`.

---

## Task 8: Past-date endpoints

Depends on Task 7 (`FarmWeatherService.getDailyWeather`).

1. `backend/api/src/main/kotlin/com/chamchamcham/api/farm/dto/WeatherResponses.kt`:
   add
   ```kotlin
   data class DailyWeatherResponse(
       val date: LocalDate,
       val weatherCondition: String,
       val minTemperature: Int,
       val maxTemperature: Int
   ) {
       companion object {
           fun from(summary: DailyWeatherSummary): DailyWeatherResponse =
               DailyWeatherResponse(
                   date = summary.date,
                   weatherCondition = summary.skyCondition,
                   minTemperature = summary.minTemperature,
                   maxTemperature = summary.maxTemperature
               )
       }
   }
   ```
   (Field name `weatherCondition`, matching `CurrentWeatherResponse`'s
   existing naming, not `skyCondition`.) **If Task 4 already added a
   forecast-list nested type to this same file with a similar shape, name
   this one so there is no class-name collision** — check the file's current
   contents before adding; if Task 4 used a generic name, prefer
   `DailyWeatherResponse` for this one since it's the more natural name for
   the past-date feature this whole plan centers on, and rename Task 4's type
   if it's the one that collides.
2. `backend/api/src/main/kotlin/com/chamchamcham/api/farm/controller/FarmWeatherController.kt`:
   add two endpoints:
   ```kotlin
   @GetMapping("/{farmId}/weather/daily")
   fun getDailyWeather(
       @AuthenticationPrincipal memberId: String?,
       @PathVariable farmId: UUID,
       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
   ): ResponseEntity<ApiResponse<WeatherResponses.DailyWeatherResponse>> {
       val summary = farmWeatherService.getDailyWeather(parseMemberId(memberId), farmId, date)
       return ResponseEntity.ok(ApiResponse.ok(WeatherResponses.DailyWeatherResponse.from(summary)))
   }

   @GetMapping("/weather/daily")
   fun getDailyWeatherForDefaultFarm(
       @AuthenticationPrincipal memberId: String?,
       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
   ): ResponseEntity<ApiResponse<WeatherResponses.DailyWeatherResponse>> {
       val summary = farmWeatherService.getDailyWeather(parseMemberId(memberId), date)
       return ResponseEntity.ok(ApiResponse.ok(WeatherResponses.DailyWeatherResponse.from(summary)))
   }
   ```

### Tests

`FarmWeatherControllerTest`: 200 success shape for both endpoints (explicit
farmId and default-farm), 400 for a malformed `date` query param, 400 for a
future date (service throws `WEATHER_DATE_IN_FUTURE`, verify it maps to 400
via the existing `GlobalExceptionHandler`/`ErrorCode.status`), 404 for
farm-not-found, 404 for `WEATHER_DAILY_DATA_NOT_FOUND`.

**Verification:** `./gradlew :api:test --tests "*Weather*"` green, then full `./gradlew test`. Also confirm `git diff --stat` for this task's commit touches no file under `farming`/`FarmingRecord`.

Report file: `.superpowers/sdd/task-8-report.md`.

---

## Task 9: UV index — spike, then implement or descope

This task has a mandatory research step before any code is written. Do not
write `UvIndexProvider`/`KmaUvIndexProvider` code based on assumed field
names — the request/response spec was not confirmed during design.

### Step 1 — Spike (do this first, report findings before writing code)

Check whether a real `KMA_SERVICE_KEY` is available in this environment (env
var, or a local secrets file referenced by `application-local.yml`). If one
is available:
- Make a real HTTP call to `https://apis.data.go.kr/1360000/LivingWthrIdxServiceV4/getUVIdxV4`
  with `serviceKey`, `areaNo` (try the first 10 characters of a real farm's
  `pnu` if you can find one in local test data, or `4113565700` as a
  known-format example), `time` (current hour, format `yyyyMMddHH`), `dataType=JSON`,
  `numOfRows`, `pageNo`. Inspect the raw response: confirm whether the request
  succeeds, what the actual response field names are, and whether `areaNo`
  derived from a PNU prefix produces a valid, non-empty result.

If no real service key is available in this environment, report back
`NEEDS_CONTEXT` with exactly what you need (a working `KMA_SERVICE_KEY` and
confirmation of the approved product) rather than guessing — **do not**
implement against invented field names.

### Step 2 — Implement (only after Step 1 confirms real request/response shape)

Once the real shape is confirmed, implement following the existing adapter
conventions (`KmaWeatherProvider`'s constructor/URI style):
- `backend/application/src/main/kotlin/com/chamchamcham/application/weather/UvIndexProvider.kt`:
  `fun interface UvIndexProvider { fun fetchUvIndex(areaNo: String): Int? }`
  (return `null` on any failure — never throw; this is a best-effort
  enrichment field, not a core piece of the response).
- `backend/api/src/main/kotlin/com/chamchamcham/api/weather/KmaUvIndexProvider.kt` +
  `WeatherUvProperties`/`WeatherUvConfig` (`weather.uv.*`), YAML additions in
  all four profiles.
- `FarmWeatherService`: `farm.pnu?.take(10)?.let { areaNo -> runCatching {
  uvIndexProvider.fetchUvIndex(areaNo) }.getOrNull() }` merged into
  `CurrentDetail.uvIndex`.
- `CurrentWeatherResponse`: add `uvIndex: Int?`.
- Tests for the adapter (success + failure-returns-null, using the exact
  field names confirmed in Step 1) and a `FarmWeatherServiceTest` case for
  `pnu == null` → `uvIndex == null` without calling the provider.

### Step 3 — Descope if the spike fails

If Step 1 shows the `areaNo`/PNU-prefix assumption doesn't hold, or the API
doesn't behave as expected, **stop here and report DONE_WITH_CONCERNS**
explaining exactly what was tried and what failed. Do not force an
implementation against a broken assumption. All other tasks in this plan are
already complete and independent of this one — descoping UV does not block
anything else.

**Verification (if implemented):** `./gradlew :api:test :application:test --tests "*Weather*" --tests "*Uv*"` green, then full `./gradlew test`.

Report file: `.superpowers/sdd/task-9-report.md`.

---

## Final Verification (after all tasks)

```bash
cd backend
./gradlew test
git diff --stat dev...HEAD   # confirm no farming/FarmingRecord files appear
```

Manual smoke test (local profile, `:api:bootRun`):
- `GET /api/v1/farms/{farmId}/weather` and `GET /api/v1/farms/weather` — address/
  humidity/wind/precipitation-probability/forecast (and UV if implemented).
- `GET /api/v1/farms/{farmId}/weather/daily?date=<past date>` and
  `GET /api/v1/farms/weather/daily?date=<past date>` — weather condition +
  min/max temperature.
- `GET /api/v1/farms/{farmId}/weather/daily?date=<future date>` — 400.
- Call the 5-day-panel endpoint at two different times of day and confirm both
  the 4-day and 5-day cases render without error.
