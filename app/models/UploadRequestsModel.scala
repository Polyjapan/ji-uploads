package models

import java.sql.PreparedStatement

import anorm.Macro.ColumnNaming
import anorm.{Macro, _}
import anorm.SqlParser._
import ch.japanimpact.api.uploads.uploads.{ReplacementPolicy, Upload}
import ch.japanimpact.auth.api.apitokens.Principal
import javax.inject.{Inject, Singleton}
import utils.RandomUtils

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UploadRequestsModel @Inject()(dbApi: play.api.db.DBApi)(implicit ec: ExecutionContext) {
  private val db = dbApi database "default"


  private implicit val ReplacementPolicyColumn: Column[ReplacementPolicy.Value] =
    Column.columnToString.map {
      case "no_replace" => ReplacementPolicy.NoReplace
      case "replace_one" => ReplacementPolicy.ReplaceOne
      case "replace_all" => ReplacementPolicy.ReplaceAll
    }

  private implicit val ReplacementPolicyStatement: ToStatement[ReplacementPolicy.Value] = (s: PreparedStatement, index: Int, v: ReplacementPolicy.Value) =>
    s.setString(index, v match {
      case ReplacementPolicy.NoReplace => "no_replace"
      case ReplacementPolicy.ReplaceOne => "replace_one"
      case ReplacementPolicy.ReplaceAll => "replace_all"
    })

  // Note: it's intentional not to store the app who requested the upload, so that apps can forward those between them if they want
  case class UploadRequest(requestId: Option[Int], ticket: String, containerId: Int,
                           uploaderType: Option[String], uploaderId: Option[Int], replacementPolicy: ReplacementPolicy.Value,
                           replaceUpload: Option[Int], uploadId: Option[Int])

  private implicit val toStatement: ToParameterList[UploadRequest] = Macro.toParameters[UploadRequest]()
  private implicit val UploadRequestParser: RowParser[UploadRequest] = Macro.namedParser[UploadRequest](ColumnNaming.SnakeCase)

  def createRequest(container: Int, uploader: Option[Principal], replacement: ReplacementPolicy.Value, replaceId: Option[Int]) = Future(db.withConnection { implicit c =>
    val token = RandomUtils.randomString(48)
    val (ut, uid) = uploader.map(ppal => (ppal.name, ppal.id)).unzip
    val upload = UploadRequest(None, token, container, ut, uid, replacement, replaceId, None)

    SqlUtils.insertOne("upload_requests", upload)

    token
  })

  def getRequest(ticket: String) = Future(db.withConnection { implicit c =>
    SQL"SELECT * FROM upload_requests WHERE request_ticket = $ticket AND expires_at > CURRENT_TIMESTAMP"
      .as(UploadRequestParser.singleOpt)
  })

  def setUploadId(request: Int, uploadId: Int) = Future(db.withConnection { implicit c =>
    SQL"UPDATE upload_requests SET upload_id = $uploadId WHERE request_id = $request"
      .executeUpdate()
  })

  def getUploadIdContainerAndAppId(ticket: String) = Future(db.withConnection { implicit c =>
    SQL"SELECT upload_id, container_name, app_id FROM upload_requests JOIN containers c on upload_requests.container_id = c.container_id WHERE request_ticket = $ticket"
      .as((int("upload_id").? ~ str("container_name")~int("app_id")).map { case a ~ b ~ c=> (a, b, c) }.singleOpt)
  })

  def deleteRequest(ticket: String): Future[Boolean] = Future(db.withConnection { implicit c =>
    SQL"DELETE FROM upload_requests WHERE request_ticket = $ticket"
      .execute()
  })


}
