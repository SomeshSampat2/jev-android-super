package com.jevfast.control

import android.app.Application
import com.jevfast.control.agent.AgentLoop

class JevFastApp : Application() {
    val agent: AgentLoop by lazy { AgentLoop(this) }
}
