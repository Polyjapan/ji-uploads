package controllers

import ch.japanimpact.api.uploads.uploads.{APIResponse, Container}
import ch.japanimpact.auth.api.apitokens.AuthorizationActions
import ch.japanimpact.auth.api.apitokens.AuthorizationActions._
import javax.inject.Inject
import models.ContainersModel
import play.api.libs.json.{JsNumber, Json}
import play.api.mvc.{AbstractController, Action, ControllerComponents}
import services.FileHandlingService
import utils.AuthUtils._

import scala.concurrent.{ExecutionContext, Future}

class ContainersController @Inject()(cc: ControllerComponents, containers: ContainersModel, authorize: AuthorizationActions, files: FileHandlingService)
                                    (implicit ec: ExecutionContext) extends AbstractController(cc) {


  private val namingRegex = "^[a-zA-Z0-9_-]{2,128}$".r

  def createContainer(app: Int): Action[Container] = authorize(OnlyApps, computeScopes("uploads/containers/:app/create", app)).async(parse.json[Container]) { req =>
    val container = req.body

    if (container.allowedTypes.isEmpty)
      Future.successful(BadRequest(Json.toJson(APIResponse("empty_allowed_types", "You must provide at least one allowed MIME type mapping"))))
    else if (!container.appId.forall(id => id == app))
      Future.successful(BadRequest(Json.toJson(APIResponse("invalid_app_id", "You must omit the app ID, or provide the same as in the URL parameter"))))
    else if (!namingRegex.matches(container.containerName))
      Future.successful(BadRequest(Json.toJson(APIResponse("invalid_name", "The container name must match the following regex: ^[a-zA-Z0-9_-]{2,128}$"))))
    else
      containers.createContainer(app, container).map {
        case Some(id) => Ok(Json.toJson(APIResponse(JsNumber(id))))
        case None => InternalServerError(Json.toJson(APIResponse("sql_error", "An error occured while inserting your container. A container with the same name may exist.")))
      }
  }

  def updateContainer(app: Int, name: String) = authorize(OnlyApps, computeScopes("uploads/containers/:app/update", app, name)).async(parse.json[Container]) { req =>
    val container = req.body

    if (!container.appId.forall(id => id == app))
      Future.successful(BadRequest(Json.toJson(APIResponse("invalid_app_id", "You must omit the app ID, or provide the same as in the URL parameter"))))
    else if (!namingRegex.matches(container.containerName))
      Future.successful(BadRequest(Json.toJson(APIResponse("invalid_name", "The container name must match the following regex: ^[a-zA-Z0-9_-]{2,128}$"))))
    else
      containers.updateContainer(app, name, container).map {
        case true => Ok(Json.toJson(APIResponse(success = true)))
        case false => NotFound(Json.toJson(APIResponse("not_found", "Cannot update this container. Are you sure it exists?")))
      }
  }

  def deleteContainer(app: Int, name: String) = authorize(OnlyApps, computeScopes("uploads/containers/:app/delete", app, name)).async { req =>
    containers.getContainerId(app, name).flatMap {
      case Some(cid) =>
        containers.deleteContainer(cid).map {
          case true =>
            files.deleteContainer(cid)
            Ok(Json.toJson(APIResponse(success = true)))
          case _ =>
            InternalServerError(Json.toJson(APIResponse("sql_error", "The DELETE request failed")))
        }
      case _ => Future.successful(NotFound(Json.toJson(APIResponse("not_found", "Cannot delete this container. Are you sure it exists?"))))
    }
  }

  def getContainer(app: Int, name: String) = authorize(OnlyApps, computeScopes("uploads/containers/:app/get", app, name)).async {
    containers.getContainer(app, name).map {
      case Some(container) => Ok(Json.toJson(container))
      case None => NotFound(Json.toJson(APIResponse("not_found", "This container doesn't exist in our files")))
    }
  }

  def getContainers(app: Int) = authorize(OnlyApps, computeScopes("uploads/containers/:app/get", app, "*")).async {
    containers.getContainers(app).map(list => Ok(Json.toJson(list)))
  }

  /*
  CREATE new container (POST /containers/:app/)
  UPDATE container (PUT /containers/:app/:name)
  GET container (GET /containers/:app/:name)
  DELETE container (DELETE /containers/:app/:name - should delete ALL files)
  GET containers (GET /containers/:app/)
   */

  /*
  Permissions :
    uploads/containers/self/create
    uploads/containers/<app>/create
    uploads/containers/self/update
    uploads/containers/<app>/update/<container name>
    uploads/containers/self/delete
    uploads/containers/<app>/delete
    uploads/containers/self/get
    uploads/containers/<app>/get/<container name>
   */
}
