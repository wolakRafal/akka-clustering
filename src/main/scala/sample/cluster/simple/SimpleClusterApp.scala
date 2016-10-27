package sample.cluster.simple

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object SimpleClusterApp {

  def main(args: Array[String]): Unit = {
    if(args.isEmpty){
      startup(Seq("2551", "2552", "0"))
    }  else {
      startup(args)
    }
  }

  def startup(ports: Seq[String]): Unit = {
    ports foreach { port =>
      // Override the config of the port
      val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).withFallback(ConfigFactory.load())

      // create Akka system
      val system = ActorSystem("ClusterSystem", config)
      // create an actor that handles cluster domain events
      system.actorOf(Props[SimpleClusterListener], name = "clusterListener")
    }
  }
}
