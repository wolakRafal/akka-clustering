package sample.cluster.sharding.blog

import java.io.File

import akka.actor.{ActorIdentity, Identify, Props}
import akka.cluster.Cluster
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.persistence.Persistence
import akka.persistence.journal.leveldb.{SharedLeveldbJournal, SharedLeveldbStore}
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils

object BlogSpec extends MultiNodeConfig{
  val controller = role("controller")
  val node1 = role("node1")
  val node2 = role("node2")

  commonConfig(ConfigFactory.parseString(
    """
      |akka.cluster.metrics.enabled=off
      |    akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
      |    akka.persistence.journal.plugin = "akka.persistence.journal.leveldb-shared"
      |    akka.persistence.journal.leveldb-shared.store {
      |      native = off
      |      dir = "target/test-shared-journal"
      |    }
      |    akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      |    akka.persistence.snapshot-store.local.dir = "target/test-snapshots"
      |
    """.stripMargin))
}

class BlogSpecMultiJvmNode1 extends BlogSpec
class BlogSpecMultiJvmNode2 extends BlogSpec
class BlogSpecMultiJvmNode3 extends BlogSpec

class BlogSpec extends MultiNodeSpec(BlogSpec) with STMultiNodeSpec with ImplicitSender {
  import BlogSpec._

  override def initialParticipants: Int = roles.size

  val storageLocations = List(
    "akka.persistence.journal.leveldb.dir",
    "akka.persistence.journal.leveldb-shared.store.dir",
    "akka.persistence.snapshot-store.local.dir").map(s => new File(system.settings.config.getString(s)))

  override protected def atStartup() {
    runOn(controller) {
      storageLocations.foreach(dir => FileUtils.deleteDirectory(dir))
    }
  }

  override protected def afterTermination() {
    runOn(controller) {
      storageLocations.foreach(dir => FileUtils.deleteDirectory(dir))
    }
  }

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      Cluster(system) join node(to).address
      startSharding()
    }
    enterBarrier(from.name + "-joined")
  }

  def startSharding(): Unit = {
    ClusterSharding(system).start(
      typeName = AuthorListing.shardName,
      entityProps = AuthorListing.props(),
      settings = ClusterShardingSettings(system),
      extractEntityId = AuthorListing.idExtractor,
      extractShardId = AuthorListing.shardResolver)

    ClusterSharding(system).start(
      typeName = Post.shardName,
      entityProps = Post.props(ClusterSharding(system).shardRegion(AuthorListing.shardName)),
      settings = ClusterShardingSettings(system),
      extractEntityId = Post.idExtractor,
      extractShardId = Post.shardResolver)
  }

  "Sharded blog app" must {
    "setup shared journal" in {
      // startup the Persistence extension
      Persistence(system)
      runOn(controller) {
        system.actorOf(Props[SharedLeveldbStore], "store")
      }
      enterBarrier("persistence-started")

      runOn(node1, node2) {
       system.actorSelection(node(controller) / "user"/ "store") ! Identify(None)
        val sharedStore = expectMsgType[ActorIdentity].ref.get
        SharedLeveldbJournal.setStore(sharedStore, system)
      }
    }
  }


}

