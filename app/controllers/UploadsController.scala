package controllers

import java.nio.file.Files
import java.time.Clock

import ch.japanimpact.api.uploads.uploads.{APIResponse, ReplacementPolicy}
import ch.japanimpact.auth.api.apitokens.{AuthorizationActions, Principal}
import javax.inject.Inject
import models.{ContainersModel, UploadRequestsModel, UploadsModel}
import pdi.jwt.JwtPlayImplicits
import play.api.Configuration
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, ControllerComponents}
import services.{FileHandlingService, JWTService}
import utils.AuthUtils

import scala.concurrent.{ExecutionContext, Future}


class UploadsController @Inject()(cc: ControllerComponents, uploads: UploadsModel, uploadRequests: UploadRequestsModel,
                                  containers: ContainersModel, jwt: JWTService,
                                  files: FileHandlingService, authorize: AuthorizationActions)
                                 (implicit ec: ExecutionContext, clock: Clock, conf: Configuration) extends AbstractController(cc) with JwtPlayImplicits {


  /*
    PUT /files/:ticket -> send the file (from the client)

    GET /files/:app/:container
    GET /files/:app/:container/:user <!-- always allowed for an user / allowed for others -->
   */
  def uploadFileFormData(ticket: String) = authorize.optional().async(parse.multipartFormData) { rq =>
    rq.body.files.headOption match {
      case Some(file) => doUploadFile(ticket, file.ref, rq)
      case None => Future.successful(BadRequest(Json.toJson(APIResponse("no_file", "The request doesn't contain any file."))))
    }
  }

  def uploadFile(ticket: String) = authorize.optional().async(parse.temporaryFile) { rq =>
    doUploadFile(ticket, rq.body, rq)
  }

  private def doUploadFile(ticket: String, file: TemporaryFile, rq: authorize.OptionalAuthorizedRequest[_]) = {
    val user = rq.principal

    uploadRequests.getRequest(ticket) flatMap {
      case Some(request) if request.uploadId.isEmpty =>
        // Check upload right
        val uploader = (request.uploaderType zip request.uploaderId).map { case (tpe, id) => Principal.fromName(tpe)(id) }

        val authorized = (uploader, user) match {
          case (Some(uploader), Some(user)) => uploader == user.principal
          case (None, _) => true
          case _ => false
        }

        if (!authorized) Future.successful(Forbidden(Json.toJson(APIResponse("forbidden", "The upload ticket was created for an other principal."))))
        else {
          containers.getContainer(request.containerId).flatMap { container =>
            // We have permission, let's move the file

            val mime = files.getMime(file)
            val size = Files.size(file.path)


            if (!container.allowedTypes.keySet(mime)) {
              println("Error: invalid mime " + mime + ". Returning 400.")
              Future.successful(BadRequest(Json.toJson(APIResponse("invalid_mime", "This file type is not allowed. Allowed types: " + container.allowedTypes.keySet))))
            } else if (size > container.maxFileSizeBytes) {
              println("Error: invalid file size " + size + ". Returning 400.")
              Future.successful(BadRequest(Json.toJson(APIResponse("file_too_big", "The file is too big. Maximal size: " + container.maxFileSizeBytes))))
            } else {
              val fileName = files.saveFile(container.containerId.get, file, container.allowedTypes(mime))

              val replace = {
                // TODO: delete these files as well
                if (request.replacementPolicy == ReplacementPolicy.ReplaceOne && request.replaceUpload.nonEmpty)
                  uploads.deleteUpload(request.replaceUpload.get).map(_ => ())
                else if (request.replacementPolicy == ReplacementPolicy.ReplaceAll)
                  uploads.deleteUploads(request.containerId, request.uploaderType, request.uploaderId).map(_ => ())
                else Future.successful(())
              }

              replace.flatMap(_ => uploads.createUpload(request.containerId, user.map(_.principal), fileName, mime, size).flatMap(uploadId => {
                uploadRequests.setUploadId(request.requestId.get, uploadId).map { _ =>
                  Ok(Json.toJson(APIResponse(Json.obj(
                    "ticket" -> ticket, "url" -> files.getUrl(container.containerId.get, fileName)
                  ))))
                }
              }))
            }
          }
        }
      case Some(_) =>
        Future.successful(NotFound(Json.toJson(APIResponse("already_used", "The upload ticket was already used."))))
      case _ =>
        Future.successful(NotFound(Json.toJson(APIResponse("not_found", "The upload ticket was not found."))))
    }
  }

  import authorize.OptionalAuthorizedRequest

  private def isAuthorized(app: Int, container: String, req: OptionalAuthorizedRequest[_]): Boolean = {
    val scope: Principal => String = AuthUtils.computeScope("uploads/files/:app/list", app, container)

    import ch.japanimpact.auth.api.apitokens.App

    if (req.principal.exists(p => p.principal.isInstanceOf[App] && p.hasScope(scope(p.principal)))) true
    else {
      // Principal not allowed or not present: check if the session works
      jwt.isSessionValid(req.jwtSession, req.principal.map(_.principal), app, container)
    }
  }


  def getFiles(app: Int, container: String) = authorize.optional().async { implicit req =>
    if (isAuthorized(app, container, req)) {
      uploads.getUploads(app, container)
        .map(uploads => uploads.map(files.setUrlInUpload))
        .map(uploads => Ok(Json.toJson(uploads)))
    } else {
      Future.successful(Forbidden)
    }
  }

  def getUserFiles(app: Int, container: String, principal: String) = authorize.optional().async { implicit req =>
    val isSameUser = req.principal.map(_.principal.toSubject).contains(principal)

    if (isSameUser || isAuthorized(app, container, req)) {
      val ppal: Option[Principal] = Principal.fromSubject(principal)

      uploads.getUploads(app, container, ppal)
        .map(uploads => uploads.map(files.setUrlInUpload))
        .map(uploads => Ok(Json.toJson(uploads)))
    } else {
      Future.successful(Forbidden)
    }
  }

  /*
  Permissions :


    (sending the file is permless :D)

    (get the files in a container)
    uploads/containers/self/list
    uploads/containers/<app id>/list/<container name>
    each user always has the permission to see their uploads
   */

}