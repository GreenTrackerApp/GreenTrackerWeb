package com.example.util

expect object Base64 {
    fun decode(base64: String): ByteArray
}
