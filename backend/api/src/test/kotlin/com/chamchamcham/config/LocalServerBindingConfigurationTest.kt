package com.chamchamcham.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.env.PropertySourcesPropertyResolver
import org.springframework.core.io.ClassPathResource

class LocalServerBindingConfigurationTest {
    private val localProfilePropertySources =
        YamlPropertySourceLoader().load(
            "application-local.yml",
            ClassPathResource("application-local.yml"),
        )

    @Test
    fun `local 서버는 기본적으로 이 컴퓨터에서만 접근할 수 있다`() {
        val resolver = resolver()

        assertEquals("127.0.0.1", resolver.getProperty("server.address"))
    }

    @Test
    fun `환경 변수로 외부 접근 주소를 명시할 수 있다`() {
        val resolver =
            resolver(
                MapPropertySource(
                    "serverAddressOverride",
                    mapOf("SERVER_ADDRESS" to "0.0.0.0"),
                ),
            )

        assertEquals("0.0.0.0", resolver.getProperty("server.address"))
    }

    private fun resolver(override: MapPropertySource? = null): PropertySourcesPropertyResolver {
        val propertySources = MutablePropertySources()
        override?.let(propertySources::addFirst)
        localProfilePropertySources.forEach(propertySources::addLast)
        return PropertySourcesPropertyResolver(propertySources)
    }
}
