package ai.openclaw.jarvis.capabilities.impl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ai.openclaw.jarvis.capabilities.base.*
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val label: String,
)

interface LocationCapability : Capability {
    suspend fun getLastKnownLocation(): CapabilityResult<LocationResult>
    fun getLocationLabel(): String
}

@Singleton
class LocationCapabilityImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationCapability {

    override val id = "location"
    override val description = "Last known GPS / network location"
    override val requiredPermissions = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    private val locationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun isAvailable(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun getLastKnownLocation(): CapabilityResult<LocationResult> {
        if (!isAvailable()) {
            return capabilityFailure("PERMISSION_DENIED", "Location permission not granted", true)
        }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        val location = providers.firstNotNullOfOrNull { provider ->
            runCatching {
                @Suppress("MissingPermission")
                locationManager.getLastKnownLocation(provider)
            }.getOrNull()
        } ?: return capabilityFailure("LOCATION_UNAVAILABLE", "No cached location available")

        val label = reverseGeocode(location.latitude, location.longitude)
        return capabilitySuccess(LocationResult(location.latitude, location.longitude, label))
    }

    override fun getLocationLabel(): String {
        if (!isAvailable()) return "unknown"
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        val location = providers.firstNotNullOfOrNull { provider ->
            runCatching {
                @Suppress("MissingPermission")
                locationManager.getLastKnownLocation(provider)
            }.getOrNull()
        } ?: return "unknown"
        return reverseGeocode(location.latitude, location.longitude)
    }

    private fun reverseGeocode(lat: Double, lon: Double): String {
        return runCatching {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            addresses?.firstOrNull()?.let { addr ->
                listOfNotNull(addr.locality, addr.countryName).joinToString(", ")
            } ?: "$lat,$lon"
        }.getOrDefault("$lat,$lon")
    }
}
