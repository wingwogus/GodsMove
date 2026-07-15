package com.chamchamcham.api.weather

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(WeatherUvProperties::class)
class WeatherUvConfig
