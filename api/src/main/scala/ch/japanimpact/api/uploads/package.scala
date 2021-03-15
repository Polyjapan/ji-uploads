package ch.japanimpact.api.uploads

import ch.japanimpact.auth.api.apitokens._
import play.api.libs.json._

package object uploads {

  /**
   * Describes a container in the system with its list of allowed MIME types
   *
   * @param containerId      the ID of the container (not required when creating)
   * @param appId            the ID of the owner app (not required when creating, if provided it MUST be the same as the ID in the request)
   * @param containerName    the name of the container
   * @param maxFileSizeBytes the maximum file size, in bytes
   * @param allowedTypes     a map of allowed mime types along with the file extension they will have once uploaded
   *                         (example: image/png -> .png)
   */
  case class Container(containerId: Option[Int], appId: Option[Int], containerName: String, maxFileSizeBytes: Long,
                       allowedTypes: Map[String, String])


  /**
   * Describes a container in the system
   *
   * @param containerId      the ID of the container (not required when creating)
   * @param appId            the ID of the owner app (not required when creating, if provided it MUST be the same as the app calling the endpoint)
   * @param containerName    the name of the container
   * @param maxFileSizeBytes the maximum file size, in bytes
   */
  case class SimpleContainer(containerId: Option[Int], appId: Option[Int], containerName: String,
                             maxFileSizeBytes: Long) {

    def toContainer(map: Seq[(String, String)]): Container =
      Container(containerId, appId, containerName, maxFileSizeBytes, map.toMap)
  }

  /**
   * An uploaded file in the system
   *
   * @param uploadId    the ID of the uploaded file
   * @param containerId the ID of the container containing the file
   * @param uploader    the principal that uploaded the file (empty if it's an anonymous user)
   * @param url         the URL where the file can be retrieved
   * @param mimeType    the MIME type of the file
   * @param sizeBytes   the size of the file, in bytes
   */
  case class Upload(uploadId: Int, containerId: Int, uploader: Option[Principal], url: String, mimeType: String,
                    sizeBytes: Long)


  object ReplacementPolicy extends Enumeration {
    type ReplacementPolicy = Value

    /**
     * Don't replace any file when uploading
     */
    val NoReplace = Value

    /**
     * Replace a single file (whose ID is provided in the request)
     */
    val ReplaceOne = Value

    /**
     * Replace all the files sent by the same uploader
     */
    val ReplaceAll = Value
  }

  case class UploadStatusResponse(uploaded: Boolean, upload: Option[Upload])

  /**
   * A request to upload a new file
   *
   * @param uploader       an optional Principal. If specified, only that principal will be able to upload the file
   * @param replacement    the [[ReplacementPolicy]] to use (see [[ReplacementPolicy]] for detailed documentation)
   * @param replaceId      the ID of the upload to replace (sets the replacement policy to [[ReplacementPolicy.ReplaceOne]]
   * @param callbackUrl    an optional URL that will be called when the upload succeeds (max 200 chrs)
   * @param callbackSecret an optional shared secret that will be used to sign (HMAC.SHA256) the callback (max 200 chrs)
   */
  case class UploadRequest(uploader: Option[Principal] = None,
                           replacement: ReplacementPolicy.Value = ReplacementPolicy.NoReplace,
                           replaceId: Option[Int] = None,
                           callbackUrl: Option[String] = None,
                           callbackSecret: Option[String] = None)


  case class APIResponse(success: Boolean, error: Option[String] = None, errorMessage: Option[String] = None,
                         data: Option[JsValue] = None)


  object APIResponse {
    def apply(error: String, errorMessage: String): APIResponse = APIResponse(false, Some(error), Some(errorMessage))

    def apply(data: JsValue): APIResponse = APIResponse(true, None, None, Some(data))
  }

  /**
   * A request for a delegation token
   *
   * @param principal  if specified, an authentication token for that principal will have to be presented with the
   *                   delegation session
   * @param appId      the app in which you are delegating access
   * @param containers the containers to which you allow access, in that app
   */
  case class DelegationRequest(principal: Option[Principal], appId: Int, containers: Set[String])

  // Json formats

  implicit val ContainerFormat: Format[Container] = Json.format[Container]
  implicit val SimpleContainerFormat: Format[SimpleContainer] = Json.format[SimpleContainer]
  implicit val UploadFormat: Format[Upload] = Json.format[Upload]
  implicit val UploadStatusResponseFormat: Format[UploadStatusResponse] = Json.format[UploadStatusResponse]
  implicit val ReplacementPolicyFormat: Format[ReplacementPolicy.Value] = Json.formatEnum(ReplacementPolicy)
  implicit val UploadRequestFormat: Format[UploadRequest] = Json.format[UploadRequest]
  implicit val APIResponseFormat: Format[APIResponse] = Json.format[APIResponse]
  implicit val DelegationRequestFormat: Format[DelegationRequest] = Json.format[DelegationRequest]
}
