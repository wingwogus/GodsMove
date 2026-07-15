package com.chamchamcham.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.env.PropertySourcesPropertyResolver
import org.springframework.core.io.ClassPathResource

class ProductionRagConfigurationTest {
    private val productionPropertySources =
        YamlPropertySourceLoader().load(
            "application-prod.yml",
            ClassPathResource("application-prod.yml"),
        )

    @Test
    fun `production vector table defaults to Spring AI vector store`() {
        assertEquals("vector_store", resolver().getProperty("spring.ai.vectorstore.pgvector.table-name"))
    }

    @Test
    fun `production vector table can be overridden by environment`() {
        val resolver = resolver(MapPropertySource("ragOverride", mapOf("RAG_VECTOR_TABLE" to "vector_store_test")))

        assertEquals("vector_store_test", resolver.getProperty("spring.ai.vectorstore.pgvector.table-name"))
    }

    @Test
    fun `production validates the manually managed vector schema`() {
        assertEquals(
            true,
            resolver().getProperty("spring.ai.vectorstore.pgvector.schema-validation", Boolean::class.java),
        )
    }

    private fun resolver(override: MapPropertySource? = null): PropertySourcesPropertyResolver {
        val propertySources = MutablePropertySources()
        override?.let(propertySources::addFirst)
        productionPropertySources.forEach(propertySources::addLast)
        return PropertySourcesPropertyResolver(propertySources)
    }
}
