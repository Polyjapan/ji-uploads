package ch.japanimpact.api.uploads

import ch.japanimpact.auth.api.apitokens.Principal
import play.api.libs.json._

package object uploads {

  case class Container(containerId: Option[Int],
                       appId: Option[Int],
                       containerName: String,
                       maxFileSizeBytes: Long,
                       allowedTypes: Map[String, String])

  implicit val ContainerFormat: Format[Container] = Json.format[Container]


  case class SimpleContainer(containerId: Option[Int],
                             appId: Option[Int],
                             containerName: String,
                             maxFileSizeBytes: Long) {
    def toContainer(map: Seq[(String, String)]) = Container(containerId, appId, containerName, maxFileSizeBytes, map.toMap)
  }

  implicit val SimpleContainerFormat: Format[SimpleContainer] = Json.format[SimpleContainer]

  case class Upload(uploadId: Int,
                    containerId: Int,
                    uploader: Option[Principal],
                    url: String,
                    mimeType: String,
                    sizeBytes: Long)

  implicit val UploadFormat: Writes[Upload] = Json.writes[Upload]


  object ReplacementPolicy extends Enumeration {
    type ReplacementPolicy = Value
    val NoReplace, ReplaceOne, ReplaceAll = Value
  }

  case class UploadStatusResponse(uploaded: Boolean, upload: Option[Upload])

  implicit val UploadStatusResponseFormat: Writes[UploadStatusResponse] = Json.writes[UploadStatusResponse]


  implicit val ReplacementPolicyFormat: Format[ReplacementPolicy.Value] = Json.formatEnum(ReplacementPolicy)

  /**
   * Details about an uploads container
   *
   * @param name         the name of the container
   * @param allowedTypes a map MimeType->file extension of all allowed mime types
   * @param maxSizeBytes the maximum file size
   */
  case class dadada(name: String, allowedTypes: Map[String, String], maxSizeBytes: Long)

  case class UploadRequest(
                            uploader: Option[Principal],
                            replacement: ReplacementPolicy.Value,
                            replaceId: Option[Int]
                          )

  implicit val UploadRequestFormat: Reads[UploadRequest] = Json.reads[UploadRequest]

  case class UploadTicket(
                           uploadId: Int
                         )

  case class APIResponse(success: Boolean, error: Option[String] = None, errorMessage: Option[String] = None, data: Option[JsValue] = None)

  implicit val APIResponseFormat: Format[APIResponse] = Json.format[APIResponse]

  object APIResponse {
    def apply(error: String, errorMessage: String): APIResponse = APIResponse(false, Some(error), Some(errorMessage))

    def apply(data: JsValue): APIResponse = APIResponse(true, None, None, Some(data))
  }

}
