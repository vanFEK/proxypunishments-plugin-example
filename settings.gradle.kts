rootProject.name = "proxypunishments-plugin"

include(":api", ":core", ":velocity", ":paper")

project(":api").projectDir = file("modules/api")
project(":core").projectDir = file("modules/core")
project(":velocity").projectDir = file("modules/velocity")
project(":paper").projectDir = file("modules/paper")
