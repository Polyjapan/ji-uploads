package models

import java.sql.{Connection, SQLException}

import anorm._
import anorm.SqlParser._
import ch.japanimpact.api.uploads.uploads.Container
import ch.japanimpact.api.uploads.uploads.SimpleContainer
import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class ContainersModel @Inject()(dbApi: play.api.db.DBApi)(implicit ec: ExecutionContext) {
  private val db = dbApi database "default"



  private val ContainerParser: RowParser[SimpleContainer] = Macro.namedParser[SimpleContainer](Macro.ColumnNaming.SnakeCase)

  def createContainer(app: Int, container: Container): Future[Option[Int]] = Future(db.withConnection { implicit c =>
    implicit val pl: ToParameterList[SimpleContainer] = Macro.toParameters[SimpleContainer]()

    Try {
      val id = SqlUtils.insertOne("containers", SimpleContainer(None, Some(app), container.containerName, container.maxFileSizeBytes))

      addMimeTypes(container.allowedTypes, id)

      id
    }.toOption
  })

  private def addMimeTypes(typesMap: Map[String, String], container: Int)(implicit c: Connection) = {
    val types = typesMap.map {
      case (mimeType, extension) =>
        Seq[NamedParameter]("id" -> container, "type" -> mimeType, "extension" -> extension)
    }.toSeq

    BatchSql("INSERT IGNORE INTO container_types(container_id, mime_type, extension) VALUES ({id}, {type}, {extension})", types.head, types.tail:_*)
      .execute().sum
  }

  def updateContainer(app: Int, name: String, container: Container): Future[Boolean] = Future(db.withConnection { implicit c =>
    val containerId = SQL"SELECT container_id FROM containers WHERE app_id = $app AND container_name = $name"
      .as(SqlParser.scalar[Int].singleOpt)

    containerId match {
      case Some(cid) =>
        SQL("UPDATE containers SET max_file_size_bytes = {maxFileSizeBytes}, container_name = {containerName} WHERE container_id = {id}")
          .on("id" -> cid, "containerName" -> name, "maxFileSizeBytes" -> container.maxFileSizeBytes)
          .executeUpdate()

        addMimeTypes(container.allowedTypes, cid)

        true
      case _ =>
        false
    }

  })

  def deleteContainer(container: Int): Future[Boolean] = Future(db.withConnection { implicit c =>
    try {
      SQL"DELETE FROM containers WHERE container_id = $container"
        .execute()

      true
    } catch {
      case _: SQLException => false
    }
  })

  private def enrichContainer(container: SimpleContainer)(implicit c: Connection): Container = {
    val id = container.containerId.get
    val types = SQL"SELECT mime_type, extension FROM container_types WHERE container_id = $id"
      .as((str("mime_type") ~ str("extension")).map { case a ~ b => a -> b }.*)

    container.toContainer(types)
  }

  def getContainer(app: Int, name: String): Future[Option[Container]] = Future(db.withConnection { implicit c =>
    SQL"SELECT * FROM containers WHERE app_id = $app AND container_name = $name"
      .as(ContainerParser.singleOpt)
      .map(enrichContainer)
  })

  def getContainer(id: Int): Future[Container] = Future(db.withConnection { implicit conn =>
    val container = SQL"SELECT * FROM containers WHERE container_id = $id"
      .as(ContainerParser.single)

    enrichContainer(container)
  })

  def getContainerId(app: Int, name: String): Future[Option[Int]] = Future(db.withConnection { implicit c =>
    SQL"SELECT container_id FROM containers WHERE app_id = $app AND container_name = $name"
      .as(scalar[Int].singleOpt)
  })

  def getContainers(app: Int): Future[List[SimpleContainer]] = Future(db.withConnection { implicit c =>
    SQL"SELECT * FROM containers WHERE app_id = $app"
      .as(ContainerParser.*)
  })


}
