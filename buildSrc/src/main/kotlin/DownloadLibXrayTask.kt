import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class DownloadLibXrayTask : DefaultTask() {

    @get:Input
    abstract val libXrayVersion: Property<String>

    @get:Input
    abstract val libXraySha256: Property<String>

    @get:OutputFile
    abstract val libraryFile: RegularFileProperty

    @get:OutputFile
    abstract val versionFile: RegularFileProperty

    @TaskAction
    fun download() {
        val version = libXrayVersion.get()
        val expectedSha256 = libXraySha256.get()

        val library = libraryFile.get().asFile
        val versionMarker = versionFile.get().asFile

        library.parentFile.mkdirs()

        val downloadUrl =
            "https://github.com/XTLS/libXray/releases/download/v$version/libxray-android.zip"

        val archiveFile = temporaryDir.resolve("libxray-android.zip")
        val temporaryLibrary = temporaryDir.resolve("libXray.aar")

        logger.lifecycle("Загрузка libXray $version...")

        try {
            val connection = URI(downloadUrl).toURL().openConnection()
            connection.connectTimeout = 30000
            connection.readTimeout = 120000

            connection.getInputStream().use { input ->
                archiveFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            logger.lifecycle("Проверка контрольной суммы SHA-256...")

            val digest = MessageDigest.getInstance("SHA-256")

            archiveFile.inputStream().use { input ->
                val buffer = ByteArray(8192)

                while (true) {
                    val bytesRead = input.read(buffer)

                    if (bytesRead == -1) break

                    digest.update(buffer, 0, bytesRead)
                }
            }

            val actualSha256 = digest.digest()
                .joinToString("") { "%02x".format(it) }

            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                throw GradleException(
                    "Контрольная сумма libXray не совпадает.\n" +
                            "Ожидаемая: $expectedSha256\n" +
                            "Полученная: $actualSha256"
                )
            }

            logger.lifecycle("Извлечение библиотеки libXray...")

            ZipFile(archiveFile).use { zip ->
                val entry = zip.entries().asSequence()
                    .firstOrNull {
                        !it.isDirectory &&
                                it.name.substringAfterLast('/') == "libXray.aar"
                    }
                    ?: throw GradleException(
                        "Файл libXray.aar не найден в архиве"
                    )

                zip.getInputStream(entry).use { input ->
                    temporaryLibrary.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            temporaryLibrary.copyTo(library, overwrite = true)
            versionMarker.writeText(version)

            logger.lifecycle("Библиотека libXray $version успешно установлена")
        } finally {
            archiveFile.delete()
            temporaryLibrary.delete()
        }
    }
}