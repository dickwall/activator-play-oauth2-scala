package controllers

import play.api.mvc.{ Action, Controller }
import java.util.UUID

class Application extends Controller {

  def index = Action { implicit request =>

    val callbackUrl = routes.OAuth2.callback(None, None).absoluteURL()
    val scope = "repo" // github scope - request repo access

    val state = UUID.randomUUID().toString // random confirmation string
    val redirectUrl = OAuth2.getAuthorizationUrl(callbackUrl, scope, state)

    Ok(views.html.index("Your new application is ready.", redirectUrl)).
      withSession("oauth-state" -> state)

  }

}
