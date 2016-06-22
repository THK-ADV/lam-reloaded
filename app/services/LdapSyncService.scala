package services

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import models.users.{Employee, Student, User}
import services.LdapSyncServiceActor.SyncRequest
import store.bind.Bindings
import store.{Resolvers, SesameRepository}
import us.theatr.akka.quartz.{AddCronSchedule, QuartzActor}

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

trait LdapSyncService {
  val cronExpression: String
  val ldapService: LdapService
  val resolvers: Resolvers
  val repository: SesameRepository
}

class ActorBasedLdapSyncService(val system: ActorSystem, val cronExpression: String, val repository: SesameRepository, val ldapService: LdapService, val resolvers: Resolvers) extends LdapSyncService {

  val quartzActor = system.actorOf(Props[QuartzActor])
  val destinationActorRef = system.actorOf(LdapSyncServiceActor.props(repository, ldapService, resolvers))

  quartzActor ! AddCronSchedule(destinationActorRef, cronExpression, SyncRequest)
}

object LdapSyncServiceActor {

  def props(repository: SesameRepository, ldapService: LdapService, resolvers: Resolvers) = Props(new LdapSyncServiceActor(repository, ldapService, resolvers))

  case object SyncRequest
}

class LdapSyncServiceActor(val repository: SesameRepository, val ldapService: LdapService, val resolvers: Resolvers) extends Actor with ActorLogging {

  val bindings = Bindings[repository.Rdf](repository.namespace)
  implicit val dispatcher: ExecutionContextExecutor = context.system.dispatcher
  import bindings.UserBinding.{userBinder, classUri}

  override def receive: Receive = {
    case SyncRequest =>
      def difference(current: Set[User], newest: Set[User]): Set[User] = {
        def equalizeId(from: User, to: User): Option[User] = (from, to) match {
          case (s1: Student, s2: Student) => Some(Student(s2.systemId, s2.lastname, s2.firstname, s2.email, s2.registrationId, s2.enrollment, s1.id))
          case (e1: Employee, e2: Employee) => Some(Employee(e2.systemId, e2.lastname, e2.firstname, e2.email, e2.status, e1.id))
          case _ => None
        }

        def differs(pair1: User, pair2: User): Option[User] = {
          equalizeId(pair1, pair2).flatMap(eq => if (pair1.equals(eq)) None else Some(eq))
        }

        current.foldLeft(Set.empty[User]) { (set, c) =>
          newest.find(_.systemId == c.systemId)
            .flatMap(n => differs(c, n))
            .fold(set)(diffUser => set + diffUser)
        }
      }

      repository.get[User](userBinder, classUri) foreach { lwmUsers =>
        ldapService.users(lwmUsers.map(_.systemId.toLowerCase))(resolvers.degree).onComplete {
          case Success(ldapUsers) =>
            difference(lwmUsers, ldapUsers) foreach { user =>
              repository.update(user)(userBinder, User) match {
                case Success(_) => log.info(s"updated $user")
                case Failure(e) => log.error(s"failed to update $user. Exception: ${e.getMessage}")
              }
            }
          case Failure(e) => log.error(s"failed to gather users from ldap. Exception: ${e.getMessage}")
        }
      }
  }
}