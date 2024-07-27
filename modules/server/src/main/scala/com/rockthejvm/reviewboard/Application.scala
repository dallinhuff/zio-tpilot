package com.rockthejvm.reviewboard

import com.rockthejvm.reviewboard.config.{
  Configs,
  EmailServiceConfig,
  JwtConfig,
  RecoveryTokensConfig
}
import com.rockthejvm.reviewboard.http.HttpApi
import com.rockthejvm.reviewboard.repositories.*
import com.rockthejvm.reviewboard.services.*
import sttp.tapir.*
import sttp.tapir.server.interceptor.cors.CORSInterceptor
import sttp.tapir.server.ziohttp.*
import zio.*
import zio.http.Server

/** Entry point for the server application */
object Application extends ZIOAppDefault:
  private val serverProgram =
    for
      endpoints <- HttpApi.endpointsZIO
      options <- ZIO.succeed:
        ZioHttpServerOptions.default.appendInterceptor(CORSInterceptor.default)
      _ <- Server.serve:
        ZioHttpInterpreter(options).toHttp(endpoints) //withDefaultErrorResponse
    yield ()

  override def run: Task[Unit] =
    serverProgram.provide(
      Server.default,
      // configs
      Configs.makeLayer[JwtConfig]("rockthejvm.jwt"),
      Configs.makeLayer[RecoveryTokensConfig]("rockthejvm.recoverytokens"),
      Configs.makeLayer[EmailServiceConfig]("rockthejvm.email"),
      // services
      CompanyServiceLive.layer,
      ReviewServiceLive.layer,
      UserServiceLive.layer,
      JwtServiceLive.layer,
      EmailServiceLive.layer,
      // repos
      CompanyRepositoryLive.layer,
      ReviewRepositoryLive.layer,
      UserRepositoryLive.layer,
      RecoveryTokensRepositoryLive.layer,
      // other requirements
      Repository.dataLayer
    )
