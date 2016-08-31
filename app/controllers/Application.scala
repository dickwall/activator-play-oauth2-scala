package controllers

import play.api.mvc.{ Action, Controller }
import java.util.UUID

class Application extends Controller {

  def index = Action { implicit request =>

    val callbackUrl = routes.OAuth2Github.callback(None, None).absoluteURL()
    val scope = "repo" // github scope - request repo access

    val state = UUID.randomUUID().toString // random confirmation string
    val redirectUrl = OAuth2Github.getAuthorizationUrl(callbackUrl, scope, state)

    val callbackUrlLinkedIn = routes.OAuth2LinkedIn.callback(None, None).absoluteURL()

    val redirectUrlLinkedIn = OAuth2LinkedIn.getAuthorizationUrl(callbackUrlLinkedIn, state)

    val callbackUrlAzureAD = routes.OAuth2AzureAD.callback(None, None).absoluteURL()

    val scopeAzureAD = "User.ReadBasic.All"
    //    val resource = "https://oriongs-1369.appspot.com/qa"
    val resource = "00000002-0000-0000-c000-000000000000"
    val redirectUrlAzureAD = OAuth2AzureAD.getAuthorizationUrl(callbackUrlAzureAD, scopeAzureAD, state, resource)

    Ok(views.html.index(
      "Your new application is ready.",
      redirectUrl,
      redirectUrlLinkedIn,
      redirectUrlAzureAD
    )).

      withSession("oauth-state" -> state)

  }

}
