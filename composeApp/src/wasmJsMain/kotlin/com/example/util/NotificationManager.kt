package com.example.util

import kotlin.js.Promise

external interface NotificationOptions : JsAny {
    var body: String?
    var icon: String?
}

@JsFun("(title, body) => { if ('serviceWorker' in navigator) { navigator.serviceWorker.ready.then(registration => { registration.showNotification(title, { body: body, icon: 'icon.png' }); }); } else if (typeof Notification !== 'undefined') { new Notification(title, { body: body }); } }")
external fun showWebNotification(title: String, body: String)

@JsFun("() => { if (typeof Notification !== 'undefined') { return Notification.permission; } return 'denied'; }")
external fun getNotificationPermission(): String

@JsFun("() => { if (typeof Notification !== 'undefined') { return Notification.requestPermission(); } return Promise.resolve('denied'); }")
external fun requestWebNotificationPermission(): Promise<JsString>

object WebNotificationManager {
    fun requestPermission(onGranted: () -> Unit = {}) {
        requestWebNotificationPermission().then { permission ->
            if (permission.toString() == "granted") {
                onGranted()
            }
            null
        }
    }

    fun showNotification(title: String, message: String) {
        val permission = getNotificationPermission()
        if (permission == "granted") {
            showWebNotification(title, message)
        } else if (permission != "denied") {
            requestPermission {
                showWebNotification(title, message)
            }
        }
    }
}
