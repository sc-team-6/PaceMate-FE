package com.gdg.scrollmanager.api

import retrofit2.Call
import retrofit2.http.GET

interface ApiService {
    @GET("api/rank")
    fun checkHealth(): Call<HealthResponse>
}

data class HealthResponse(
    val message: String
)