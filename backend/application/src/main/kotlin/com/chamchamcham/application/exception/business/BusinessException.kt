package com.chamchamcham.application.exception.business

import com.chamchamcham.application.exception.ApplicationException
import com.chamchamcham.application.exception.ErrorCode

open class BusinessException(
    val errorCode: ErrorCode,
    val detail: Any? = null,
    val customMessage: String? = null,
    message: String = customMessage ?: errorCode.messageKey
) : ApplicationException(message)
