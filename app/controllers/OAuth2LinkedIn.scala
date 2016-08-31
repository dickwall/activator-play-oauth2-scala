package controllers

import javax.inject.Inject

import play.api.http.{ HeaderNames, MimeTypes }
import play.api.libs.ws.WSClient
import play.api.mvc.{ Action, Controller, Results }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OAuth2LinkedIn @Inject() (val wsClient: WSClient, val configuration: play.api.Configuration) extends Controller {
  lazy val githubAuthId = configuration.getString("linkedin.client.id").get
  lazy val githubAuthSecret = configuration.getString("linkedin.client.secret").get

  OAuth2LinkedIn.setConfigs(configuration)

  def getToken(code: String, redirect_uri: String): Future[String] = {
    val tokenResponse = wsClient.url("https://www.linkedin.com/uas/oauth2/accessToken").
      withQueryString(
        "redirect_uri" -> redirect_uri,
        "grant_type" -> "authorization_code",

        "client_id" -> githubAuthId,
        "client_secret" -> githubAuthSecret,
        "code" -> code
      ).
        withHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON).
        post(Results.EmptyContent())

    tokenResponse.flatMap { response =>
      println(response.json)
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
        getToken(code, routes.OAuth2LinkedIn.callback(None, None).absoluteURL()).map { accessToken =>
          Redirect(controllers.routes.OAuth2LinkedIn.success()).withSession("oauth-token" -> accessToken)
        }.recover {
          case ex: IllegalStateException => Unauthorized(ex.getMessage)
        }
      } else
        Future.successful(BadRequest("Invalid LinkedIn login"))

    }).getOrElse(Future.successful(BadRequest("No parameters supplied")))

  }

  def success() = Action.async { request =>
    request.session.get("oauth-token").
      fold(Future.successful(Unauthorized("No way Jose"))) { authToken =>
        wsClient.url("https://api.linkedin.com/v1/people/~:(email-address,id,first-name,last-name,industry,picture-url,public-profile-url,headline)?format=json").
          withHeaders(HeaderNames.AUTHORIZATION -> s"Bearer $authToken").
          get().map { response =>
            Ok(response.json)
          }
      }
  }

}

object OAuth2LinkedIn {
  var configuration: play.api.Configuration = _

  def setConfigs(conf: play.api.Configuration): Unit = {
    configuration = conf
  }

  def getAuthorizationUrl(redirectUri: String, state: String): String = {

    val baseUrl = configuration.getString("linkedin.redirect.url").get

    baseUrl.format(configuration.getString("linkedin.client.id").get, redirectUri, state)

  }

}