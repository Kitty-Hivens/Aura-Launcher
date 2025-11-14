plugins {
    id("org.openjfx.javafxplugin") version "0.1.0"
}

dependencies {
    implementation(project(":client-core"))
    implementation(project(":client-launcher"))
    implementation(project(":client-config"))

    // HTTP-клиент
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON-маппинг
    implementation("com.google.code.gson:gson:2.10.1")
}

javafx {
    version = "21"
    modules("javafx.controls", "javafx.fxml")
}