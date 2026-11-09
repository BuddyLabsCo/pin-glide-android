package com.buddylabs.pinglide.domain.settings

interface SetupProgress {
    val state: SetupState

    fun start()

    fun recordSmsRequest()

    fun completeSmsPrerequisite()

    fun acknowledgeBattery()

    fun complete()
}

data class SetupState(
    val started: Boolean = false,
    val smsRequested: Boolean = false,
    val smsCompleted: Boolean = false,
    val batteryAcknowledged: Boolean = false,
    val completed: Boolean = false
)
