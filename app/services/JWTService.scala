package services

import java.time.Clock

import ch.japanimpact.auth.api.apitokens.Principal
import javax.inject.{Inject, Singleton}
import pdi.jwt.{JwtClaim, JwtSession}
import play.api.Configuration

import scala.concurrent.duration._

@Singleton
class JWTService @Inject()(implicit conf: Configuration, clock: Clock) {
  def generateAccessToken(principal: Option[Principal], appId: Int, containers: Set[String], duration: Duration): JwtSession = {
    val time = if (duration.toHours > 48) 48.hours else duration

    val claim = JwtClaim()
      .about(principal.map(_.toSubject).getOrElse("anon"))
      .by(s"$appId")
      .to(containers)
      .issuedNow
      .expiresIn(time.toSeconds)

    JwtSession(claim)
  }

  def isSessionValid(session: JwtSession, principal: Option[Principal], appId: Int, container: String): Boolean = {
    val subject = principal.map(_.toSubject).getOrElse("anon")

    session.claim.isValid(s"$appId", container) && session.claim.subject.contains(subject)
  }

}
