package akka.dispatch

import akka.actor.{ Props, LocalActorRef, Actor }
import akka.testkit.AkkaSpec
import akka.util.Duration
import akka.testkit.DefaultTimeout

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class PriorityDispatcherSpec extends AkkaSpec with DefaultTimeout {

  "A PriorityDispatcher" must {
    "Order it's messages according to the specified comparator using an unbounded mailbox" in {
      testOrdering(UnboundedPriorityMailbox(PriorityGenerator({
        case i: Int  ⇒ i //Reverse order
        case 'Result ⇒ Int.MaxValue
      }: Any ⇒ Int)))
    }

    "Order it's messages according to the specified comparator using a bounded mailbox" in {
      testOrdering(BoundedPriorityMailbox(PriorityGenerator({
        case i: Int  ⇒ i //Reverse order
        case 'Result ⇒ Int.MaxValue
      }: Any ⇒ Int), 1000, system.settings.MailboxPushTimeout))
    }
  }

  def testOrdering(mboxType: MailboxType) {
    val dispatcher = system.dispatcherFactory.newDispatcher("Test", 1, Duration.Zero, mboxType).build

    val actor = system.actorOf(Props(new Actor {
      var acc: List[Int] = Nil

      def receive = {
        case i: Int  ⇒ acc = i :: acc
        case 'Result ⇒ sender.tell(acc)
      }
    }).withDispatcher(dispatcher)).asInstanceOf[LocalActorRef]

    actor.suspend //Make sure the actor isn't treating any messages, let it buffer the incoming messages

    val msgs = (1 to 100).toList
    for (m ← msgs) actor ! m

    actor.resume //Signal the actor to start treating it's message backlog

    Await.result(actor.?('Result).mapTo[List[Int]], timeout.duration) must be === msgs.reverse
  }

}
