package com.example.gt6driver.net;

import android.content.Context;

import java.util.concurrent.TimeUnit;

import androidx.preference.PreferenceManager;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    public static final String PREF_API_ENVIRONMENT = "api_environment";
    public static final String ENV_PRODUCTION = "production";
    public static final String ENV_UAT = "uat";

    private static final String PRODUCTION_BASE_URL = "https://member.api.barrett-jackson.com/";
    private static final String UAT_BASE_URL = "https://member.api-uat.barrett-jackson.com/";

    private static Retrofit retrofit;
    private static String currentEnvironment = ENV_PRODUCTION;

    /** Retrofit client for Barrett-Jackson Member API */
    public static synchronized Retrofit getMemberApi() {
        if (retrofit == null) {
            retrofit = buildRetrofit(getBaseUrlForEnvironment(currentEnvironment));
        }
        return retrofit;
    }

    /** Load the saved environment and rebuild Retrofit if it changed. */
    public static synchronized void configure(Context context) {
        setEnvironment(getSavedEnvironment(context));
    }

    public static synchronized void saveEnvironment(Context context, String environment) {
        String normalized = normalizeEnvironment(environment);
        PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext())
                .edit()
                .putString(PREF_API_ENVIRONMENT, normalized)
                .apply();
        setEnvironment(normalized);
    }

    public static synchronized void setEnvironment(String environment) {
        String normalized = normalizeEnvironment(environment);
        if (!normalized.equals(currentEnvironment)) {
            currentEnvironment = normalized;
            retrofit = null;
        }
    }

    public static synchronized String getEnvironment() {
        return currentEnvironment;
    }

    public static String getSavedEnvironment(Context context) {
        return normalizeEnvironment(
                PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext())
                        .getString(PREF_API_ENVIRONMENT, ENV_PRODUCTION)
        );
    }

    public static synchronized String getCurrentBaseUrl() {
        return getBaseUrlForEnvironment(currentEnvironment);
    }

    public static String getBaseUrlForEnvironment(String environment) {
        return ENV_UAT.equals(normalizeEnvironment(environment))
                ? UAT_BASE_URL
                : PRODUCTION_BASE_URL;
    }

    public static String getEnvironmentLabel(String environment) {
        return ENV_UAT.equals(normalizeEnvironment(environment)) ? "UAT" : "PRODUCTION";
    }

    private static String normalizeEnvironment(String environment) {
        return ENV_UAT.equalsIgnoreCase(environment) ? ENV_UAT : ENV_PRODUCTION;
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



