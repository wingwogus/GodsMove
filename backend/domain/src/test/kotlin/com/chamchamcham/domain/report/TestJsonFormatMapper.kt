package com.chamchamcham.domain.report

import org.hibernate.type.descriptor.WrapperOptions
import org.hibernate.type.descriptor.java.JavaType
import org.hibernate.type.format.FormatMapper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Base64

class TestJsonFormatMapper : FormatMapper {
    override fun <T : Any?> fromString(
        charSequence: CharSequence,
        javaType: JavaType<T>,
        wrapperOptions: WrapperOptions,
    ): T {
        val encoded = charSequence.toString().trim().removeSurrounding("\"")
        val bytes = Base64.getDecoder().decode(encoded)
        val value = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
        return javaType.javaTypeClass.cast(value)
    }

    override fun <T : Any?> toString(
        value: T,
        javaType: JavaType<T>,
        wrapperOptions: WrapperOptions,
    ): String {
        val bytes = ByteArrayOutputStream().use { output ->
            ObjectOutputStream(output).use { it.writeObject(value) }
            output.toByteArray()
        }
        return "\"${Base64.getEncoder().encodeToString(bytes)}\""
    }
}
