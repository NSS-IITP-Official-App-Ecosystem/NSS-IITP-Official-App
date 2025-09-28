package com.phad.chatapp.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.GeoPoint

/**
 * Service class for handling location tracking operations
 */
class LocationService(private val context: Context) {
    private val TAG = "LocationService"
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private var locationCallback: LocationCallback? = null
    
    /**
     * Get the current location once
     * @param callback Callback function with the location result
     */
    fun getCurrentLocation(callback: (Location?) -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            callback(null)
            return
        }
        
        Log.d(TAG, "Getting current location")
        
        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        Log.d(TAG, "Location retrieved: ${location.latitude}, ${location.longitude}")
                        callback(location)
                    } else {
                        Log.w(TAG, "Location is null, requesting location updates")
                        requestLocationUpdates(callback)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error getting location", e)
                    callback(null)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while getting location", e)
            callback(null)
        }
    }
    
    /**
     * Request location updates if last location is not available
     * @param callback Callback function with the location result
     */
    private fun requestLocationUpdates(callback: (Location?) -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            callback(null)
            return
        }
        
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setWaitForAccurateLocation(true)
                .setMinUpdateIntervalMillis(5000)
                .setMaxUpdateDelayMillis(15000)
                .build()
            
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        Log.d(TAG, "Location update received: ${location.latitude}, ${location.longitude}")
                        fusedLocationClient.removeLocationUpdates(this)
                        locationCallback = null
                        callback(location)
                    } ?: run {
                        Log.w(TAG, "Location update received but location is null")
                        callback(null)
                    }
                }
            }
            
            locationCallback?.let {
                fusedLocationClient.requestLocationUpdates(locationRequest, it, null)
            }
            
            // Set a timeout to stop location updates if they take too long
            android.os.Handler().postDelayed({
                locationCallback?.let {
                    Log.w(TAG, "Location updates timed out")
                    fusedLocationClient.removeLocationUpdates(it)
                    locationCallback = null
                    callback(null)
                }
            }, 30000) // 30 seconds timeout
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception while requesting location updates", e)
            callback(null)
        }
    }
    
    /**
     * Convert Android Location to Firestore GeoPoint
     * @param location The Android Location object
     * @return GeoPoint for Firestore storage
     */
    fun locationToGeoPoint(location: Location?): GeoPoint? {
        return location?.let { GeoPoint(it.latitude, it.longitude) }
    }
    
    /**
     * Calculate distance between two locations in meters
     * @param location1 First location
     * @param location2 Second location
     * @return Distance in meters
     */
    fun calculateDistance(location1: Location, location2: Location): Float {
        return location1.distanceTo(location2)
    }
    
    /**
     * Check if a location is within a specified radius of a target location
     * @param currentLocation Current location
     * @param targetLocation Target location
     * @param radiusMeters Radius in meters
     * @return True if within radius, false otherwise
     */
    fun isWithinRadius(currentLocation: Location, targetLocation: Location, radiusMeters: Float): Boolean {
        val distance = calculateDistance(currentLocation, targetLocation)
        return distance <= radiusMeters
    }
} 