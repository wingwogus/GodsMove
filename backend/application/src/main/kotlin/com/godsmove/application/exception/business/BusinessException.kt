package com.godsmove.application.exception.business

import com.godsmove.application.exception.ApplicationException
import com.godsmove.application.exception.ErrorCode

open class BusinessException(
    val errorCode: ErrorCode,
    val detail: Any? = null,
    val customMessage: String? = null,
    message: String = customMessage ?: errorCode.messageKey
) : ApplicationException(message)
