package controllers

import ch.japanimpact.api.uploads.uploads.{APIResponse, ReplacementPolicy, UploadRequest, UploadStatusResponse}
import ch.japanimpact.auth.api.apitokens.AuthorizationActions
import ch.japanimpact.auth.api.apitokens.AuthorizationActions.OnlyApps
import javax.inject.Inject
import models.{ContainersModel, UploadRequestsModel, UploadsModel}
import play.api.libs.json.{JsString, Json}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import services.FileHandlingService
import utils.AuthUtils
import utils.AuthUtils.computeScopes

import scala.concurrent.{ExecutionContext, Future}


class UploadRequestsController @Inject()(cc: ControllerComponents, uploads: UploadsModel, uploadRequests: UploadRequestsModel,
                                         files: FileHandlingService, authorize: AuthorizationActions, containers: ContainersModel)(implicit ec: ExecutionContext) extends AbstractController(cc) {


  /*
    GET /uploadRequests/:ticket
    POST /files/:app/:container -> start an upload (from the app)
   */

  def uploadRequestStatus(ticket: String): Action[AnyContent] = authorize(OnlyApps).async { req =>
    val app = req.principal
    uploadRequests.getUploadIdContainerAndAppId(ticket).flatMap {
      case Some((uploadId, container, appId)) =>
        if (!app.hasScope(AuthUtils.computeScope("uploads/requests/:app/status", appId, container)(app.principal))) {
          Future.successful(NotFound(Json.toJson(APIResponse("not_found", "The given upload request doesn't exist."))))
        } else if (uploadId.isEmpty) {
          Future.successful(Ok(Json.toJson(UploadStatusResponse(false, None))))
        } else {
          // Louis: Deleting requests makes this endpoint non idempotent, and I don't think it's useful in any way
          // Louis²: this was actually probably implemented to avoid the ability for users to reuse previous requests - don't yet know the limitations for this.
          // This current system allows a user to upload a file in a container A and then return it to an app that "believed" that the file was uploaded into a container B
          // We should definitely implement a procedure so that Uploads directly talks to the requesting server when an upload is complete.
          // uploadRequests.deleteRequest(ticket).flatMap(_ =>
            uploads.getUpload(uploadId.get) map {
              case Some(upload) =>
                Ok(Json.toJson(UploadStatusResponse(true, Some(files.setUrlInUpload(upload)))))
              case None =>
                Ok(Json.toJson(UploadStatusResponse(false, None)))
            }
          // )
        }
      case None => Future.successful(NotFound(Json.toJson(APIResponse("not_found", "The given upload request doesn't exist."))))
    }
  }

  def startUpload(app: Int, container: String) = authorize(OnlyApps, computeScopes("uploads/requests/:app/new", app, container)).async(parse.json[UploadRequest]) { req =>
    val request = req.body

    // Find the container
    containers.getContainerId(app, container).flatMap {
      case Some(containerId) =>
        // Good, now check the id
        val canReplaceThat =
          if (request.replaceId.isEmpty) Future.successful(true)
          else uploads.getUpload(request.replaceId.get).map(_.exists(u => u.containerId == containerId))

        val replacementPolicy =
          if (request.replaceId.isDefined) ReplacementPolicy.ReplaceOne
          else request.replacement

        canReplaceThat.flatMap {
          case true =>
            uploadRequests.createRequest(containerId, request.uploader, replacementPolicy, request.replaceId)
              .map(ticket => Ok(Json.toJson(APIResponse(JsString(ticket)))))
          case false =>
            Future.successful(Forbidden(Json.toJson(APIResponse("illegal_replacement", "You cannot replace an upload that doesn't exist or that is in an other collection."))))
        }
      case None => Future.successful(NotFound(Json.toJson(APIResponse("not_found", "The given container doesn't exist."))))
    }
  }

  /*
  Permissions :

    (start an upload)
    uploads/containers/self/upload
    uploads/containers/<app id>/upload/container

    (get the status of an upload)
    uploads/containers/self/uploadstatus
    uploads/containers/:app/uploadstatus/container
   */

}
