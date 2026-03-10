package com.ddgo.app.domain.repository

import com.ddgo.app.domain.model.BenchmarkResult
import com.ddgo.app.domain.model.ModelInfo
import com.ddgo.app.domain.model.SimState

/** MuJoCo 물리 엔진 인터페이스 (계약서) */
interface PhysicsEngine {
    fun load(): Boolean
    fun init(xml: String): Boolean
    fun step()
    fun benchmark(nSteps: Int): BenchmarkResult?
    fun getState(): SimState?
    fun getModelInfo(): ModelInfo?
    fun version(): String
    fun close()
}
