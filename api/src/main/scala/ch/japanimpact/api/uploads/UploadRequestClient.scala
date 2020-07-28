package ch.japanimpact.api.uploads

import ch.japanimpact.api.uploads.uploads._
import scala.concurrent.Future

/**
 * A client to access upload requests
 */
trait UploadRequestClient {
  /**
   * Get the status of this upload request
   *
   * @return an error or the status of the request
   */
  def status: Future[Either[APIResponse, UploadStatusResponse]]

  /**
   * Get the URL to which a file can be POSTed / PUT to finish this request
   *
   * @return the url where the file must be sent
   */
  def url: String
}
