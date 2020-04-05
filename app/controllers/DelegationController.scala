package controllers

import java.time.Clock

import ch.japanimpact.api.uploads.uploads.{APIResponse, DelegationRequest}
import ch.japanimpact.auth.api.apitokens.AuthorizationActions.OnlyApps
import ch.japanimpact.auth.api.apitokens.{AuthorizationActions, Principal}
import javax.inject.Inject
import pdi.jwt.JwtPlayImplicits
import play.api.Configuration
import play.api.libs.json.{JsString, Json}
import play.api.mvc.{AbstractController, ControllerComponents}
import services.JWTService
import utils.AuthUtils.computeScope

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._


class DelegationController @Inject()(cc: ControllerComponents, jwt: JWTService, authorize: AuthorizationActions)(implicit ec: ExecutionContext, conf: Configuration, clock: Clock) extends AbstractController(cc) with JwtPlayImplicits {


  def getTicketForUser = authorize(OnlyApps)(parse.json[DelegationRequest]) { req =>
    val request = req.body
    val user = req.principal

    if (request.containers.isEmpty) {
      BadRequest(Json.toJson(APIResponse("empty_request", "You didn't provide any requested container name for the token.")))
    } else if (! request.containers.forall(container => user.hasScope(computeScope("uploads/files/:app/list", request.appId, container)(user.principal)))) {
      Forbidden(Json.toJson(APIResponse("forbidden", "You don't have access to all containers of this request.")))
    } else {
      val token = jwt.generateAccessToken(request.principal, request.appId, request.containers, 24.hours)

      Ok(Json.toJson(APIResponse(JsString(token.serialize)))).withJwtSession(token)
    }

  }


}
