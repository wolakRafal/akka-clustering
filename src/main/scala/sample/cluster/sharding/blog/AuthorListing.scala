package sample.cluster.sharding.blog

import akka.actor.{ActorLogging, PoisonPill, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.Passivate
import akka.persistence.PersistentActor

import scala.collection.immutable
import scala.concurrent.duration._

object AuthorListing {

  def props(): Props = Props(new AuthorListing)

  case class PostSummary(author: String, postId: String, title: String)
  case class GetPosts(author: String)
  case class Posts(list: immutable.IndexedSeq[PostSummary])

  val idExtractor: ShardRegion.ExtractEntityId = {
    case s: PostSummary => (s.author, s)
    case m: GetPosts    => (m.author, m)
  }

  val shardResolver: ShardRegion.ExtractShardId = msg => msg match {
    case s: PostSummary   => (math.abs(s.author.hashCode) % 100).toString
    case GetPosts(author) => (math.abs(author.hashCode) % 100).toString
  }

  val shardName: String = "AuthorListing"
}

class AuthorListing extends PersistentActor with ActorLogging {
  import AuthorListing._

  override def persistenceId: String = self.path.parent.name + "-" + self.path.name

  // passivate the entity when no activity
  context.setReceiveTimeout(2.minutes)

  var posts = Vector.empty[PostSummary]

  def receiveCommand = {
    case s: PostSummary =>
      persist(s) { evt =>
        posts :+= evt
        log.info("Post added to {}'s list: {}", s.author, s.title)
      }
    case GetPosts(_) =>
      sender() ! Posts(posts)
    case ReceiveTimeout => context.parent ! Passivate(stopMessage = PoisonPill)
  }

  override def receiveRecover: Receive = {
    case evt: PostSummary => posts :+= evt

  }

}