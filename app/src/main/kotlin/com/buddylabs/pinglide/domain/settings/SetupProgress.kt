package com.buddylabs.pinglide.domain.settings

interface SetupProgress {
    val started: Boolean

    fun start()
}
