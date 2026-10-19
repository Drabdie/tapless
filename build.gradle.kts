plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}

// Одна команда вместо двух: ./gradlew apk  ->  собирает оба debug APK.
tasks.register("apk") {
    group = "build"
    description = "Build both debug APKs (Main App + Companion Agent) in one step."
    dependsOn(":app:assembleDebug", ":agent-companion:assembleDebug")

    // Захватываем провайдеры вне doLast — иначе configuration cache не сериализует Project.
    val projectDir = layout.projectDirectory
    val outDir = layout.buildDirectory.dir("apk")
    val sources = listOf(
        "app/build/outputs/apk/debug/app-debug.apk" to "AI-Phone-Agent-debug.apk",
        "agent-companion/build/outputs/apk/debug/agent-companion-debug.apk" to "AI-Phone-Agent-Companion-debug.apk",
    )
    doLast {
        val out = outDir.get().asFile
        out.mkdirs()
        sources.forEach { (src, name) ->
            val f = projectDir.file(src).asFile
            if (f.exists()) f.copyTo(java.io.File(out, name), overwrite = true)
        }
        println("APK -> ${out.absolutePath}")
    }
}

// Быстрая сборка только Main App (самый частый кейс).
tasks.register("apkMain") {
    group = "build"
    description = "Build only the Main App debug APK."
    dependsOn(":app:assembleDebug")
}
