plugins {
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.beryx.jlink") version "3.0.1"
    id("application")
}

dependencies {
    implementation(project(":client-core"))
    implementation(project(":client-launcher"))
    implementation(project(":client-config"))

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
}

javafx {
    version = "21"
    modules("javafx.controls", "javafx.fxml")
    application {
        mainClass.set("hivens.ui.Main")
    }
}

jlink {
    jpackage {
        // Имя приложения
        launcher {
            name = "SCOL"
        }

        // Главный класс (точка входа)
        mainClass = "hivens.ui.Main"

        // Версия и поставщик
        appVersion = "1.0.0"
        vendor = "SCOL Community"

        // (Опционально) Добавляем иконки (путь к .ico/.icns)
        // icon = "src/main/resources/images/icon.ico"

        // (Опционально) JVM флаги по умолчанию
        jvmArgs.addAll(listOf("-XX:+UseG1GC"))
    }
}