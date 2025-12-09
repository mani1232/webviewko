plugins {
    kotlin("multiplatform") version "2.3.0-RC3"
    id("maven-publish")
    id("org.jetbrains.dokka") version "2.1.0"
}

group = "com.github.winterreisender"
version = "0.7.0"
description = "webviewko"

repositories {
    mavenCentral()
}

lateinit var osPrefix: String

kotlin {
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")

    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val isMac = hostOs == "Mac OS X"
    val isLinux = hostOs == "Linux"

    osPrefix = when {
        isLinux -> "linuxX64"
        isMac -> "macosX64"
        isMingwX64 -> "mingwX64"
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    val nativeTarget = when {
        isMingwX64 -> mingwX64("native")
        isLinux -> linuxX64("native")
        isMac -> macosX64("native")
        else -> throw GradleException("$hostOs is not supported.")
    }

    nativeTarget.apply {
        compilations.getByName("main") {
            cinterops {
                val cwebview by creating {
                    val interopDir = project.file("src/commonMain/nativeInterop/cinterop")
                    definitionFile.set(interopDir.resolve("webview.def"))

                    packageName("${group}.cwebview")

                    includeDirs(interopDir)

                    if (isLinux) {
                        try {
                            val cflags = "pkg-config --cflags gtk+-3.0 webkit2gtk-4.0".runCommand()
                            extraOpts("-compiler-options", cflags)
                        } catch (e: Exception) {
                            println("Warning: pkg-config failed. Make sure libgtk-3-dev and libwebkit2gtk-4.0-dev are installed.")
                        }
                    }
                }
            }
        }

        binaries {
            executable {
                entryPoint = "main"

                if (isLinux) {
                    try {
                        val libs = "pkg-config --libs gtk+-3.0 webkit2gtk-4.0".runCommand()
                        linkerOpts(libs.split("\\s+".toRegex()))
                    } catch (e: Exception) {
                        println("Warning: pkg-config failed during linking config.")
                    }
                }
            }

            test("native") {
                if (isLinux) {
                    val libs = "pkg-config --libs gtk+-3.0 webkit2gtk-4.0".runCommand()
                    linkerOpts(libs.split("\\s+".toRegex()))
                }
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                //implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            }
        }
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

publishing {
    repositories {
        maven {
            name = "GitLabPackages"
            url = uri("https://gitlab.com/api/v4/projects/38224197/packages/maven")
            credentials(HttpHeaderCredentials::class) {
                name = "Private-Token"
                value = System.getenv("GITLAB_TOKEN")
            }
            authentication {
                create<HttpHeaderAuthentication>("header")
            }
        }
    }
}

fun String.runCommand(): String {
    return try {
        val parts = this.split("\\s+".toRegex())
        val proc = ProcessBuilder(*parts.toTypedArray())
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
        proc.waitFor(10, TimeUnit.SECONDS)
        proc.inputStream.bufferedReader().readText().trim()
    } catch(e: Exception) {
        ""
    }
}