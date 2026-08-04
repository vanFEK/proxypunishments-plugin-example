rootProject.name = "proxybans-plugin"

include(":api", ":core", ":velocity")

project(":api").projectDir = file("modules/api")
project(":core").projectDir = file("modules/core")
project(":velocity").projectDir = file("modules/velocity")
