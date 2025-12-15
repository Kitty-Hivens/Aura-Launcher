dependencies {
    // Контракты (IFileDownloadService)
    implementation(project(":client-core"))

    // Эндпоинты (CLIENT_DOWNLOAD_BASE)
    implementation(project(":client-config"))

    // OkHttp (поставляется транзитивно из client-core)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON-маппинг
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("org.apache.commons:commons-compress:1.26.1")
    implementation("commons-io:commons-io:2.15.1")
}