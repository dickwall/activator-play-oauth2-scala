package controllers

import javax.inject.Inject

import play.api.http.{ HeaderNames, MimeTypes }
import play.api.libs.ws.WSClient
import play.api.mvc.{ Action, Controller, Results }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OAuth2AzureAD @Inject() (val wsClient: WSClient, val configuration: play.api.Configuration) extends Controller {
  lazy val azureadAuthId = configuration.getString("azuread.client.id").get
  lazy val azureadAuthSecret = configuration.getString("azuread.client.secret").get

  OAuth2AzureAD.setConfigs(configuration)

  def getToken(code: String, redirect_uri: String): Future[String] = {
    val tokenResponse = wsClient.url("https://login.microsoftonline.com/16f15083-eb69-41dd-8f91-3d0997ce821b/oauth2/token").
      withHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON).
      post(Map(
        "redirect_uri" -> Seq(redirect_uri),
        "grant_type" -> Seq("authorization_code"),
        "client_id" -> Seq(azureadAuthId),
        "client_secret" -> Seq(azureadAuthSecret),
        "code" -> Seq(code)
      ))

    tokenResponse.flatMap { response =>
      (response.json \ "access_token").
        asOpt[String].fold(
          Future.failed[String](new IllegalStateException("Sod off!"))
        ) { accessToken =>
            Future.successful(accessToken)
          }
    }
  }

  def callback(codeOpt: Option[String] = None, stateOpt: Option[String] = None) = Action.async { implicit request =>
    (for {
      code <- codeOpt
      state <- stateOpt
      oauthState <- request.session.get("oauth-state")
    } yield {
      if (state == oauthState) {
        getToken(code, routes.OAuth2AzureAD.callback(None, None).absoluteURL()).map { accessToken =>
          Redirect(controllers.routes.OAuth2AzureAD.success()).withSession("oauth-token" -> accessToken)
        }.recover {
          case ex: IllegalStateException => Unauthorized(ex.getMessage)
        }
      } else
        Future.successful(BadRequest("Invalid AzureAD login"))

    }).getOrElse(Future.successful(BadRequest("No parameters supplied")))

  }

  def success() = Action.async { request =>
    request.session.get("oauth-token").
      fold(Future.successful(Unauthorized("No way Jose"))) { authToken =>
        wsClient.url("https://graph.windows.net/me?api-version=1.6").
          withHeaders(
            HeaderNames.AUTHORIZATION -> s"Bearer $authToken"
          ).
            get().map { response =>
              Ok(response.json)
            }
      }
  }

}

object OAuth2AzureAD {

  var configuration: play.api.Configuration = _

  def setConfigs(conf: play.api.Configuration): Unit = {
    configuration = conf
  }

  def getAuthorizationUrl(redirectUri: String, scope: String, state: String, resource: String): String = {

    val baseUrl = configuration.getString("azuread.redirect.url").get

    baseUrl.format(configuration.getString("azuread.client.id").get, redirectUri, state, scope, resource)

  }

}
