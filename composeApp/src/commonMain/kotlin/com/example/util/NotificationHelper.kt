package com.example.util

expect object NotificationHelper {
    fun notify(title: String, message: String)
    fun requestPermission()
}
