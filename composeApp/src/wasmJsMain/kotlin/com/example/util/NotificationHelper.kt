package com.example.util

actual object NotificationHelper {
    actual fun notify(title: String, message: String) {
        WebNotificationManager.showNotification(title, message)
    }

    actual fun requestPermission() {
        WebNotificationManager.requestPermission()
    }
}
