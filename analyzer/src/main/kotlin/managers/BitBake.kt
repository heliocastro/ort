package com.here.ort.analyzer.managers

import ch.frankel.slf4k.debug
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.PackageManagerFactory
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.*
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.log
import com.here.ort.utils.textValueOrEmpty

import java.io.File

class BitBake(config: AnalyzerConfiguration) : PackageManager(config) {
    companion object : PackageManagerFactory<BitBake>(
            "https://github.com/openembedded/bitbake",
            "n/a",
            listOf("oe-init-build-env")
    ) {
        override fun create(config: AnalyzerConfiguration) = BitBake(config)
    }

    override fun command(workingDir: File) = "bitbake"

    override fun toString() = BitBake.toString()

    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        val workingDir = definitionFile.parentFile
        val bbPath = initBuildEnvironment(definitionFile)

        val scriptFile = File(bbPath, "find_packages.py")
        scriptFile.writeBytes(javaClass.classLoader.getResource("find_packages.py").readBytes())

        val scriptCmd = ProcessCapture(
                bbPath,
                mapOf("BBPATH" to bbPath.absolutePath),
                "python3",
                scriptFile.absolutePath,
                // FIXME: add a list of bitbake recipes to query in AnalyzerConfiguration
                "gdb"
        )

        val errors = mutableListOf<Error>()
        val packages = mutableMapOf<String, Package>()
        val packageReferences = mutableSetOf<PackageReference>()

        jsonMapper.readValue<JsonNode>(scriptCmd.requireSuccess().stdout()).forEach {
            parseDependency(it, packages, packageReferences, errors)
        }

        val project = Project(
                id = Identifier(toString(), "", "FIXME", "23.42"),
                definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                declaredLicenses = emptySet<String>().toSortedSet(),
                vcs = VcsInfo.EMPTY,
                vcsProcessed = processProjectVcs(workingDir),
                homepageUrl = "",
                scopes = sortedSetOf(Scope("default", packageReferences.toSortedSet()))
        )

        return ProjectAnalyzerResult(project, packages.values.map { it.toCuratedPackage() }.toSortedSet(), errors)
    }

    // TODO: error handling
    private fun parseDependency(node: JsonNode, packages: MutableMap<String, Package>,
                                scopeDependencies: MutableSet<PackageReference>,
                                errors: MutableList<Error>) {

        val name = node["name"].textValue()
        log.debug { "Parsing recipe '$name'." }

        val pkg = packages[name] ?: addPackage(name, node, packages)
        val transitiveDependencies = mutableSetOf<PackageReference>()

        node["dependencies"].forEach {
            parseDependency(it, packages, transitiveDependencies, errors)
        }

        scopeDependencies += PackageReference(pkg.id, transitiveDependencies.toSortedSet())
    }

    private fun addPackage(name: String, node: JsonNode, packages: MutableMap<String, Package>): Package {
        // TODO: Figure out what to do about patch files and lack of hashing.
        val srcUri = node["src_uri"].toList().let {
            if (it.isEmpty())
                ""
            else
                it.first().textValueOrEmpty()
        }
        val sourceArtifact = RemoteArtifact(url = srcUri, hash = "", hashAlgorithm = HashAlgorithm.UNKNOWN)

        val pkg = Package(
                id = Identifier(
                        provider = toString(),
                        namespace = "",
                        name = name,
                        version = node["version"].textValueOrEmpty()
                ),
                declaredLicenses = sortedSetOf(node["license"].textValueOrEmpty()),
                description = node["description"].textValueOrEmpty(),
                homepageUrl = node["homepage"].textValueOrEmpty(),
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = sourceArtifact,
                vcs = VcsInfo.EMPTY,
                vcsProcessed = VcsInfo.EMPTY
        )

        packages[name] = pkg
        return pkg
    }

    private fun initBuildEnvironment(definitionFile: File): File {
        val pwd = definitionFile.parentFile
        val buildDir = File(pwd, "build").absolutePath

        val bashCmd = ProcessCapture(
                pwd,
                "/bin/bash",
                "-c",
                "'set $buildDir && . ./${definitionFile.name} $buildDir'" // > /dev/null; echo \$BBPATH'"
        )

        return File(bashCmd.requireSuccess().stdout())
    }
}
