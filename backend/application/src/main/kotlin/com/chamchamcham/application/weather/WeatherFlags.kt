package com.chamchamcham.application.weather

data class WeatherFlags(
    val raining: Boolean,
    val rainLikely: Boolean,
    val heatWave: Boolean,
    val frost: Boolean,
    val mild: Boolean,
    val humid: Boolean,
    val dry: Boolean,
    val windy: Boolean,
    val highUv: Boolean,
    val clearSky: Boolean
) {
    companion object {
        private val PRECIPITATION = setOf(
            WeatherCondition.RAIN, WeatherCondition.RAIN_SNOW, WeatherCondition.SNOW,
            WeatherCondition.SHOWER, WeatherCondition.DRIZZLE,
            WeatherCondition.DRIZZLE_SNOW, WeatherCondition.SNOW_FLURRY
        )
        private val CLEAR = setOf(WeatherCondition.CLEAR, WeatherCondition.PARTLY_CLOUDY)

        fun from(w: DetailWeather): WeatherFlags = WeatherFlags(
            raining = w.condition in PRECIPITATION,
            rainLikely = (w.precipitationProbability ?: 0) >= 60,
            heatWave = (w.maxTemperature ?: w.temperature) >= 33,
            frost = (w.minTemperature ?: w.temperature) <= 4,
            mild = w.temperature in 15..25,
            humid = (w.humidity ?: -1) >= 80,
            dry = w.humidity?.let { it <= 40 } ?: false,
            windy = (w.windSpeed ?: 0.0) >= 7.0,
            highUv = (w.uvIndex ?: 0) >= 8,
            clearSky = w.condition in CLEAR
        )
    }
}
