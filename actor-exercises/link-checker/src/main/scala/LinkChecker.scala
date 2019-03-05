import java.net.URL

import LinkChecker.LinkRequestReceiver._
import LinkChecker.LinkFinder.Find
import LinkChecker.MainActor
import LinkChecker.MainActor.Finished
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive
import akka.util.Timeout

import scala.collection.immutable.Queue

object LinkChecker {

  class PooledLinkRequestReceiver extends Actor {
    import LinkRequestReceiver._
    var requestQueue = Queue[LinkRequest]()

    override def receive: Receive = LoggingReceive {
      case request@LinkRequest(_, _) =>
        if(requestQueue.isEmpty) {
          println(s"processing request $request")
          requestQueue = requestQueue.enqueue(request)
          self ! ProcessRequest(request)
        }
        else if(requestQueue.length > 2){
          println(s"I am busy for $request")
          sender ! Busy
        }
        else {
          println(s"queueing request $request")
          requestQueue = requestQueue.enqueue(request)
        }
      case ProcessRequest(request) =>
        context.actorOf(LinkAggregator.props(request)) ! request
      case result@LinkResult(_, _) => context.parent ! result
        requestQueue.dequeueOption.fold(
          context.parent ! Finished
        ){
          case (head, rest) =>
            println(s"deque request $head")
            self ! ProcessRequest(head)
            requestQueue = rest
        }
      case Busy => sender ! Busy
    }
  }

  class SingleLinkRequestReceiver extends Actor {

    import LinkRequestReceiver._

    override def receive: Receive = LoggingReceive  {
      case request@LinkRequest(_, _) =>
        context.actorOf(LinkAggregator.props(request)) ! request
        context.become(busy())
    }

    def busy(): Receive = LoggingReceive {
        case result@LinkResult(_, _) =>
          context.parent ! result
          context.stop(self)
    }
  }

  object LinkRequestReceiver {
    def propsPool = Props(new PooledLinkRequestReceiver)
    def propsSingle = Props(new SingleLinkRequestReceiver)

    case class LinkRequest(url: URL, depth: Int)
    case class ProcessRequest(request: LinkRequest)
    case class LinkResult(url: URL, links: Set[URL])
    case class Failed(url: URL)
    case object Busy
  }

  class LinkAggregator(request: LinkRequest) extends Actor {
    import LinkRequestReceiver._

    var linksFound = Set[URL]()
    var children = Set[ActorRef]()

    override def receive: Receive = LoggingReceive {
      case LinkRequest(link, depth) =>
        if(!linksFound(link) && depth > 0) {
          val finder = context.actorOf(LinkFinder.props(link, depth - 1))
          children += finder
          finder ! Find
        }
        linksFound += link
      case LinkAggregator.Done =>
        children -= sender
        if(children.isEmpty) context.parent ! LinkResult(request.url, linksFound)
      case Timeout => children foreach(_ ! LinkFinder.Abort)
    }
  }

  object LinkAggregator {
    def props(request: LinkRequest) = Props(new LinkAggregator(request))
    case object Done
  }

  class LinkFinder(url: URL, depth: Int) extends Actor with ActorLogging {
    import LinkFinder._

    val webClient = new WebClient()


    override def postStop(): Unit = {
      super.postStop()
      webClient.backend.close()
    }

    override def receive: Receive = LoggingReceive {
      case LinkFinder.Find =>
        webClient.get(url).fold(
          _ => log.error(s"Unable to obtain links in $url"),
          links => links.foreach(context.parent ! LinkRequest(_, depth))
        )
        context.parent ! LinkAggregator.Done
        context.stop(self)
      case Abort =>
        context.parent ! LinkAggregator.Done
        context.stop(self)
    }
  }

  object LinkFinder {
    def props(url: URL, depth: Int) = Props(new LinkFinder(url, depth))

    case class Links(embeddedUrls: Set[URL])
    case object Find
    case object Abort

  }

  class MainActor extends Actor {
    private val requestReceiver: ActorRef = context.actorOf(LinkRequestReceiver.propsPool)
    requestReceiver ! LinkRequest(new URL("http://www.bbc.co.uk/news"), 2)
    /*requestReceiver ! LinkRequest(new URL("http://www.bbc.co.uk/weather"), 1)
    requestReceiver ! LinkRequest(new URL("http://www.bbc.co.uk/sports"), 1)
    requestReceiver ! LinkRequest(new URL("http://www.bbc.co.uk"), 1)*/

    override def receive: Receive = {
      case LinkResult(url, innerUrls) => println(s"$url has $innerUrls")
      case Busy => println("too many request")
      case Finished => println("finished")
        context.stop(self)
    }
  }

  object MainActor {
    case object Finished
  }
}

object Main extends App {
  akka.Main.main(Array[String](classOf[MainActor].getName))
}