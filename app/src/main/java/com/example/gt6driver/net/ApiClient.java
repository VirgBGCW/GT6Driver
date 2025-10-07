package com.example.gt6driver.net;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    // Single API base for all calls
    private static final String BASE_URL = "https://member.api.barrett-jackson.com/";

    private static Retrofit retrofit;

    /** Retrofit client for Barrett-Jackson Member API */
    public static Retrofit getMemberApi() {
        if (retrofit == null) {
            retrofit = buildRetrofit(BASE_URL);
        }
        return retrofit;
    }

    // -------------------- internals --------------------

    private static Retrofit buildRetrofit(String baseUrl) {
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(buildClient())
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    private static OkHttpClient buildClient() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(chain ->
                        chain.proceed(
                                chain.request().newBuilder()
                                        .addHeader("accept", "application/json")
                                        .addHeader("Content-Type", "application/json")
                                        .build()
                        ))
                .addInterceptor(logging)
                .build();
    }
}



