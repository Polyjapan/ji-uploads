package services

import java.io.InputStreamReader
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path, Paths}
import java.util.{Comparator, Scanner}

import ch.japanimpact.api.uploads.uploads.Upload
import javax.inject.Inject
import play.api.Configuration
import play.api.libs.Files.TemporaryFile
import utils.RandomUtils


class FileHandlingService @Inject()(configuration: Configuration) {
  lazy val uploadPath = {
    val path = configuration.get[String]("uploads.localPath")
    if (path.endsWith("/")) path.dropRight(1) else path
  }
  
  lazy val uploadUrl = {
    val url = configuration.get[String]("uploads.remoteUrl")
    if (url.endsWith("/")) url.dropRight(1) else url
  }

  def getUrl(containerId: Int, localPath: String) = s"$uploadUrl/$containerId/$localPath"

  def setUrlInUpload(upload: Upload) = upload.copy(url = getUrl(upload.containerId, upload.url))

  def getMime(file: TemporaryFile): String = {
    val mime = Files.probeContentType(file.path)

    if (mime == null) {
      val p = new ProcessBuilder("/usr/bin/file", "-b", "--mime-type", file.path.toAbsolutePath.toString).start()
      val os = p.getInputStream
      p.waitFor()

      val reader = new Scanner(new InputStreamReader(os))
      val res = reader.nextLine()
      reader.close()
      res
    } else mime
  }

  def deleteContainer(container: Int) = {
    val dir = Paths.get(s"$uploadPath/$container")

    Files.walk(dir)
      .sorted(Comparator.reverseOrder[Path])
      .forEach((t: Path) => Files.delete(t))

    if (Files.exists(dir)) {
      println("ERR: could not delete directory")
    }
  }

  def saveFile(container: Int, file: TemporaryFile, ext: String): String = {
    val randomId = RandomUtils.randomString(32)
    val dot = if (ext.startsWith(".")) "" else "."
    val fileName: String = randomId + dot + ext
    val path = Paths.get(s"$uploadPath/$container/$fileName")

    println(" --> all good, storing the file under " + path)

    if (!Files.exists(path)) Files.createDirectories(path)

    file.moveTo(path, replace = true)

    Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-r--r--"))

    fileName
  }
}
