package com.github.vmencik.akkanative

import java.util.logging.LogManager

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

object Main extends LazyLogging {

  def main(args: Array[String]): Unit = {
    configureLogging()

    val config = ConfigFactory.load()
    implicit val system: ActorSystem = ActorSystem("graal", config)
    implicit val materializer: Materializer = ActorMaterializer()
    implicit val ec: ExecutionContext = system.dispatcher

    val route =
      path("graal-hp-size") {
        get {
          onSuccess(graalHomepageSize) { size =>
            complete(size.toString)
          }
        }
      }

    Http()
      .bindAndHandle(route,
                     config.getString("http.service.bind-to"),
                     config.getInt("http.service.port"))
      .andThen {
        case Success(binding) => logger.info(s"Listening at ${binding.localAddress}")
      }
  }

  private def graalHomepageSize(implicit ec: ExecutionContext,
                                system: ActorSystem,
                                mat: Materializer): Future[Int] =
    Http().singleRequest(HttpRequest(uri = "https://www.graalvm.org")).flatMap { resp =>
      resp.status match {
        case StatusCodes.OK =>
          resp.entity.dataBytes.runFold(0) { (cnt, chunk) =>
            cnt + chunk.size
          }
        case other =>
          resp.discardEntityBytes()
          throw new IllegalStateException(s"Unexpected status code $other")
      }

    }

  private def configureLogging(): Unit = {
    val is = getClass.getResourceAsStream("/app.logging.properties")
    try {
      LogManager.getLogManager.reset()
      LogManager.getLogManager.readConfiguration(is)
    }
    finally is.close()
  }

}
