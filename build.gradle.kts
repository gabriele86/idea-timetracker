import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
        intellijDependencies()
    }
}

dependencies {
    intellijPlatform {
        create(
            providers.gradleProperty("platformType").get(),
            providers.gradleProperty("platformVersion").get(),
        )
        bundledPlugin("Git4Idea")
        bundledModule("intellij.platform.vcs.dvcs.impl")

        pluginVerifier()
        zipSigner()
        instrumentationTools()
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

kotlin {
    jvmToolchain(21)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }

    signing {
        certificateChainFile = layout.projectDirectory.file("chain.crt")
        privateKeyFile = layout.projectDirectory.file("private.pem")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            // Pin explicit targets: `recommended()` chases the latest release and
            // breaks around EAP windows when that build is not published yet.
            ide(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
            ide("IC", "2025.2")
        }
    }
}

tasks {
    wrapper {
        gradleVersion = "8.10.2"
    }

    test {
        useJUnit()
    }
}
