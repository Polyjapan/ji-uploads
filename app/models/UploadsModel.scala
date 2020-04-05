package models

import anorm.Macro.ColumnNaming
import anorm.{Macro, _}
import anorm.SqlParser._
import ch.japanimpact.api.uploads.uploads.Upload
import ch.japanimpact.auth.api.apitokens.Principal
import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UploadsModel @Inject()(dbApi: play.api.db.DBApi)(implicit ec: ExecutionContext) {

  private val db = dbApi database "default"

  private case class PrivateUpload(uploadId: Option[Int], containerId: Int, uploaderType: Option[String], uploaderId: Option[Int], filePath: String, mimeType: String, sizeBytes: Long) {
    def toUpload: Upload =
      Upload(uploadId.get, containerId, uploaderType.zip(uploaderId).map(pair => Principal.fromName(pair._1)(pair._2)), filePath, mimeType, sizeBytes)
  }

  private implicit val toStatement: ToParameterList[PrivateUpload] = Macro.toParameters[PrivateUpload]()
  private implicit val PrivateUploadParser: RowParser[PrivateUpload] = Macro.namedParser[PrivateUpload](ColumnNaming.SnakeCase)

  def createUpload(container: Int, uploader: Option[Principal], file: String, mime: String, size: Long): Future[Int] = Future(db.withConnection { implicit c =>
    val (ut, uid) = uploader.map(ppal => (ppal.name, ppal.id)).unzip
    val upload = PrivateUpload(None, container, ut, uid, file, mime, size)

    SqlUtils.insertOne("uploads", upload)
  })

  def deleteUpload(upload: Int): Future[Boolean] = Future(db.withConnection { implicit c =>
    SQL"DELETE FROM uploads WHERE upload_id = $upload"
      .execute()
  })


  def deleteUploads(containerId: Int, uploaderType: Option[String], uploaderId: Option[Int]): Future[Boolean] = Future(db.withConnection { implicit c =>
    SQL"DELETE FROM uploads WHERE container_id = $containerId AND uploader_type = $uploaderType AND uploader_id = $uploaderId"
      .execute()
  })

  def getUpload(upload: Int): Future[Option[Upload]] = Future(db.withConnection { implicit c =>
    SQL"SELECT * FROM uploads WHERE upload_id = $upload"
      .as(PrivateUploadParser.singleOpt)
      .map(_.toUpload)
  })

  def getUploads(containerId: Int): Future[List[Upload]] = Future(db.withConnection { implicit c =>
    SQL"SELECT * FROM uploads WHERE container_id = $containerId"
      .as(PrivateUploadParser.*)
      .map(_.toUpload)
  })

  def getUploads(appId: Int, containerName: String): Future[List[Upload]] = Future(db.withConnection { implicit c =>
    SQL"SELECT uploads.* FROM uploads JOIN containers c on uploads.container_id = c.container_id WHERE c.app_id = $appId AND c.container_name = $containerName"
      .as(PrivateUploadParser.*)
      .map(_.toUpload)
  })

  def getUploads(appId: Int, containerName: String, user: Option[Principal]): Future[List[Upload]] = Future(db.withConnection { implicit c =>
    val (ut, uid) = user.map(ppal => (ppal.name, ppal.id)).unzip

    SQL"SELECT uploads.* FROM uploads JOIN containers c on uploads.container_id = c.container_id WHERE c.app_id = $appId AND c.container_name = $containerName AND uploader_type = $ut AND uploader_id = $uid"
      .as(PrivateUploadParser.*)
      .map(_.toUpload)
  })


}
