plugins {
    kotlin("multiplatform") version "2.3.0-RC3"
    kotlin("plugin.serialization") version "2.3.0-RC3"
    id("maven-publish")
    id("org.jetbrains.dokka") version "2.1.0"
}

group = "com.github.winterreisender"
version = "0.7.0"
description = "webviewko"

repositories {
    mavenCentral()
}

kotlin {
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")

    mingwX64 {
        compilations.getByName("main") {
            cinterops {
                val webview by cinterops.creating {
                    definitionFile.set(project.file("src/nativeInterop/cinterop/webview.def"))

                    packageName("$group.cwebview")

                    includeDirs("nativeInterop/include")
                    extraOpts(
                        "-libraryPath", project.file("libs/windows").absolutePath,
                        "-verbose"
                    )
                }
            }
        }
    }
    linuxX64 {
        compilations.getByName("main") {
            cinterops {
                val webview by cinterops.creating {
                    includeDirs("nativeInterop/include")
                    definitionFile.set(project.file("src/nativeInterop/cinterop/webview.def"))

                    packageName("$group.cwebview")
                    extraOpts("-libraryPath", project.file("libs/linux").absolutePath)
                }
            }
        }
    }

    macosX64 {
        compilations.getByName("main") {
            cinterops {
                val webview by creating {
                    defFile(project.file("src/nativeInterop/cinterop/webview.def"))
                    packageName("$group.cwebview")
                    includeDirs(project.file("nativeInterop/include"))

                    extraOpts("-libraryPath", project.file("libs/macos").absolutePath)
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
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
                implementation(kotlin("test"))
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
            name = "worldMandia"
            url = uri("https://repo.worldmandia.cc/snapshots")
            credentials {
                username = System.getenv("REPO_USER")
                password = System.getenv("REPO_PASSWORD")
            }
        }
    }
}
