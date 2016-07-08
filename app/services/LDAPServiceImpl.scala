package services

import java.util.UUID
import java.util.concurrent._
import javax.net.ssl.SSLContext

import com.unboundid.ldap.sdk._
import com.unboundid.util.ssl.{SSLUtil, TrustAllTrustManager}
import models.users.{Employee, Student, User}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait LdapService {
  def authenticate(user: String, password: String): Future[Boolean]

  def user(user: String)(degreeFor: String => Try[UUID]): Future[User]

  def users(user: Set[String])(degreeFor: (String) => Try[UUID]): Future[Set[User]]
}

/**
  * The [[LdapServiceImpl]] object enables the user to communicate with an LDAP service.
  */
case class LdapServiceImpl(bindHost: String, bindPort: Int, dn: String, bindUsername: Option[String], bindPassword: Option[String]) extends LdapService {

  private implicit val executionContext = ExecutionContext.fromExecutorService(new ThreadPoolExecutor(0, 32, 60L, TimeUnit.SECONDS, new SynchronousQueue[Runnable]))

  private val trustManager = new TrustAllTrustManager()
  // Yes, it is actually a bad idea to trust every server but for now it's okay as we only use it with exactly one server in a private network.
  private val sslUtil = new SSLUtil(trustManager)
  private val connectionOptions = new LDAPConnectionOptions()
  connectionOptions.setAutoReconnect(true)
  connectionOptions.setUseSynchronousMode(true)

  def authenticate(user: String, password: String): Future[Boolean] = bind() { connection ⇒
    val bindRequest = new SimpleBindRequest(bindDN(user), password)
    val bindResult = connection.bind(bindRequest)
    Success(bindResult.getResultCode == ResultCode.SUCCESS)
  }

  def filter[B](predicate: String)(f: List[SearchResultEntry] => B): Future[B] = bind(bindUsername, bindPassword) { connection =>
    search(connection, dn, predicate) map f
  }

  private def bind[A](user: Option[String] = None, password: Option[String] = None)(f: LDAPConnection => Try[A]): Future[A] = Future {
    f(connection(user, password)) match {
      case Success(a) => a
      case Failure(e) => throw new RuntimeException(e)
    }
  }

  private def connection(user: Option[String] = None, password: Option[String] = None): LDAPConnection = ssl { context =>
    val connection = (for {
      u <- user
      p <- password
      dn = bindDN(u)
    } yield new LDAPConnection(context.getSocketFactory, bindHost, bindPort, dn, p)
      ) getOrElse new LDAPConnection(context.getSocketFactory, bindHost, bindPort)

    connection.setConnectionOptions(connectionOptions)
    connection
  }

  private def bindDN(uid: String): String = s"uid=$uid,$dn"

  private def ssl[A](f: SSLContext => A): A = {
    f(sslUtil.createSSLContext("SSLv3"))
  }

  override def users(users: Set[String])(degreeFor: (String) => Try[UUID]): Future[Set[User]] = bind(bindUsername, bindPassword) { connection ⇒
    Success {
      users.map { user =>
        user0(connection, user)(degreeFor)
      }.filter(_.isSuccess).map(_.get)
    }
  }

  private def search(connection: LDAPConnection, baseDN: String, predicate: String = "(cn=*)"): Try[List[SearchResultEntry]] = {
    import scala.collection.JavaConverters._
    Try(connection.search(baseDN, SearchScope.SUB, Filter.create(predicate))) map (_.getSearchEntries.asScala.toList)
  }

  override def user(user: String)(degreeFor: String => Try[UUID]): Future[User] = bind(bindUsername, bindPassword) { connection ⇒
    user0(connection, user)(degreeFor)
  }

  private def user0(connection: LDAPConnection, user: String)(degreeFor: String => Try[UUID]): Try[User] = {
    search(connection, bindDN(user)) flatMap {
      case h :: Nil => makeUser(h, user)(degreeFor)
      case h :: t => Failure(new Throwable(s"More than one LDAP entry found under username $user"))
      case _ => Failure(new Throwable("No attributes found"))
    }
  }

  private def makeUser(entry: SearchResultEntry, user: String)(degreeFor: String => Try[UUID]): Try[User] = {
    val optUser = attribute(entry, "employeeType").flatMap[User] {
      case status@(User.employeeType | User.lecturerType) => employee(entry, status)
      case User.studentType => student(entry)(degreeFor)
      case _ => None
    }
    optUser.fold[Try[User]](Failure(new Throwable(s"Could not resolve user $user")))(u => Success(u))
  }

  private def attribute(entry: SearchResultEntry, parameter: String): Option[String] = Option(entry.getAttributeValue(parameter))

  private def employee(entry: SearchResultEntry, status: String): Option[Employee] = {
    for {
      systemId <- attribute(entry, "uid")
      firstname <- attribute(entry, "givenName")
      lastname <- attribute(entry, "sn")
      email = attribute(entry, "mail") getOrElse ""
    } yield Employee(systemId, lastname, firstname, email, status)
  }

  private def student(entry: SearchResultEntry)(degreeFor: String => Try[UUID]): Option[Student] = {
    for {
      systemId <- attribute(entry, "uid")
      firstname <- attribute(entry, "givenName")
      lastname <- attribute(entry, "sn")
      regId <- attribute(entry, "matriculationNumber")
      enrollment <- attribute(entry, "studyPath")
      email = attribute(entry, "mail") getOrElse ""
      degree <- degreeFor(enrollment).toOption
    } yield Student(systemId, lastname, firstname, email, regId, degree)
  }
}
