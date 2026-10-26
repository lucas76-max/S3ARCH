package com.s3arch.app.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Client HTTP partage par les trackers et la Freebox. */
object Network {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val request: Request = chain.request().newBuilder()
                .header("User-Agent", "S3ARCH/1.0 (Android)")
                .build()
            chain.proceed(request)
        }
        .build()
}
