import java.util.UUID

import Account.{Deposit, Withdraw}
import Transaction.Transfer
import akka.actor.{Actor, ActorRef, Props}
import akka.event.LoggingReceive

object Main {
  def main(args: Array[String]): Unit = {
    akka.Main.main(Array[String](classOf[TransferMain].getName))
  }
}

trait WithUUID {
  val id: UUID = UUID.randomUUID()
}

class TransferMain extends Actor {
  val bobAccount = context.actorOf(Account.props, "Bob")
  val aliceAccount = context.actorOf(Account.props, "Alice")

  bobAccount ! Deposit(100)

  override def receive: Receive = LoggingReceive {
    case Account.Done =>
      context.actorOf(Transaction.props, "BobToAlice") ! Transfer(bobAccount, aliceAccount, 50)
      context.become(awaitingTransfer())
  }

  def awaitingTransfer(): Receive = LoggingReceive {
    case Transaction.Done =>
      println("Transfer complete")
      context.stop(self)
    case Transaction.Failed =>
      println("Transfer failed")
      context.stop(self)
  }
}


class Account extends Actor {

  import Account._

  var balance: BigInt = 0

  override def receive: Receive = LoggingReceive {
    case Deposit(amt) =>
      balance = balance + amt
      sender ! Done
    case Withdraw(amt) if amt <= balance => balance = balance - amt
      sender ! Done
    case _ => sender ! Account.Failed
  }
}

object Account {

  def props = Props(new Account)

  case class Deposit(amount: BigInt) extends WithUUID {
    require(amount > 0)
  }

  case class Withdraw(amount: BigInt) extends WithUUID {
    require(amount > 0)
  }

  case object Done

  case object Failed

}

class Transaction extends Actor {

  import Transaction._

  override def receive: Receive = LoggingReceive {
    case Transfer(from, to, amount) =>
      from ! Withdraw(amount)
      context.become(awaitWithdrawal(to, amount, sender))
    case Account.Failed =>
      sender ! Transaction.Failed
      context.stop(self)
  }

  def awaitWithdrawal(to: ActorRef, amount: BigInt, client: ActorRef): Receive = LoggingReceive {
    case Account.Done =>
      to ! Deposit(amount)
      context.become(awaitDeposit(client))
    case Account.Failed =>
      client ! Transaction.Failed
      context.stop(self)
  }

  def awaitDeposit(client: ActorRef): Receive = LoggingReceive {
    case Account.Done =>
      client ! Transaction.Done
      context.stop(self)
    case Account.Failed =>
      client ! Transaction.Failed
      context.stop(self)

  }
}

object Transaction {
  def props = Props(new Transaction)

  case class Transfer(from: ActorRef, to: ActorRef, amount: BigInt) extends WithUUID

  case object Done

  case object Failed

}




