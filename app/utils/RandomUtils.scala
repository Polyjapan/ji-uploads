package utils

import java.security.SecureRandom
import java.util.Base64

import scala.util.Random

/**
  * @author zyuiop
  */
object RandomUtils {
  private val base64 = Base64.getUrlEncoder
  private val random = new Random(new SecureRandom())

  def randomString(length: Int): String = {
    base64.encodeToString(randomBytes(length)).filterNot(_ == '=').take(length)
  }

  def randomBytes(bytes: Int): Array[Byte] = {
    val tokenBytes = new Array[Byte](bytes)
    random.nextBytes(tokenBytes)
    tokenBytes
  }
}
