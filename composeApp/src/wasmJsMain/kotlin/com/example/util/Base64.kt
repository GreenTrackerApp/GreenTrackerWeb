package com.example.util

import kotlin.js.*

@JsFun("(base64) => { const binary_string = window.atob(base64); const len = binary_string.length; const bytes = new Uint8Array(len); for (let i = 0; i < len; i++) { bytes[i] = binary_string.charCodeAt(i); } return bytes; }")
external fun decodeBase64ToJsArray(base64: String): JsArray<JsNumber>

actual object Base64 {
    actual fun decode(base64: String): ByteArray {
        val jsArray = decodeBase64ToJsArray(base64)
        val size = jsArray.length
        val result = ByteArray(size)
        for (i in 0 until size) {
            val num = jsArray[i]
            if (num != null) {
                result[i] = num.toInt().toByte()
            }
        }
        return result
    }
}
