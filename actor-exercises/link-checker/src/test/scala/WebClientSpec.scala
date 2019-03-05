import java.net.URL

import org.scalatest.{BeforeAndAfterAll, Matchers}

class WebClientSpec extends org.scalatest.WordSpec with BeforeAndAfterAll with Matchers {

  val webClient = new WebClient()

  "Web Client" should {
    "get links on a web page" in {
      val urls = webClient.get(new URL("http://bbc.co.uk"))
      urls.foreach(println)
      urls should not be('empty)
    }
  }

  override protected def afterAll(): Unit = {
    webClient.backend.close()
  }
}
