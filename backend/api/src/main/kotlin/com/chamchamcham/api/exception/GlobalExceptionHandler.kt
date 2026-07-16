package com.chamchamcham.api.exception

import com.chamchamcham.api.common.ApiResponse
import com.chamchamcham.application.common.LoggingUtil
import com.chamchamcham.application.exception.ErrorCode
import com.chamchamcham.application.exception.business.BusinessException
import jakarta.validation.ConstraintViolationException
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BindException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.util.*

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = KotlinLogging.logger {}

    /**
     * 1) 비즈니스 예외 (개발자가 명시적으로 throw한 것)
     *    클라이언트에게 알려줘야 하는 "정상적인 실패"이므로 WARN 로그만 남김
     */
    @ExceptionHandler
    fun handleBusiness(e: BusinessException): ResponseEntity<ApiResponse<Unit>> {

        // 비즈니스 예외는 WARN으로만 남긴다

        val body = ApiResponse.fail(
            code = e.errorCode.code,
            messageKey = e.customMessage ?: e.errorCode.messageKey,
            detail = e.detail
        )
        return ResponseEntity.status(e.errorCode.status).body(body)
    }

    /**
     * 2) Validation 예외 (@Valid 검증 실패)
     * MethodArgumentNotValidException → @RequestBody DTO 검증 실패
     * BindException → @ModelAttribute 검증 실패 (ex: 쿼리파라미터)
     */
    @ExceptionHandler
    fun handleMethodArgumentNotValid(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Unit>> {

        LoggingUtil.logBusinessError(logger, e)

        val fieldError = e.bindingResult.fieldErrors.firstOrNull()
        val detail = fieldError?.let {
            mapOf(
                "field" to it.field,
                "reason" to (it.defaultMessage ?: "Invalid value")
            )
        }

        val body = ApiResponse.fail(
            code = ErrorCode.INVALID_INPUT.code,
            messageKey = ErrorCode.INVALID_INPUT.messageKey,
            detail = detail
        )

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    /**
     * 3) @ModelAttribute 검증 실패
     */
    @ExceptionHandler
    fun handleBindException(e: BindException): ResponseEntity<ApiResponse<Unit>> {

        LoggingUtil.logBusinessError(logger, e)

        val fieldError = e.bindingResult.fieldErrors.firstOrNull()
        val detail = fieldError?.let {
            mapOf(
                "field" to it.field,
                "reason" to (it.defaultMessage ?: "Invalid value")
            )
        }

        val body = ApiResponse.fail(
            code = ErrorCode.INVALID_INPUT.code,
            messageKey = ErrorCode.INVALID_INPUT.messageKey,
            detail = detail
        )

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    @ExceptionHandler
    fun handleConstraintViolation(e: ConstraintViolationException): ResponseEntity<ApiResponse<Unit>> {
        LoggingUtil.logBusinessError(logger, e)

        val violation = e.constraintViolations.firstOrNull()
        val body = ApiResponse.fail(
            code = ErrorCode.INVALID_INPUT.code,
            messageKey = ErrorCode.INVALID_INPUT.messageKey,
            detail = violation?.let {
                mapOf(
                    "field" to it.propertyPath.toString().substringAfterLast('.'),
                    "reason" to it.message,
                )
            },
        )

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    /**
     * 4) 쿼리 파라미터 / 경로 변수 타입 변환 실패
     * ex: Enum에 없는 값, UUID 형식 오류, 숫자 타입 오류
     */
    @ExceptionHandler
    fun handleMethodArgumentTypeMismatch(e: MethodArgumentTypeMismatchException):
            ResponseEntity<ApiResponse<Unit>> {

        LoggingUtil.logBusinessError(logger, e)

        val body = ApiResponse.fail(
            code = ErrorCode.INVALID_INPUT.code,
            messageKey = ErrorCode.INVALID_INPUT.messageKey,
            detail = mapOf(
                "field" to e.name,
                "reason" to (e.value?.let { "Invalid value: $it" } ?: "Invalid value")
            )
        )

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    /**
     * 4-1) 필수 쿼리 파라미터 누락
     * ex: @RequestParam(필수)이 요청에 없음
     */
    @ExceptionHandler
    fun handleMissingServletRequestParameter(e: MissingServletRequestParameterException):
            ResponseEntity<ApiResponse<Unit>> {

        LoggingUtil.logBusinessError(logger, e)

        val body = ApiResponse.fail(
            code = ErrorCode.INVALID_INPUT.code,
            messageKey = ErrorCode.INVALID_INPUT.messageKey,
            detail = mapOf(
                "field" to e.parameterName,
                "reason" to "Required parameter is missing"
            )
        )

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }


    /**
     * 5) JSON 파싱 예외
     * JSON 문법 오류 / Enum 값 에러 / 숫자 타입 오류 등
     */
    @ExceptionHandler
    fun handleJsonParse(e: HttpMessageNotReadableException):
            ResponseEntity<ApiResponse<Unit>> {

        val eventId = LoggingUtil.logBusinessError(logger, e)

        val body = ApiResponse.fail(
            code = ErrorCode.INVALID_JSON.code,
            messageKey = ErrorCode.INVALID_JSON.messageKey,
            detail = mapOf(
                "reason" to "Invalid JSON format",
                "eventId" to eventId
            )
        )

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    /**
     * 6) 404 (Spring MVC에서 발생)
     */
    @ExceptionHandler
    fun handleNotFound(e: NoResourceFoundException): ResponseEntity<ApiResponse<Unit>> {
        // 정적 리소스 요청이므로 에러 로그 남기지 않음
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(
                ApiResponse.fail(
                    code = ErrorCode.RESOURCE_NOT_FOUND.code,
                    messageKey = ErrorCode.RESOURCE_NOT_FOUND.messageKey,
                    detail = e.message
                )
            )
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiResponse<Unit>> {

        val eventId = LoggingUtil.logUnexpectedError(logger, e)


        val body = ApiResponse.fail(
            code = ErrorCode.INTERNAL_ERROR.code,
            messageKey = ErrorCode.INTERNAL_ERROR.messageKey,
            detail = mapOf("eventId" to eventId)
        )

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(body)
    }


}
