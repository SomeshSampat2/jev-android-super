package com.jevfast.control.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jevfast.control.JevFastApp
import com.jevfast.control.agent.AgentLoop
import com.jevfast.control.agent.RunState
import com.jevfast.control.agent.StepEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AgentViewModel(app: Application) : AndroidViewModel(app) {
    private val agent: AgentLoop = (app as JevFastApp).agent
    private var job: Job? = null

    val state: StateFlow<RunState> = agent.state
    val log: StateFlow<List<StepEntry>> = agent.log
    val elapsedMs: StateFlow<Long> = agent.elapsedMs
    val snackbar: StateFlow<String?> = agent.snackbar

    fun consumeSnackbar() = agent.consumeSnackbar()

    fun start(goal: String, texts: List<String>) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            agent.run(goal.trim(), texts.map { it.trim() }.filter { it.isNotEmpty() })
        }
    }

    fun stop() {
        job?.cancel()
        agent.stop()
    }

    fun submitText(text: String) = agent.submitText(text)
}
