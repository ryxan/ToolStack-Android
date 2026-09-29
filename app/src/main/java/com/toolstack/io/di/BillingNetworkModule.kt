package com.toolstack.io.di

import com.toolstack.io.data.api.BillingVerificationApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for the billing-specific OkHttpClient and Retrofit instance.
 * Kept separate from any future general-purpose network module so billing
 * traffic has its own timeout budget and logging config.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BillingRetrofit

@Module
@InstallIn(SingletonComponent::class)
object BillingNetworkModule {

    /**
     * Purchase-verification backend.
     *
     * Currently shared with RiverWatch (same developer account, same service account).
     * When ToolStack ships, swap this for a dedicated domain and backend instance.
     * The Android client already sends packageName + productId in the request body,
     * so the backend can distinguish apps without any other changes here.
     */
    private const val BILLING_VERIFICATION_URL = "https://api.riverwatch.cc/"

    @Provides
    @Singleton
    @BillingRetrofit
    fun provideBillingOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (com.toolstack.io.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @BillingRetrofit
    fun provideBillingRetrofit(@BillingRetrofit okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BILLING_VERIFICATION_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideBillingVerificationApi(@BillingRetrofit retrofit: Retrofit): BillingVerificationApi {
        return retrofit.create(BillingVerificationApi::class.java)
    }
}
