plugins {
    id("com.android.library") version "8.7.0"
    kotlin("android") version "1.9.23"
    `maven-publish`
}

android {
    namespace = "dev.k9o.barbican"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        targetSdk = 36
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // The per-ABI compiled core (libk9o_keep_core_ffi_uniffi.so) is dropped into
    // src/main/jniLibs/<abi>/ by CI (gitignored; see README "Native artifact").
    sourceSets["main"].jniLibs.srcDirs("src/main/jniLibs")

    testOptions {
        unitTests.all { it.systemProperty("k9o.pkg.dir", projectDir.absolutePath) }
    }
}

dependencies {
    // uniffi runtime: JNA (Android aar variant). Barbican is fully synchronous: no coroutines needed.
    implementation("net.java.dev.jna:jna:5.14.0@aar")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    // Host-JVM unit tests load the engine through the plain JNA jar (the aar is Android-only).
    testImplementation("net.java.dev.jna:jna:5.14.0")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "dev.k9o"
                artifactId = "barbican"
                version = "1.0.0"
                pom {
                    name.set("K9OBarbican Android SDK")
                    description.set("Barbican, the free embeddable L2 guardrail for Android: a thin Kotlin wrapper over the keep-core Rust engine bound via uniffi. The wrapper does no cryptography and holds no key.")
                    url.set("https://kilimanjaro.dev")
                    licenses {
                        license {
                            name.set("Apache License 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            name.set("Kilimanjaro Engineering")
                            email.set("engineering@kilimanjaro.dev")
                        }
                    }
                    scm {
                        connection.set("scm:git:https://github.com/k9o-platform/sdk-barbican-kotlin.git")
                        developerConnection.set("scm:git:ssh://git@github.com/k9o-platform/sdk-barbican-kotlin.git")
                        url.set("https://github.com/k9o-platform/sdk-barbican-kotlin")
                    }
                }
            }
        }
    }
}
