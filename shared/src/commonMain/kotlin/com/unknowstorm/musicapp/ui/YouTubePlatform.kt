package com.unknowstorm.musicapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Simple flag storage (e.g. "accepted the YouTube terms"). */
interface FlagStore {
    fun get(key: String): Boolean
    fun set(key: String, value: Boolean)
}

@Composable
expect fun rememberFlagStore(): FlagStore

/** Opens a link in an external app or browser. */
@Composable
expect fun rememberExternalLinkOpener(): (String) -> Unit

/**
 * Embedded YouTube player. The video plays only while this component is on screen
 * (YouTube rules forbid background playback).
 * onError receives an IFrame API error code: 100, 101, 150, 153, etc.
 */
@Composable
expect fun YouTubePlayerView(videoId: String, modifier: Modifier, onError: (Int) -> Unit)

/**
 * Persistent string storage (survives restart, update and rebuild).
 * quotaDay: the number of the calendar day in Pacific Time when the YouTube quota resets.
 */
interface TextStore {
    fun get(key: String): String?
    fun set(key: String, value: String)
    fun nowMillis(): Long
    fun quotaDay(millis: Long): Long
}

@Composable
expect fun rememberTextStore(): TextStore
