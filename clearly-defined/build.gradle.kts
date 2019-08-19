import java.net.URL

plugins {
    // Apply core plugins.
    `java-library`

    // Apply third-party plugins.
    id("org.openapi.generator")
}

openApiGenerate {
    // For some reason using https://api.clearlydefined.io/schemas/swagger.yaml here does not work, see
    // https://github.com/clearlydefined/website/issues/804.
    val schemaUrl = "https://github.com/clearlydefined/service/raw/master/schemas/swagger.yaml"
    val schemaText = URL(schemaUrl).readText()
    val schemaFile = file("build/tmp/swagger.yml").apply {
        parentFile.mkdirs()
        writeText(schemaText)
    }

    generatorName.set("kotlin")
    inputSpec.set(schemaFile.path)
}
