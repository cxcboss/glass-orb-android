plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}

// macOS Desktop may be backed by a file-provider volume. Keeping rapidly rewritten
// compiler outputs in the process temp directory prevents conflict-copy class files.
val localBuildRoot = file("${System.getProperty("java.io.tmpdir")}/glass-orb-android-build")
layout.buildDirectory.set(localBuildRoot.resolve("root"))
subprojects {
    layout.buildDirectory.set(localBuildRoot.resolve(name))
}
