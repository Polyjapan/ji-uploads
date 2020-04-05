package utils

import ch.japanimpact.auth.api.apitokens.{App, Principal}

object AuthUtils {
  def computeScopes(baseScope: String, app: Int, container: String = null): Principal => Set[String] = (p: Principal) => {
    Set(computeScope(baseScope, app, container)(p))
  }

  def computeScope(baseScope: String, app: Int, container: String = null)(p: Principal): String = {
    val (_app, _container) = p match {
      case App(id) if id == app => ("self", "")
      case App(_) => (app.toString, if (container == null) "" else s"/$container")
    }

    baseScope.replace(":app", _app) + _container
  }
}
