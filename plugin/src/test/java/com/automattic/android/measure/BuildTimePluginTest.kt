package com.automattic.android.measure

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

@Suppress("MaximumLineLength", "MaxLineLength")
class BuildTimePluginTest {

    @Test
    fun `given a project that attaches gradle scan id, when executing a task with configuration from cache, then send the report with attached gradle scan id`() {
        // given
        val runner = functionalTestRunner()

        // when
        val prepareConfigurationCache = runner.withArguments("--configuration-cache", "help").build()

        // then
        assertThat(prepareConfigurationCache.output)
            .contains("Calculating task graph as no configuration cache is available for tasks")
            .contains("Configuration cache entry stored")

        // when
        val buildUsingConfigurationCache = runner.withArguments("--configuration-cache", "help", "--debug").build()

        // then
        assertThat(buildUsingConfigurationCache.output)
            .contains("Reusing configuration cache")
            .contains("Reporting build data to Apps Metrics...")
            .contains("{\"name\":\"woocommerce-gradle-scan-id\",\"value\":")
            .doesNotContain("{\"name\":\"woocommerce-gradle-scan-id\",\"value\":\"null\"}")
    }

    @BeforeEach
    fun clearCache() {
        val projectDir = File("build/tmp/test/work/.gradle-test-kit/caches")
        projectDir.deleteRecursively()
    }

    private fun functionalTestRunner(vararg arguments: String): GradleRunner {
        val projectDir = File("build/functionalTest")
        projectDir.mkdirs()
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            plugins {
                id("com.gradle.enterprise") version "3.15.1"
            }
            gradleEnterprise {
                buildScan {
                    publishAlways()
                    termsOfServiceUrl = "https://gradle.com/terms-of-service"
                    termsOfServiceAgree = "yes"
                }
            }
            """.trimIndent()
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("com.automattic.android.measure-builds")
            }
            measureBuilds {
                attachGradleScanId.set(true)
                automatticProject.set(com.automattic.android.measure.MeasureBuildsExtension.AutomatticProject.WooCommerce)
            }
        """
        )
        projectDir.resolve("gradle.properties").writeText("appsMetricsToken=token")

        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments(arguments.toList())
        runner.withProjectDir(projectDir)
        return runner
    }
}