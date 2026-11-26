package com.vsa.vpnswitch

import kotlinx.coroutines.flow.MutableStateFlow

data class MonitorStatus(
    val running: Boolean = false,
    val text: String = "",
)

/** Prozessweiter Status, damit die Oberfläche anzeigen kann, was der Dienst gerade tut. */
object MonitorState {
    val status = MutableStateFlow(MonitorStatus())
}
