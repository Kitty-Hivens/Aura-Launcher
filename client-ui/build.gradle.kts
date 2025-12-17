plugins {
    id("java")
    id("application")
    id("org.openjfx.javafxplugin") version "0.0.13"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":client-core"))
    implementation(project(":client-launcher"))
    implementation(project(":client-config"))

    // JavaFX
    implementation("org.openjfx:javafx-controls:17.0.8")
    implementation("org.openjfx:javafx-fxml:17.0.8")
    implementation("org.openjfx:javafx-graphics:21.0.2")

    // Modern UI (AtlantaFX) & Icons
    implementation("io.github.mkpaz:atlantafx-base:2.0.1")
    implementation("org.kordamp.ikonli:ikonli-javafx:12.3.1")
    implementation("org.kordamp.ikonli:ikonli-feather-pack:12.3.1")
    implementation("org.kordamp.ikonli:ikonli-material2-pack:12.3.1")

    // Utils
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.5.13")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
}

javafx {
    version = "21"
    modules("javafx.controls", "javafx.fxml", "javafx.graphics")
    application {
        mainClass.set("hivens.ui.Main")
    }
}
