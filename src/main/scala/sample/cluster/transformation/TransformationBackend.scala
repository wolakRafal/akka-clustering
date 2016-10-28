package sample.cluster.transformation

import akka.actor.{Actor, ActorSystem, Props, RootActorPath}
import akka.actor.Actor.Receive
import akka.cluster.{Cluster, Member}
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import akka.cluster.protobuf.msg.ClusterMessages.MemberStatus
import com.typesafe.config.ConfigFactory

class TransformationBackend extends Actor {

  val cluster = Cluster(context.system)

  // subscribe to cluster changes, MemberUp
  // re-subscribe when restart
  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp])

  override def postStop(): Unit = cluster.unsubscribe(self)

  override def receive: Receive = {
    case TransformationJob(text) => sender() ! TransformationResult(text.toUpperCase)
    case state: CurrentClusterState =>
      state.members.filter(_.status == MemberStatus.Up) foreach register
    case MemberUp(member) => register(member)
  }

  def register(member: Member): Unit = {
    if(member.hasRole("frontend")) {
      context.actorSelection(RootActorPath(member.address) / "user" / "frontend") ! BackendRegistration
    }
  }
}


object TransformationBackend {
  def main(args: Array[String]): Unit = {
    // Override the configuration of the port when specified as program argument
    val port = if (args.isEmpty) "0" else args(0)
    val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$port").
      withFallback(ConfigFactory.parseString("akka.cluster.roles = [backend]")).
      withFallback(ConfigFactory.load())

    val system = ActorSystem("ClusterSystem", config)
    system.actorOf(Props[TransformationBackend], name = "backend")
  }
}