package com.chamchamcham.application.exception

abstract class ApplicationException(
    override val message: String
) : RuntimeException(message)
