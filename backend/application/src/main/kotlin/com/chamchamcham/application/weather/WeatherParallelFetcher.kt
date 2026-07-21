package com.chamchamcham.application.weather

import mu.KotlinLogging
import org.springframework.beans.factory.DisposableBean
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

private val logger = KotlinLogging.logger {}

/**
 * 홈 화면에 필요한 소스. 자외선·중기예보는 부르지 않는다(홈 화면에 없는 값이라 3콜로 끝낸다).
 */
data class HomeSources(
    val current: CurrentObservation,
    val latest: ShortTermForecast?,
    val todayRange: DailyForecast?,
    val partial: PartialFailure
)

/**
 * 상세 화면에 필요한 소스.
 *
 * [midTermD4]는 중기예보 원본 결과를 그대로 노출한다 — D1~D3처럼 [latest]가 D4를 이미 주는
 * 경우가 있어(계획 §1), "latest에 D4가 있으면 그걸 쓰고 없으면 이걸 쓴다"는 선택은
 * 호출자(FarmWeatherService)가 한다. 여기서 미리 하나로 합쳐버리면 서비스가 그 선택을
 * 다시 되돌릴 방법이 없다.
 */
data class DetailSources(
    val current: CurrentObservation,
    val latest: ShortTermForecast?,
    val todayRange: DailyForecast?,
    val midTermD4: DailyForecast?,
    val uvIndex: Int?,
    val partial: PartialFailure
)

/**
 * 포트 5개를 병렬로 호출해 조립한다.
 *
 * 전용 [executor]로 [Executors.newVirtualThreadPerTaskExecutor]를 쓴다. `supplyAsync`에 executor를
 * 생략하면 `ForkJoinPool.commonPool()`이 쓰이는데, 병렬도가 코어수-1(2코어 컨테이너면 1)이라
 * 블로킹 HTTP 호출들이 직렬화되고 앱의 다른 commonPool 사용처까지 굶는다.
 * `spring.threads.virtual.enabled`는 Tomcat 요청 스레드와 `@Async`에만 적용되고
 * `supplyAsync`에는 적용되지 않으므로 여기서 직접 지정해야 한다.
 */
@Component
class WeatherParallelFetcher(
    private val currentObservationPort: CurrentObservationPort,
    private val shortTermForecastPort: ShortTermForecastPort,
    private val midTermForecastPort: MidTermForecastPort,
    private val uvIndexPort: UvIndexPort,
    private val clock: Clock
) : DisposableBean {

    private val executor = Executors.newVirtualThreadPerTaskExecutor()

    fun fetchDetail(location: WeatherLocation): DetailSources {
        val currentFuture = supplyCurrent(location)
        val latestFuture = supplyLatest(location)
        val todayRangeFuture = supplyTodayRange(location)
        // D4 날짜는 호출자가 아니라 여기서 Clock으로 계산한다 — 인자 없는 LocalDate.now() 금지.
        val today = LocalDate.now(clock)
        val d4Date = today.plusDays(4)
        val midFuture = supplyMidTerm(location, d4Date)
        val uvFuture = supplyUvIndex(location)

        val currentRaw = currentFuture.join()
        val latestRaw = latestFuture.join()
        val todayRangeRaw = todayRangeFuture.join()
        val midTermD4Raw = midFuture.join()
        val uvIndexRaw = uvFuture.join()

        // 날짜 존재가 아니라 온도까지 있어야 D4를 확보한 것이다 — 단기예보는 D4를 온도 없는
        // 반쪽짜리로 줄 때가 있고, 그때 서비스는 중기예보를 쓴다(DailyForecast.hasTemperatureRange).
        val d4FromLatest = latestRaw?.dailyForecasts?.any { it.date == d4Date && it.hasTemperatureRange } ?: false
        // missing 판정은 폴백 치환 전 원본 nullness로 한다 — 폴백으로 채워도 degraded는 정직하게 유지.
        val partial = PartialFailure.of(
            "current" to (currentRaw == null),
            "todayMinMax" to (todayRangeRaw == null),
            "forecast" to (latestRaw == null),
            "forecast.D4" to (!d4FromLatest && midTermD4Raw == null),
            "uvIndex" to (uvIndexRaw == null)
        )

        val providerDown = currentRaw == null
        return DetailSources(
            current = currentRaw ?: WeatherFallback.currentObservation(clock),
            latest = latestRaw ?: fallbackIfProviderDown(providerDown) { WeatherFallback.shortTermForecast(today) },
            todayRange = todayRangeRaw ?: fallbackIfProviderDown(providerDown) { WeatherFallback.dailyForecast(today) },
            midTermD4 = midTermD4Raw ?: fallbackIfProviderDown(providerDown) { WeatherFallback.dailyForecast(d4Date) },
            uvIndex = uvIndexRaw ?: fallbackIfProviderDown(providerDown) { WeatherFallback.UV_INDEX },
            partial = partial
        )
    }

    fun fetchHome(location: WeatherLocation): HomeSources {
        val currentFuture = supplyCurrent(location)
        val latestFuture = supplyLatest(location)
        val todayRangeFuture = supplyTodayRange(location)

        val currentRaw = currentFuture.join()
        val latestRaw = latestFuture.join()
        val todayRangeRaw = todayRangeFuture.join()

        val partial = PartialFailure.of(
            "current" to (currentRaw == null),
            "todayMinMax" to (todayRangeRaw == null),
            "forecast" to (latestRaw == null)
        )

        val providerDown = currentRaw == null
        val today = LocalDate.now(clock)
        return HomeSources(
            current = currentRaw ?: WeatherFallback.currentObservation(clock),
            latest = latestRaw ?: fallbackIfProviderDown(providerDown) { WeatherFallback.shortTermForecast(today) },
            todayRange = todayRangeRaw ?: fallbackIfProviderDown(providerDown) { WeatherFallback.dailyForecast(today) },
            partial = partial
        )
    }

    // 필수 소스(current) 실패 = 외부 provider 전체 장애(해외 VPN·기상청 장애)로 보고 나머지도 폴백으로
    // 채운다. current가 살아있는 부분 결측은 예전처럼 null로 둔다 — 없는 값을 지어내지 않기 위해서다.
    private fun <T> fallbackIfProviderDown(providerDown: Boolean, fallback: () -> T): T? =
        if (providerDown) fallback() else null

    // 실황도 이제 예외를 던지지 않는다 — 실패하면 null로 흡수하고, 호출부가 provider 장애로 판단해
    // 폴백으로 채운다(과거엔 여기서 503이 요청 전체를 실패시켰다).
    private fun supplyCurrent(location: WeatherLocation): CompletableFuture<CurrentObservation?> =
        CompletableFuture.supplyAsync(
            { fetchOptional("current") { currentObservationPort.fetch(location) } },
            executor
        )

    private fun supplyLatest(location: WeatherLocation): CompletableFuture<ShortTermForecast?> =
        CompletableFuture.supplyAsync(
            { fetchOptional("shortTermForecast.latest") { shortTermForecastPort.fetchLatest(location) } },
            executor
        )

    private fun supplyTodayRange(location: WeatherLocation): CompletableFuture<DailyForecast?> =
        CompletableFuture.supplyAsync(
            { fetchOptional("shortTermForecast.todayRange") { shortTermForecastPort.fetchTodayRange(location) } },
            executor
        )

    private fun supplyMidTerm(location: WeatherLocation, date: LocalDate): CompletableFuture<DailyForecast?> =
        CompletableFuture.supplyAsync(
            { fetchOptional("midTermForecast") { midTermForecastPort.fetch(location, date) } },
            executor
        )

    private fun supplyUvIndex(location: WeatherLocation): CompletableFuture<Int?> =
        CompletableFuture.supplyAsync({ fetchOptional("uvIndex") { uvIndexPort.fetch(location) } }, executor)

    // 선택 소스는 예외를 던지면 안 된다 — 판정(missing 여부)은 PartialFailure 한 곳에서만 한다.
    // 예외 메시지에 serviceKey 등 인증 정보가 섞일 수 있어 메시지는 로그에 남기지 않는다.
    private fun <T> fetchOptional(sourceName: String, block: () -> T?): T? =
        try {
            block()
        } catch (ex: Exception) {
            logger.warn { "weather optional source failed: $sourceName (${ex.javaClass.simpleName})" }
            null
        }

    override fun destroy() {
        executor.shutdown()
    }
}
