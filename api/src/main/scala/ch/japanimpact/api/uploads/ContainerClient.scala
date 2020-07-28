package ch.japanimpact.api.uploads

import ch.japanimpact.api.uploads.uploads._
import ch.japanimpact.auth.api.apitokens

import scala.concurrent.Future

/**
 * A client to access to a single container
 */
trait ContainerClient {

  /**
   * Update the container
   *
   * @param container the new properties to set to the container
   * @return a response, indicating success or failure
   */
  def update(container: Container): Future[APIResponse]

  /**
   * Get the container configuration
   *
   * @return an error response or the container configuration
   */
  def get: Future[Either[APIResponse, Container]]

  /**
   * Delete the container
   *
   * @return a response indicating success or failure
   */
  def delete: Future[APIResponse]

  /**
   * Create an upload request
   *
   * @param request the request to create
   * @return an error or a [[UploadRequestClient]] to work with the successfully created request
   */
  def startUpload(request: UploadRequest): Future[Either[APIResponse, UploadRequestClient]]

  /**
   * List all the files in the container
   *
   * @return an error or the lisf of [[Upload]] from the container
   */
  def listFiles: Future[Either[APIResponse, Seq[Upload]]]

  /**
   * List all the files in the container that were uploaded by a given principal
   *
   * @param principal the principal to check
   * @return an error or the list of [[Upload]] from the principal in the container
   */
  def listFilesBy(principal: Option[apitokens.Principal]): Future[Either[APIResponse, Seq[Upload]]]

  /**
   * The ID of the app owning the container
   */
  val app: Int

  /**
   * Get the name of the container. It may change in response to a successful update.
   *
   * @return the current name of the container
   */
  def name: String
}
