package com.chamchamcham.application.coaching.rag.record

interface RecordFeedbackWeatherPort {
    fun fetch(latitude: Double, longitude: Double, limitDays: Int = 7): RecordFeedbackLiveWeather
}
