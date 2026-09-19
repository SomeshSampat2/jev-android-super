package com.jevfast.control

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jevfast.control.a11y.ControlService

/** Handles the STOP action on the ongoing agent-control notification. */
class StopAgentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ControlService.ACTION_STOP) {
            (context.applicationContext as? JevFastApp)?.agent?.stop()
        }
    }
}
