import org.gradle.kotlin.dsl.support.listFilesOrdered

plugins {
    kotlin("multiplatform") version "2.2.10" apply false
    kotlin("plugin.serialization") version "2.2.10" apply false
    id("com.android.library") version "8.9.3" apply false
    id("org.jetbrains.dokka") version "2.0.0"
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.17.0"
}

val artifactVersion: String by extra
group = "at.asitplus"
version = artifactVersion

dependencies {
    dokka(project(":cidre"))
}

apiValidation {
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        enabled = true
    }
}

dokka {
    val moduleDesc = File("$rootDir/dokka-tmp.md").also { it.createNewFile() }
    val readme =
        File("${rootDir}/README.md").readText()
    moduleDesc.writeText("\n\n$readme")
    moduleName.set("CIDRE")

    basePublicationsDirectory.set(file("${rootDir}/docs"))
    dokkaPublications.html {
        includes.from(moduleDesc)
    }
    pluginsConfiguration.html {
        footerMessage = "&copy; 2025 A-SIT Plus GmbH"
    }
}

tasks.dokkaGenerate {
    doLast {
        rootDir.listFilesOrdered { it.extension.lowercase() == "png" || it.extension.lowercase() == "svg" }
            .forEach { it.copyTo(File("$rootDir/docs/html/${it.name}"), overwrite = true) }

    }
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(java.net.URI("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(java.net.URI("https://central.sonatype.com/repository/maven-snapshots/"))
        }
    }
}
