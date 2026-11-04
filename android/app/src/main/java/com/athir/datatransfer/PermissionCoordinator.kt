package com.athir.datatransfer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionCoordinator {

    fun getRequiredMediaPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun getRequiredNearbyPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun getRequiredCameraPermissions(): Array<String> {
        return arrayOf(Manifest.permission.CAMERA)
    }

    fun getRequiredNotificationPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }
    }

    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun arePermissionsGranted(context: Context, permissions: Array<String>): Boolean {
        return permissions.all { isPermissionGranted(context, it) }
    }

    fun checkAllPermissions(context: Context): PermissionStatus {
        val media = arePermissionsGranted(context, getRequiredMediaPermissions())
        val nearby = arePermissionsGranted(context, getRequiredNearbyPermissions())
        val camera = arePermissionsGranted(context, getRequiredCameraPermissions())
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arePermissionsGranted(context, getRequiredNotificationPermissions())
        } else {
            true
        }
        val contacts = isPermissionGranted(context, Manifest.permission.READ_CONTACTS)

        val allGranted = media && nearby && camera

        return PermissionStatus(
            mediaGranted = media,
            nearbyGranted = nearby,
            cameraGranted = camera,
            notificationGranted = notification,
            contactsGranted = contacts,
            allGranted = allGranted,
            sdkInt = Build.VERSION.SDK_INT
        )
    }

    fun getMissingPermissions(context: Context): List<String> {
        val allNeeded = mutableListOf<String>()
        allNeeded.addAll(getRequiredMediaPermissions())
        allNeeded.addAll(getRequiredNearbyPermissions())
        allNeeded.addAll(getRequiredCameraPermissions())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            allNeeded.addAll(getRequiredNotificationPermissions())
        }
        allNeeded.add(Manifest.permission.READ_CONTACTS)

        return allNeeded.filter { !isPermissionGranted(context, it) }
    }

    data class PermissionStatus(
        val mediaGranted: Boolean,
        val nearbyGranted: Boolean,
        val cameraGranted: Boolean,
        val notificationGranted: Boolean,
        val contactsGranted: Boolean,
        val allGranted: Boolean,
        val sdkInt: Int
    )
}
