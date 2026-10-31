val neow3jVersion: String by project

plugins {
    id("io.neow3j.gradle-plugin")
}

dependencies {
    implementation("io.neow3j:devpack:$neow3jVersion")
    testImplementation(project(":testkit"))
}

neow3jCompiler {
    className = "com.smartargs.mempolis.guestbook.FoundersGuestbook"
}
