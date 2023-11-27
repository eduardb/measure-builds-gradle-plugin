package io.github.wzieba.tracks.plugin

import io.github.wzieba.tracks.plugin.analytics.BuildFinishedFlowAction
import io.github.wzieba.tracks.plugin.analytics.networking.AppsMetricsReporter
import org.codehaus.groovy.runtime.EncodingGroovyMethods
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.flow.FlowActionSpec
import org.gradle.api.flow.FlowProviders
import org.gradle.api.flow.FlowScope
import org.gradle.api.provider.Provider
import org.gradle.build.event.BuildEventsListenerRegistry
import javax.inject.Inject
import kotlin.time.ExperimentalTime

const val EXTENSION_NAME = "tracks"

@Suppress("UnstableApiUsage")
@ExperimentalTime
class BuildTimePlugin @Inject constructor(
    private val registry: BuildEventsListenerRegistry,
    private val flowScope: FlowScope,
    private val flowProviders: FlowProviders,
) : Plugin<Project> {
    override fun apply(project: Project) {
        val authToken: String? = project.properties["appsMetricsToken"] as String?

        if (authToken.isNullOrBlank()) {
            project.logger.warn("Did not find appsMetricsToken in gradle.properties. Skipping reporting.")
            return
        }

        val analyticsReporter = AppsMetricsReporter(project.logger)

        val extension =
            project.extensions.create(
                EXTENSION_NAME,
                TracksExtension::class.java,
                project,
                InMemoryReport,
                analyticsReporter,
                authToken
            )

        val user = project.providers.systemProperty("user.name").get()

        val encodedUser: String = user.let {
            if (extension.obfuscateUsername.getOrElse(false) == true) {
                EncodingGroovyMethods.digest(it, "SHA-1")
            } else {
                it
            }
        }

        project.gradle.projectsEvaluated {
            InMemoryReport.buildDataStore =
                BuildDataFactory.buildData(project, extension.automatticProject.get(), encodedUser)

            val serviceProvider: Provider<BuildTaskService> =
                project.gradle.sharedServices.registerIfAbsent(
                    "taskEvents",
                    BuildTaskService::class.java
                ) {
                }
            registry.onTaskCompletion(serviceProvider)

            flowScope.always(
                BuildFinishedFlowAction::class.java
            ) { spec: FlowActionSpec<BuildFinishedFlowAction.Parameters> ->
                spec.parameters.apply {
                    this.buildWorkResult.set(flowProviders.buildWorkResult)
                    this.sendMetricsOnBuildFinished.set(extension.sendMetricsOnBuildFinished)
                    this.analyticsReporter.set(analyticsReporter)
                    this.authToken.set(authToken)
                }
            }
        }
    }
}
