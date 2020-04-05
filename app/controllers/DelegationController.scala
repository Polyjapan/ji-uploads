package controllers

import java.time.Clock

import ch.japanimpact.api.uploads.uploads.APIResponse
import ch.japanimpact.auth.api.apitokens.AuthorizationActions.OnlyApps
import ch.japanimpact.auth.api.apitokens.{AuthorizationActions, Principal}
import javax.inject.Inject
import pdi.jwt.JwtPlayImplicits
import play.api.Configuration
import play.api.libs.json.{JsString, Json}
import play.api.mvc.{AbstractController, ControllerComponents}
import services.JWTService
import utils.AuthUtils.computeScopes

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._


class DelegationController @Inject()(cc: ControllerComponents, jwt: JWTService, authorize: AuthorizationActions)(implicit ec: ExecutionContext, conf: Configuration, clock: Clock) extends AbstractController(cc) with JwtPlayImplicits {


  def getTicketForUser(app: Int, container: String, user: Option[String]) = authorize(OnlyApps, computeScopes("uploads/containers/:app/list", app, container)) { req =>

    val token = jwt.generateAccessToken(user.flatMap(Principal.fromSubject), app, Set(container), 24.hours)

    Ok(Json.toJson(APIResponse(JsString(token.serialize)))).withJwtSession(token)
  }


}
