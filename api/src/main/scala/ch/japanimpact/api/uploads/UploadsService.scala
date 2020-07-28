package ch.japanimpact.api.uploads

import scala.concurrent.Future
import com.google.inject.ImplementedBy
import ch.japanimpact.api.uploads.uploads._

/**
 * An injectable service that provides a client to the uploads APIs.<br>
 * This API is designed to handle chained calls (i.e. `service.containers(app) / "myContainer" ...`
 */
@ImplementedBy(classOf[HttpUploadsService])
trait UploadsService {
  /**
   * Access the containers belonging to an app
   *
   * @param app the app to access
   * @return a client to access the containers of these apps
   */
  def containers(app: Int): ContainersClient

  /**
   * Access an upload request
   *
   * @param ticket the ticket corresponding to the request
   * @return a client to access the request
   */
  def uploadRequest(ticket: String): UploadRequestClient

  /**
   * Create a delegation for an user
   *
   * @param req the delegation request
   * @return an error or the Session delegation token
   */
  def delegate(req: DelegationRequest): Future[Either[APIResponse, String]]
}
