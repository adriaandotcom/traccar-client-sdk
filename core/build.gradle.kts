import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
plugins { alias(libs.plugins.kotlinMultiplatform) }
kotlin {
    val framework = XCFramework("TraccarClientSDK")
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
        target.binaries.framework {
            baseName = "TraccarClientSDK"
            isStatic = true
            framework.add(this)
        }
    }
    sourceSets {
        commonMain.dependencies { implementation(libs.kotlinx.coroutines.core) }
        commonTest.dependencies { implementation(kotlin("test")); implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0") }
    }
}
