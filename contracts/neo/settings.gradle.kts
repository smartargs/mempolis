pluginManagement {
    val neow3jVersion: String by settings
    plugins {
        id("io.neow3j.gradle-plugin") version neow3jVersion
    }
}

rootProject.name = "mempolis-contracts-neo"

include("testkit")
include("guestbook")
include("credits")
