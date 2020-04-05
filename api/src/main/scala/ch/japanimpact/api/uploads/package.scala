package ch.japanimpact.api.uploads

import ch.japanimpact.auth.api.apitokens.Principal
import play.api.libs.json._

package object uploads {

  case class Container(containerId: Option[Int], appId: Option[Int], containerName: String, maxFileSizeBytes: Long,
                       allowedTypes: Map[String, String])


  case class SimpleContainer(containerId: Option[Int], appId: Option[Int], containerName: String,
                             maxFileSizeBytes: Long) {

    def toContainer(map: Seq[(String, String)]): Container =
      Container(containerId, appId, containerName, maxFileSizeBytes, map.toMap)
  }

  case class Upload(uploadId: Int, containerId: Int, uploader: Option[Principal], url: String, mimeType: String,
                    sizeBytes: Long)


  object ReplacementPolicy extends Enumeration {
    type ReplacementPolicy = Value
    val NoReplace, ReplaceOne, ReplaceAll = Value
  }

  case class UploadStatusResponse(uploaded: Boolean, upload: Option[Upload])


  case class UploadRequest(uploader: Option[Principal] = None, replacement: ReplacementPolicy.Value = ReplacementPolicy.NoReplace, replaceId: Option[Int] = None)


  case class APIResponse(success: Boolean, error: Option[String] = None, errorMessage: Option[String] = None,
                         data: Option[JsValue] = None)


  object APIResponse {
    def apply(error: String, errorMessage: String): APIResponse = APIResponse(false, Some(error), Some(errorMessage))

    def apply(data: JsValue): APIResponse = APIResponse(true, None, None, Some(data))
  }

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
