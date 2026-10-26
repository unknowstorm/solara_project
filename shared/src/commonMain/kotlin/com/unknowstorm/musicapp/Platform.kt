package com.unknowstorm.musicapp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform