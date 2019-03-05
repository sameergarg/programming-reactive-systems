import java.net.URL

import com.softwaremill.sttp._

import scala.util.Try


class WebClient {

  //TODO close it appropriately
  implicit val backend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()

  def get(url: URL): Either[String, Set[URL]] = {
    println(s"fetching ${url.toString}")
    val request = sttp.get(uri"${url.toString}")
    val response = request.send()
    response.body.map(_.split{"""\s+"""}.map{ s => Try { new URL(s) } }.flatMap{ _.toOption }.toSet)
  }


}
