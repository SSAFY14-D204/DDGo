package com.ddgo.app.feature.onboarding

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.util.Consumer

internal fun hasLocationPermission(context: Context): Boolean {
    val fineGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val coarseGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    return fineGranted || coarseGranted
}

@SuppressLint("MissingPermission")
internal fun loadCurrentLocationIncrementally(
    context: Context,
    onCachedLocation: (Double, Double) -> Unit,
    onFreshLocation: (Double, Double, Boolean) -> Unit,
    onError: (String) -> Unit
) {
    if (!hasLocationPermission(context)) {
        onError("위치 권한이 필요합니다.")
        return
    }

    val locationManager = context.getSystemService(LocationManager::class.java)
        ?: run {
            onError("위치 서비스를 사용할 수 없습니다.")
            return
        }

    val availableProviders = listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    ).filter(locationManager::isProviderEnabled)

    if (availableProviders.isEmpty()) {
        onError("위치 서비스를 켜주세요.")
        return
    }

    try {
        val cachedLocation = findBestLastKnownLocation(locationManager, availableProviders)
        cachedLocation?.let { location ->
            onCachedLocation(location.latitude, location.longitude)
        }

        requestCurrentLocation(
            context = context,
            locationManager = locationManager,
            providers = availableProviders,
            providerIndex = 0,
            onSuccess = { latitude, longitude ->
                val isSameAsCached = cachedLocation?.let {
                    areLocationsEffectivelySame(
                        cachedLatitude = it.latitude,
                        cachedLongitude = it.longitude,
                        freshLatitude = latitude,
                        freshLongitude = longitude
                    )
                } ?: false

                onFreshLocation(latitude, longitude, isSameAsCached)
            },
            onError = onError
        )
    } catch (_: SecurityException) {
        onError("위치 권한이 필요합니다.")
    }
}

@SuppressLint("MissingPermission")
private fun requestCurrentLocation(
    context: Context,
    locationManager: LocationManager,
    providers: List<String>,
    providerIndex: Int,
    onSuccess: (Double, Double) -> Unit,
    onError: (String) -> Unit
) {
    if (!hasLocationPermission(context)) {
        onError("위치 권한이 필요합니다.")
        return
    }

    if (providerIndex >= providers.size) {
        onError("현재 위치를 가져오지 못했습니다.")
        return
    }

    val provider = providers[providerIndex]
    val cancellationSignal = CancellationSignal()
    val timeoutHandler = Handler(Looper.getMainLooper())
    var completed = false

    fun completeWithFallback() {
        if (completed) return
        completed = true
        cancellationSignal.cancel()
        timeoutHandler.removeCallbacksAndMessages(null)
        requestCurrentLocation(
            context = context,
            locationManager = locationManager,
            providers = providers,
            providerIndex = providerIndex + 1,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    timeoutHandler.postDelayed({ completeWithFallback() }, 6_000L)

    val consumer = Consumer<Location> { location ->
        if (completed) return@Consumer

        completed = true
        timeoutHandler.removeCallbacksAndMessages(null)
        onSuccess(location.latitude, location.longitude)
    }

    LocationManagerCompat.getCurrentLocation(
        locationManager,
        provider,
        cancellationSignal,
        ContextCompat.getMainExecutor(context),
        consumer
    )
}

private fun findBestLastKnownLocation(
    locationManager: LocationManager,
    providers: List<String>
): Location? {
    return providers
        .mapNotNull(locationManager::getLastKnownLocation)
        .maxByOrNull { it.time }
}

private fun areLocationsEffectivelySame(
    cachedLatitude: Double,
    cachedLongitude: Double,
    freshLatitude: Double,
    freshLongitude: Double
): Boolean {
    return kotlin.math.abs(cachedLatitude - freshLatitude) < 0.0001 &&
        kotlin.math.abs(cachedLongitude - freshLongitude) < 0.0001
}
