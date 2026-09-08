pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "net.devrieze.gradlecodegen" -> Unit /*{

                    useVersion(requested.version ?: codegen_version)
                }*/

                "org.jetbrains.kotlin.android",

                    "org.jetbrains.kotlin.jvm",

                "kotlin-android-extensions",

                "org.jetbrains.kotlin.multiplatform" -> {
                    val ver = requested.version ?: "2.4.10"
                    useVersion(ver)
                }
            }
        }
    }

}

rootProject.name="gradle-codegen"
