package com.ddgo.app.core.dev

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ddgo.app.domain.model.AiAnalysisMode

object DevOptions {
    var aiAnalysisMode by mutableStateOf(AiAnalysisMode.PHYSICS)
}
