package com.chamchamcham.application.coaching.recordfeedback.generation

interface RecordFeedbackWeatherPort {
    fun fetch(latitude: Double, longitude: Double, limitDays: Int = 7): RecordFeedbackLiveWeather
}
