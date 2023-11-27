package com.automattic.android.measure

data class BuildData(
    val forProject: TracksExtension.AutomatticProject,
    val user: String,
    val tasks: List<String>,
    val daemonsRunning: Int,
    val thisDaemonBuilds: Int,
    val gradleVersion: String,
    val operatingSystem: String,
    val environment: Environment,
    val isConfigureOnDemand: Boolean,
    val isConfigurationCache: Boolean,
    val isBuildCache: Boolean,
    val maxWorkers: Int,
    val buildDataCollectionOverhead: Long,
    val includedBuildsNames: List<String>,
    val architecture: String,
)

data class ExecutionData(
    val buildTime: Long,
    val failed: Boolean,
    val failure: Throwable?,
    val taskStatistics: TaskStatistics,
    val buildFinishedTimestamp: Long,
)

enum class Environment {
    IDE,
    CI,
    CMD
}

data class TaskStatistics(
    val upToDate: Int,
    val fromCache: Int,
    val executed: Int
)