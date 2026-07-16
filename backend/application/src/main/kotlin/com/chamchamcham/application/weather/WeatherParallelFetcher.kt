package com.chamchamcham.application.weather

import com.chamchamcham.application.exception.business.BusinessException
import mu.KotlinLogging
import org.springframework.beans.factory.DisposableBean
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
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
        val d4Date = LocalDate.now(clock).plusDays(4)
        val midFuture = supplyMidTerm(location, d4Date)
        val uvFuture = supplyUvIndex(location)

        val current = joinRequired(currentFuture)
        val latest = latestFuture.join()
        val todayRange = todayRangeFuture.join()
        val midTermD4 = midFuture.join()
        val uvIndex = uvFuture.join()

        // 날짜 존재가 아니라 온도까지 있어야 D4를 확보한 것이다 — 단기예보는 D4를 온도 없는
        // 반쪽짜리로 줄 때가 있고, 그때 서비스는 중기예보를 쓴다(DailyForecast.hasTemperatureRange).
        val d4FromLatest = latest?.dailyForecasts?.any { it.date == d4Date && it.hasTemperatureRange } ?: false
        val partial = PartialFailure.of(
            "todayMinMax" to (todayRange == null),
            "forecast" to (latest == null),
            "forecast.D4" to (!d4FromLatest && midTermD4 == null),
            "uvIndex" to (uvIndex == null)
        )

        return DetailSources(
            current = current,
            latest = latest,
            todayRange = todayRange,
            midTermD4 = midTermD4,
            uvIndex = uvIndex,
            partial = partial
        )
    }

    fun fetchHome(location: WeatherLocation): HomeSources {
        val currentFuture = supplyCurrent(location)
        val latestFuture = supplyLatest(location)
        val todayRangeFuture = supplyTodayRange(location)

        val current = joinRequired(currentFuture)
        val latest = latestFuture.join()
        val todayRange = todayRangeFuture.join()

        val partial = PartialFailure.of(
            "todayMinMax" to (todayRange == null),
            "forecast" to (latest == null)
        )

        return HomeSources(current = current, latest = latest, todayRange = todayRange, partial = partial)
    }

    private fun supplyCurrent(location: WeatherLocation): CompletableFuture<CurrentObservation> =
        CompletableFuture.supplyAsync({ currentObservationPort.fetch(location) }, executor)

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

    // join()은 태스크 예외를 CompletionException으로 감싼다. 그대로 두면 GlobalExceptionHandler가
    // BusinessException 매칭에 실패해 503이어야 할 게 500으로 샌다. cause가 BusinessException이면 언랩한다.
    private fun joinRequired(future: CompletableFuture<CurrentObservation>): CurrentObservation =
        try {
            future.join()
        } catch (ex: CompletionException) {
            val cause = ex.cause
            if (cause is BusinessException) throw cause else throw ex
        }

    override fun destroy() {
        executor.shutdown()
    }
}
