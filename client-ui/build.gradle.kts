plugins {
    id("org.openjfx.javafxplugin") version "0.1.0"
}

dependencies {
    implementation(project(":client-core"))
    implementation(project(":client-launcher"))
    implementation(project(":client-config"))
}

javafx {
    version = "21"
    modules("javafx.controls", "javafx.fxml")
}