package com.example.aichat.data.model

import com.google.gson.annotations.SerializedName

/**
 * Error response model for OpenAI API
 */
data class ApiError(
    val error: ErrorDetail
)

data class ErrorDetail(
    val message: String,
    val type: String?,
    val param: String?,
    val code: String?
)


