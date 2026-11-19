package com.example.update

import com.example.R
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

internal object HttpHops {

    val ALLOWED_HOSTS: Set<String> = setOf(
        "api.github.com",
        "github.com",
        "release-assets.githubusercontent.com",
        "objects.githubusercontent.com"
    )

    const val MAX_HOPS = 3
    private const val HTTPS_PORT = 443

    fun validate(url: HttpUrl) {
        if (url.scheme != "https") {
            throw UpdateFailure(R.string.update_error_host, kind = UpdateFailure.Kind.BAD_HOST)
        }
        if (url.host !in ALLOWED_HOSTS) {
            throw UpdateFailure(R.string.update_error_host, kind = UpdateFailure.Kind.BAD_HOST)
        }
        if (url.port != HTTPS_PORT) {
            throw UpdateFailure(R.string.update_error_host, kind = UpdateFailure.Kind.BAD_HOST)
        }
    }

    fun execute(client: OkHttpClient, request: Request): Response {
        var current = request
        var hops = 0
        while (true) {
            validate(current.url)
            val response = client.newCall(current).execute()
            if (!response.isRedirect) return response

            val location = response.header("Location")
            response.close()
            if (location.isNullOrBlank()) {
                throw UpdateFailure(R.string.update_error_host, kind = UpdateFailure.Kind.BAD_HOST)
            }
            hops++
            if (hops > MAX_HOPS) {
                throw UpdateFailure(R.string.update_error_host, kind = UpdateFailure.Kind.BAD_HOST)
            }
            val next = current.url.resolve(location)
                ?: throw UpdateFailure(R.string.update_error_host, kind = UpdateFailure.Kind.BAD_HOST)
            current = current.newBuilder().url(next).build()
        }
    }

    fun apiClient(): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .callTimeout(8, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    fun downloadClient(): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}
