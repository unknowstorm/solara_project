package com.unknowstorm.musicapp.imports

/** Gives screens that are far from MainScreen (the profile editor) access to the ImportRepository. Same idea as DownloadsHolder. */
object ImportHolder {
    var repo: ImportRepository? = null
}
