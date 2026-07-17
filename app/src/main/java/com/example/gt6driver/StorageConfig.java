package com.example.gt6driver;

import android.content.Context;

import com.example.gt6driver.net.ApiClient;
import com.example.gt6driver.sync.GT6MediaSync;

public final class StorageConfig {
    private static final String PROD_ACCOUNT = "stgt6driverappprod";
    private static final String UAT_ACCOUNT = "stgt6driverappdev";

    private StorageConfig() {}

    public static String accountName() {
        return ApiClient.ENV_UAT.equals(ApiClient.getEnvironment()) ? UAT_ACCOUNT : PROD_ACCOUNT;
    }

    public static String blobEndpoint() {
        return "https://" + accountName() + ".blob.core.windows.net";
    }

    public static String driverContainerUrl() {
        return blobEndpoint() + "/driver";
    }

    public static String driverBaseUrl() {
        return driverContainerUrl() + "/";
    }

    public static String compressedFilesBaseUrl() {
        return blobEndpoint() + "/compressed-files/";
    }

    public static void configureMediaSync(Context context) {
        ApiClient.configure(context);
        GT6MediaSync.setContainerUrl(context, driverContainerUrl());
    }
}
