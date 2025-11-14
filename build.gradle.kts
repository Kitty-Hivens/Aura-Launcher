plugins {
    kotlin("jvm") version "1.9.22" apply false
    id("java")
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    dependencies {
        implementation(kotlin("stdlib-jdk8"))

        compileOnly("org.projectlombok:lombok:1.18.30")
        annotationProcessor("org.projectlombok:lombok:1.18.30")

        implementation("org.slf4j:slf4j-api:2.0.12")
        runtimeOnly("ch.qos.logback:logback-classic:1.4.14")
    }
}