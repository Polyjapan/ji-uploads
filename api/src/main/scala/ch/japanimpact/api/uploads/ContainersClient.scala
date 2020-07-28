package ch.japanimpact.api.uploads

import scala.concurrent.Future
import ch.japanimpact.api.uploads.uploads._

/**
 * A client to access the containers of an app
 */
trait ContainersClient {
  /**
   * The app owning the containers
   */
  val app: Int

  /**
   * Create a new container owned by the app
   *
   * @param container the container to create
   * @return an error or a client to access the newly created container
   */
  def createContainer(container: Container): Future[Either[APIResponse, ContainerClient]]

  /**
   * Get a client to access a container. Existance of the container is NOT checked during this call.
   *
   * @param containerName the name of the container to access
   * @return a client to that container
   */
  def apply(containerName: String): ContainerClient

  /**
   * Get a client to access a container. Existance of the container is NOT checked during this call.<br>
   * Equivalent to [[ContainersClient.apply(String)]]
   *
   * @param containerName the name of the container to access
   * @return a client to that container
   */
  def /(containerName: String): ContainerClient

  /**
   * Get a list of all the containers of this app
   *
   * @return an error or the list of containers
   */
  def getContainers: Future[Either[APIResponse, Seq[Container]]]

  /**
   * Access a container, creating it first if it doesn't exist.<br>
   * If it exists but is different from the passed container, it will be updated to match.
   *
   * @param container the container to get
   * @return an error or a client to the container
   */
  def getOrCreateContainer(container: Container): Future[Either[APIResponse, ContainerClient]]
}
