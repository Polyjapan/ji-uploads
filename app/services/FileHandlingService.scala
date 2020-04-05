package services

import java.io.InputStreamReader
import java.nio.file.{Files, Paths}
import java.util.Scanner

import ch.japanimpact.api.uploads.uploads.Upload
import javax.inject.Inject
import play.api.Configuration
import play.api.libs.Files.TemporaryFile
import utils.RandomUtils


class FileHandlingService @Inject()(configuration: Configuration) {
  lazy val uploadPath = configuration.get[String]("uploads.localPath")
  lazy val uploadUrl = configuration.get[String]("uploads.remoteUrl")

  def getUrl(localPath: String) = uploadUrl + "/" + localPath

  def setUrlInUpload(upload: Upload) = upload.copy(url = getUrl(upload.url))

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

  def saveFile(file: TemporaryFile, ext: String): String = {
    val randomId = RandomUtils.randomString(64)
    val fileName: String = randomId + "." + ext
    println(" --> all good, storing the image under " + fileName)
    file.moveTo(Paths.get(uploadPath + fileName), replace = true)

    fileName
  }
}
