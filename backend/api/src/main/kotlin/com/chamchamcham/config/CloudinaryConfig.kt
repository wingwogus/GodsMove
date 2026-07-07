package com.chamchamcham.config

import com.cloudinary.Cloudinary
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(CloudinaryProperties::class)
class CloudinaryConfig {
    @Bean
    fun cloudinary(properties: CloudinaryProperties): Cloudinary {
        return Cloudinary(
            mapOf(
                "cloud_name" to properties.cloudName,
                "api_key" to properties.apiKey,
                "api_secret" to properties.apiSecret,
                "secure" to properties.secure
            )
        )
    }
}
