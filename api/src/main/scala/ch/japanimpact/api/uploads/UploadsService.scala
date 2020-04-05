package ch.japanimpact.api.uploads

import ch.japanimpact.api.uploads.uploads.{APIResponse, Container, DelegationRequest, Upload, UploadStatusResponse}
import ch.japanimpact.auth.api.apitokens.{APITokensService, AppTokenRequest, Principal}
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


/**
 * @author Louis Vialar
 */
@Singleton
class UploadsService @Inject()(ws: WSClient, config: Configuration, tokens: APITokensService)(implicit ec: ExecutionContext) {
  private val apiBase = config.get[String]("uploads.baseUrl")

  private val token = new TokenHolder


  private def withToken[T](endpoint: String)(exec: WSRequest => Future[WSResponse])(map: JsValue => T): Future[Either[APIResponse, T]] =
    token()
      .map(token => ws.url(s"$apiBase/$endpoint").addHttpHeaders("Authorization" -> ("Bearer " + token)))
      .flatMap(r => mapping(r)(exec)(map))

  private def mapping[T](request: WSRequest)(exec: WSRequest => Future[WSResponse])(map: JsValue => T): Future[Either[APIResponse, T]] =
    exec(request)
      .map { response =>

        if (response.status == 200) {
          try {
            Right(map(response.json))
          } catch {
            case e: Exception =>
              e.printStackTrace()
              println(response.body)
              Left(APIResponse("unknown_error", "Unknown error with success response mapping"))
          }
        } else {
          try {
            Left(response.json.as[APIResponse])
          } catch {
            case e: Exception =>
              e.printStackTrace()
              Left(APIResponse("unknown_error", "Unknown error with code " + response.status))
          }
        }

      }

  private class TokenHolder {
    var token: String = _
    var exp: Long = _

    def apply(): Future[String] = {
      if (token != null && exp > System.currentTimeMillis() + 1000) Future.successful(token)
      else {
        tokens.getToken(AppTokenRequest(Set("uploads/*"), Set("uploads"), 48.hours.toSeconds))
          .map {
            case Right(token) =>
              this.token = token.token
              this.exp = System.currentTimeMillis() + token.duration * 1000 - 1000

              this.token
            case _ => throw new Exception("No token returned")
          }
      }
    }
  }

  class ContainersAPI(app: Int) {
    def createContainer(container: Container) = {
      withToken(s"/containers/$app")(_.post(Json.toJson(container)))(rep => {
        if (rep.as[APIResponse].success) new ContainerAPI(app, container.containerName)
        else throw new Exception()
      })
    }

    def apply(containerName: String) = new ContainerAPI(app, containerName)

    def / (containerName: String) = apply(containerName)

    def getContainers =
      withToken(s"/containers/$app")(_.get)(_.as[Seq[Container]])

    def getOrCreateContainer(container: Container) = {
      val api = apply(container.containerName)
      api.get.flatMap {
        case Left(err) =>
          createContainer(container)
        case Right(c) =>
          if (container.copy(containerId = None, appId = None) == c.copy(containerId = None, appId = None))
            Future.successful(Right(api))
          else {
            api.update(container).map(r => {
              if (r.success) Right(api)
              else Left(r)
            })
          }
      }
    }
  }

  class ContainerAPI(app: Int, var container: String) {
    /**
     * Update the container
     *
     * @param container the new container to set
     * @return
     */
    def update(container: Container) = {
      withToken(s"/containers/$app/$container")(_.put(Json.toJson(container)))(_.as[APIResponse])
        .map { case Left(r) => r case Right(r) => r }
        .map { r =>
          if (r.success && container.containerName != this.container)
            this.container = container.containerName
          r
        }
    }

    def get =
      withToken(s"/containers/$app/$container")(_.get)(_.as[uploads.Container])

    def delete =
      withToken(s"/containers/$app/$container")(_.delete())(_.as[APIResponse])
        .map { case Left(r) => r case Right(r) => r }

    def startUpload(request: uploads.UploadRequest) =
      withToken(s"/files/$app/$container")(_.post(Json.toJson(request))) {
        rep => UploadRequest(rep.as[APIResponse].data.get.as[String])
      }

    def listFiles =
      withToken(s"/files/$app/$container")(_.get)(_.as[Seq[Upload]])

    def listFilesBy(principal: Option[Principal]) =
      withToken(s"/files/$app/$container/${principal.map(_.toSubject).getOrElse("anon")}")(_.get)(_.as[Seq[Upload]])
  }

  case class UploadRequest(ticket: String) {
    def status =
      withToken(s"/uploadRequests/$ticket")(_.get)(_.as[APIResponse].data.get.as[UploadStatusResponse])

    def url = s"$apiBase/files/$ticket"
  }

  def containers(app: Int) = new ContainersAPI(app)

  def uploadRequest(ticket: String) = UploadRequest(ticket)

  def delegate(req: DelegationRequest) = {
    withToken(s"/delegation")(_.post(Json.toJson(req)))(_.as[APIResponse].data.get.as[String])
  }

}


