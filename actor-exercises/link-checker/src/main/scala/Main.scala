import LinkChecker.PooledLinkRequestReceiver

object Main extends App {

  akka.Main.main(Array[String](classOf[PooledLinkRequestReceiver].getName))


}
