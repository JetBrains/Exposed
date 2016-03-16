package ru.yandex.qatools.embed.postgresql

import de.flapdoodle.embed.process.config.IRuntimeConfig
import de.flapdoodle.embed.process.distribution.Distribution
import de.flapdoodle.embed.process.extract.IExtractedFileSet
import de.flapdoodle.embed.process.io.file.Files
import de.flapdoodle.embed.process.runtime.Executable
import de.flapdoodle.embed.process.runtime.Starter
import org.jetbrains.exposed.sql.exposedLogger
import ru.yandex.qatools.embed.postgresql.config.PostgresConfig
import ru.yandex.qatools.embed.postgresql.config.RuntimeConfigBuilder
import ru.yandex.qatools.embed.postgresql.ext.PostgresArtifactStore
import ru.yandex.qatools.embed.postgresql.ext.SubdirTempDir
import java.util.*

/** Remove this when yandex-qatools/postgresql-embedded supports startup/shutdown on Windows under Administrator permissions */
object PgSQLServiceUtils {

    internal fun initDB(config: PostgresConfig): PgExecutable {
        return PgStarter(RuntimeConfigBuilder().defaults(Command.PgCtl).build()).run {
            prepare(config.withArgs("init")).start().waitFor()
            config.args().clear()
            prepare(config.withArgs("-o", "-h ${config.net().host()} -p ${config.net().port()}", "-w", "start"))
        }
    }

    private class PgStarter(config: IRuntimeConfig) : Starter<PostgresConfig, PgExecutable, PgProcess>(config) {
        override fun newExecutable(config: PostgresConfig, distribution: Distribution, runtime: IRuntimeConfig, exe: IExtractedFileSet): PgExecutable? {
            return PgExecutable(distribution, config, runtime, exe)
        }

    }

    internal class PgExecutable(distribution: Distribution, val config: PostgresConfig, runtime: IRuntimeConfig, exe: IExtractedFileSet)
            : Executable<PostgresConfig, PgProcess>(distribution, config, runtime, exe) {
        override fun start(distribution: Distribution, config: PostgresConfig, runtime: IRuntimeConfig): PgProcess? {
            return PgProcess(distribution, config, runtime, this)
        }

        override fun stop() {
            if (!config.args().contains("init")) {
                super.stop()
            }
        }
    }

    internal class PgProcess(distribution: Distribution, config: PostgresConfig, val runtime: IRuntimeConfig, exe: PgExecutable)
        : AbstractPGProcess<PgExecutable, PgProcess>(distribution, config, runtime, exe) {

        private var stopped = false

        override fun getCommandLine(distribution: Distribution, config: PostgresConfig, exe: IExtractedFileSet): MutableList<String> {

            if (config.credentials() != null && config.args().contains("init")) {
                val pwFile = createTempFile(directory = SubdirTempDir.defaultInstance().asFile(), prefix = "pwfile" , suffix = UUID.randomUUID().toString())
                Files.write(config.credentials().password(), pwFile)
                config.withArgs("-o", "-A password -U ${config.credentials().username()} --pwfile=${pwFile.absolutePath}")
            }

            return (listOf(exe.executable().absolutePath, "-D", config.storage().dbDir().absolutePath) + config.args()).toMutableList()
        }

        // Copy-paste from PostgresProcess to graceful shutdown postgresql on Windows

        override fun stopInternal() {
            synchronized (this) {
                if (!stopped) {
                    stopped = true
                    exposedLogger.info("trying to stop postgresql")
                    if (!sendStopToPostgresqlInstance()) {
                        exposedLogger.warn("could not stop postgresql with command, try next")
                        if (!sendKillToProcess()) {
                            exposedLogger.warn("could not stop postgresql, try next")
                            if (!sendTermToProcess()) {
                                exposedLogger.warn("could not stop postgresql, try next")
                                if (!tryKillToProcess()) {
                                    exposedLogger.warn("could not stop postgresql the second time, try one last thing")
                                }
                            }
                        }
                    }
                }
                deleteTempFiles()
            }
        }

        protected fun deleteTempFiles() {
            val storage = config.storage()
            if (storage.dbDir() != null && storage.isTmpDir && !Files.forceDelete(storage.dbDir())) {
                exposedLogger.warn("Could not delete temp db dir: " + storage.dbDir())
            }
        }

        protected fun sendStopToPostgresqlInstance(): Boolean {
            val result = PostgresProcess.shutdownPostgres(config)
            if (runtime.artifactStore is PostgresArtifactStore) {
                val tempDir = (runtime.artifactStore as PostgresArtifactStore).tempDir
                if (tempDir != null && tempDir.asFile() != null) {
                    exposedLogger.info("Cleaning up after the embedded process (removing ${tempDir.asFile().absolutePath})...")
                    Files.forceDelete(tempDir.asFile())
                }
            }
            return result
        }
    }

}