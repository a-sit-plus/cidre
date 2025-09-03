//import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI

plugins {
    id("com.android.library")
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("signing")
    id("org.jetbrains.dokka")
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
    //   id("io.gitlab.arturbosch.detekt") version "1.23.7"
}

val artifactVersion: String by extra
group = "at.asitplus"
version = artifactVersion

repositories {
    google()
    mavenCentral()
}

val dokkaOutputDir = "$projectDir/docs"
dokka {
    dokkaSourceSets {

        named("commonMain") {
            sourceLink {
                val path = "${projectDir}/src/$name/kotlin"
                println(path)
                localDirectory.set(file(path))
                remoteUrl.set(
                    URI("https://github.com/a-sit-plus/cidre/tree/main/cidre/src/$name/kotlin")
                )
                // Suffix which is used to append the line number to the URL. Use #L for GitHub
                remoteLineSuffix.set("#L")
            }
        }
    }
    pluginsConfiguration.html {
        footerMessage = "&copy; 2025 A-SIT Plus GmbH"
    }
}


val deleteDokkaOutputDir by tasks.register<Delete>("deleteDokkaOutputDirectory") {
    delete(dokkaOutputDir)
}
val javadocJar = tasks.register<Jar>("javadocJar") {
    dependsOn(deleteDokkaOutputDir, tasks.dokkaGenerate)
    archiveClassifier.set("javadoc")
    from(dokkaOutputDir)
}

tasks.getByName("check") {
    //  dependsOn("detektMetadataMain")
}


//first sign everything, then publish!
tasks.withType<AbstractPublishToMaven>() {
    tasks.withType<Sign>().forEach {
        dependsOn(it)
    }
}

kotlin {

    applyDefaultHierarchyTemplate()

    jvmToolchain(17)
    val xcf = org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFrameworkConfig(project, "CIDRE")
    listOf(
        macosArm64(),
        macosX64(),
        tvosArm64(),
        tvosX64(),
        tvosSimulatorArm64(),
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
        watchosSimulatorArm64(),
        watchosX64(),
        watchosArm32(),
        watchosArm64(),
        watchosDeviceArm64(),
    ).forEach {
        it.binaries.framework {
            baseName = "CIDRE"
            binaryOption("bundleId", "at.asitplus.cidre")
            xcf.add(this)
            isStatic = true
        }
    }

    androidNativeX64()
    androidNativeX86()
    androidNativeArm32()
    androidNativeArm64()
    androidTarget {
        compilerOptions {
            publishLibraryVariants("release")
            jvmTarget = JvmTarget.JVM_1_8
        }
    }

    jvm {
        compilerOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = JvmTarget.JVM_17
        }
    }


    listOf(
        js(IR).apply { browser { testTask { enabled = false } } },
        @OptIn(ExperimentalWasmDsl::class)
        wasmJs().apply { browser { testTask { enabled = false } } },
        @OptIn(ExperimentalWasmDsl::class)
        wasmWasi()
    ).forEach {
        it.nodejs()
    }

    linuxX64()
    linuxArm64()
    mingwX64()

    sourceSets {

        val posixMain by creating { dependsOn(commonMain.get()) }
        androidNativeMain.get().dependsOn(posixMain)
        appleMain.get().dependsOn(posixMain)
        linuxMain.get().dependsOn(posixMain)


        val posixTest by creating { dependsOn(commonTest.get()) }
        androidNativeTest.get().dependsOn(posixTest)
        appleTest.get().dependsOn(posixTest)
        linuxTest.get().dependsOn(posixTest)

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
        }
    }

}


android {
    namespace = "at.asitplus.cidre"
    compileSdk = 36
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    defaultConfig {
        minSdk = 21
    }
}

/*
tasks.withType<Detekt>().configureEach {
    reports {
        xml.required.set(true)
        html.required.set(false)
        txt.required.set(false)
        sarif.required.set(true)
        md.required.set(true)
    }
}*/
/*
dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
}*/

repositories {
    mavenCentral()
}

publishing {
    publications {
        withType<MavenPublication> {
            artifact(javadocJar)
            pom {
                name.set("CIDRE")
                description.set("100% Pure KMP IPv4 + IPv6 Representation + CIDR Math")
                url.set("https://github.com/a-sit-plus/cidre")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("JesusMcCloud")
                        name.set("Bernd Prünster")
                        email.set("bernd.pruenster@a-sit.at")
                    }
                    developer {
                        id.set("StjepanovicSrdjan")
                        name.set("Srđan Stjepanović")
                        email.set("srdan.stjepanovic@tugraz.at")
                    }
                }
                scm {
                    connection.set("scm:git:git@github.com:a-sit-plus/cidre.git")
                    developerConnection.set("scm:git:git@github.com:a-sit-plus/cidre.git")
                    url.set("https://github.com/a-sit-plus/cidre")
                }
            }
        }
    }
    repositories {
        mavenLocal {
            signing.isRequired = false
        }
    }
}

afterEvaluate {
    val signingTasks = tasks.withType<Sign>()
    if (signingTasks.isNotEmpty()) {
        logger.lifecycle("")
        logger.lifecycle("  Making signing tasks of project ${name} run after publish tasks")
        tasks.withType<AbstractPublishToMaven>().configureEach {
            mustRunAfter(*signingTasks.toTypedArray())
            logger.info("   * $name must now run after ${signingTasks.joinToString { it.name }}")
        }
        logger.info("")
    }
}

signing {
    val signingKeyId: String? by project
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
    sign(publishing.publications)
}

