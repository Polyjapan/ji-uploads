package ch.japanimpact.api.uploads

import ch.japanimpact.api.uploads.uploads.{APIResponse, Container, DelegationRequest, Upload, UploadRequest, UploadStatusResponse}
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
class HttpUploadsService @Inject()(ws: WSClient, config: Configuration, tokens: APITokensService)(implicit ec: ExecutionContext) extends UploadsService {
  private val apiBase = {
    var url = config.get[String]("uploads.baseUrl")
    while (url.endsWith("/")) url = url.dropRight(1)
    url
  }

  private val token = new TokenHolder


  private def withToken[T](endpoint: String)(exec: WSRequest => Future[WSResponse])(map: JsValue => T): Future[Either[APIResponse, T]] =
    token()
      .map(token => ws.url(s"$apiBase/${endpoint.dropWhile(_ == '/')}").addHttpHeaders("Authorization" -> ("Bearer " + token)))
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

  class HttpContainersClient(override val app: Int) extends ContainersClient {
    override def createContainer(container: Container): Future[Either[APIResponse, ContainerClient]] = {
q      withToken(s"containers/$app")(_.post(Json.toJson(container)))(rep => {
        if (rep.as[APIResponse].success) new HttpContainerClient(app, container.containerName)
        else throw new Exception()
      })
    }

    override def apply(containerName: String): ContainerClient = new HttpContainerClient(app, containerName)

    override def / (containerName: String): ContainerClient = apply(containerName)

    override def getContainers: Future[Either[APIResponse, Seq[Container]]] =
      withToken(s"containers/$app")(_.get)(_.as[Seq[Container]])

    override def getOrCreateContainer(container: Container): Future[Either[APIResponse, ContainerClient]] = {
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

  class HttpContainerClient(override val app: Int, var container: String) extends ContainerClient {
    override def name = container
    /**
     * Update the container
     *
     * @param container the new container to set
     * @return
     */
    override def update(container: Container): Future[APIResponse] = {
      withToken(s"containers/$app/${container.containerName}")(_.put(Json.toJson(container)))(_.as[APIResponse])
        .map { case Left(r) => r case Right(r) => r }
        .map { r =>
          if (r.success && container.containerName != this.container)
            this.container = container.containerName
          r
        }
    }

    override def get: Future[Either[APIResponse, Container]] =
      withToken(s"containers/$app/$container")(_.get)(_.as[Container])

    override def delete: Future[APIResponse] =
      withToken(s"containers/$app/$container")(_.delete())(_.as[APIResponse])
        .map { case Left(r) => r case Right(r) => r }

    override def startUpload(request: UploadRequest): Future[Either[APIResponse, UploadRequestClient]] =
      withToken(s"files/$app/$container")(_.post(Json.toJson(request))) {
        rep => HttpUploadRequest(rep.as[APIResponse].data.get.as[String])
      }

    override def listFiles: Future[Either[APIResponse, Seq[Upload]]] =
      withToken(s"files/$app/$container")(_.get)(_.as[Seq[Upload]])

    override def listFilesBy(principal: Option[Principal]): Future[Either[APIResponse, Seq[Upload]]] =
      withToken(s"files/$app/$container/${principal.map(_.toSubject).getOrElse("anon")}")(_.get)(_.as[Seq[Upload]])
  }

  case class HttpUploadRequest(ticket: String) extends UploadRequestClient {
    override def status: Future[Either[APIResponse, UploadStatusResponse]] =
      withToken(s"uploadRequests/$ticket")(_.get)(_.as[APIResponse].data.get.as[UploadStatusResponse])

    override def url: String = s"$apiBase/files/$ticket"
  }

  override def containers(app: Int): ContainersClient = new HttpContainersClient(app)

  override def uploadRequest(ticket: String): UploadRequestClient = HttpUploadRequest(ticket)

  override def delegate(req: DelegationRequest): Future[Either[APIResponse, String]] = {
    withToken(s"delegation")(_.post(Json.toJson(req)))(_.as[APIResponse].data.get.as[String])
  }

}


