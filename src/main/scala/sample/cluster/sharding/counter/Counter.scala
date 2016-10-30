package sample.cluster.sharding.counter

import akka.actor.{ActorRef, Props, ReceiveTimeout}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.persistence.PersistentActor

import scala.concurrent.duration.DurationInt

case object Increment

case object Decrement

final case class Get(counterId: Long)

final case class EntityEnvelope(id: Long, payload: Any)

case object Stop

final case class CounterChanged(delta: Int)

class Counter extends PersistentActor {

  import akka.cluster.sharding.ShardRegion.Passivate

  context.setReceiveTimeout(120.seconds)

  override def persistenceId: String = "Counter-" + self.path.name

  var count = 0

  def updateState(event: CounterChanged): Unit = count += event.delta

  override def receiveRecover: Receive = {
    case evt: CounterChanged => updateState(evt)
  }

  override def receiveCommand: Receive = {
    case Increment => persist(CounterChanged(+1))(updateState)
    case Decrement => persist(CounterChanged(-1))(updateState)
    case Get(_) => sender() ! count
    case ReceiveTimeout => context.parent ! Passivate(stopMessage = Stop)
    case Stop => context.stop(self)
  }

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case EntityEnvelope(id, payload) ⇒ (id.toString, payload)
    case msg@Get(id) ⇒ (id.toString, msg)
  }

  val numberOfShards = 100

  val extractShardId: ShardRegion.ExtractShardId = {
    case EntityEnvelope(id, _) ⇒ (id % numberOfShards).toString
    case Get(id) ⇒ (id % numberOfShards).toString
  }

/////   example of usage
//  val counterRegion: ActorRef = ClusterSharding(context.system).start(
//    typeName = "Counter",
//    entityProps = Props[Counter],
//    settings = ClusterShardingSettings(context.system),
//    extractEntityId = extractEntityId,
//    extractShardId = extractShardId
//  )
}


