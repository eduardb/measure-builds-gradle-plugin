package com.automattic.android.measure

import com.automattic.android.measure.analytics.BuildFinishedFlowAction
import com.automattic.android.measure.analytics.networking.AppsMetricsReporter
import com.gradle.scan.plugin.BuildScanExtension
import kotlinx.coroutines.runBlocking
import org.codehaus.groovy.runtime.EncodingGroovyMethods
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.flow.FlowActionSpec
import org.gradle.api.flow.FlowProviders
import org.gradle.api.flow.FlowScope
import org.gradle.api.provider.Provider
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.internal.buildevents.BuildStartedTime
import org.gradle.invocation.DefaultGradle
import javax.inject.Inject
import kotlin.time.ExperimentalTime

const val EXTENSION_NAME = "tracks"

@Suppress("MaxLineLength")
private const val NO_GRADLE_ENTERPRISE_PLUGIN_MESSAGE =
    "The project has no Gradle Enterprise plugin enabled and `attachGradleScanId` option enabled. No metric will be send in this configuration."

@Suppress("UnstableApiUsage")
@ExperimentalTime
class BuildTimePlugin @Inject constructor(
    private val registry: BuildEventsListenerRegistry,
    private val flowScope: FlowScope,
    private val flowProviders: FlowProviders,
) : Plugin<Project> {
    override fun apply(project: Project) {
        val start =
            (project.gradle as DefaultGradle).services[BuildStartedTime::class.java].startTime

        val authToken: String? = project.properties["appsMetricsToken"] as String?
        if (authToken.isNullOrBlank()) {
            project.logger.warn("Did not find appsMetricsToken in gradle.properties. Skipping reporting.")
            return
        }

        val analyticsReporter = AppsMetricsReporter(project.logger)

        val extension =
            project.extensions.create(EXTENSION_NAME, TracksExtension::class.java, project)

        val encodedUser: String = prepareUser(project, extension)

        project.gradle.projectsEvaluated {
            prepareBuildScanListener(project, extension, analyticsReporter, authToken, onSuccess = {
                InMemoryReport.buildDataStore =
                    BuildDataFactory.buildData(
                        project,
                        extension.automatticProject.get(),
                        encodedUser
                    )
                prepareBuildTaskService(project)
                prepareBuildFinishedAction(extension, analyticsReporter, authToken, start)
            })
        }
    }

    private fun prepareBuildScanListener(
        project: Project,
        extension: TracksExtension,
        analyticsReporter: AppsMetricsReporter,
        authToken: String,
        onSuccess: () -> Unit,
    ) {
        val buildScanExtension = project.extensions.findByType(BuildScanExtension::class.java)
        if (buildScanExtension != null && extension.attachGradleScanId.get() == true) {
            buildScanExtension.buildScanPublished {
                runBlocking {
                    analyticsReporter.report(InMemoryReport, authToken, it.buildScanId)
                }
            }
        } else if (extension.attachGradleScanId.get() == true) {
            project.logger.warn(NO_GRADLE_ENTERPRISE_PLUGIN_MESSAGE)
            return
        }
        onSuccess.invoke()
    }

    private fun prepareBuildFinishedAction(
        extension: TracksExtension,
        analyticsReporter: AppsMetricsReporter,
        authToken: String?,
        start: Long
    ) {
        flowScope.always(
            BuildFinishedFlowAction::class.java
        ) { spec: FlowActionSpec<BuildFinishedFlowAction.Parameters> ->
            spec.parameters.apply {
                this.buildWorkResult.set(flowProviders.buildWorkResult)
                this.attachGradleScanId.set(extension.attachGradleScanId)
                this.analyticsReporter.set(analyticsReporter)
                this.authToken.set(authToken)
                this.startTime.set(start)
            }
        }
    }

    private fun prepareBuildTaskService(project: Project) {
        val serviceProvider: Provider<BuildTaskService> =
            project.gradle.sharedServices.registerIfAbsent(
                "taskEvents",
                BuildTaskService::class.java
            ) {
            }
        registry.onTaskCompletion(serviceProvider)
    }

    private fun prepareUser(project: Project, extension: TracksExtension): String {
        val user = project.providers.systemProperty("user.name").get()

        val encodedUser: String = user.let {
            if (extension.obfuscateUsername.getOrElse(false) == true) {
                EncodingGroovyMethods.digest(it, "SHA-1")
            } else {
                it
            }
        }

        return encodedUser
    }
}