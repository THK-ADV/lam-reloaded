package services

import java.util.UUID
import java.util.concurrent._

import com.unboundid.ldap.sdk._
import com.unboundid.util.ssl.{SSLUtil, TrustAllTrustManager}
import models.users.{Employee, Student, User}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait LDAPService {
  def authenticate(user: String, password: String): Future[Boolean]

  def user(user: String)(degreeFor: String => Try[UUID]): Future[User]

  def users(user: Set[String])(degreeFor: (String) => Try[UUID]): Future[Set[User]]
}

/**
  * The [[LDAPServiceImpl]] object enables the user to communicate with an LDAP service.
  */
case class LDAPServiceImpl(bindHost: String, bindPort: Int, dn: String) extends LDAPService {

  private implicit val executionContext = ExecutionContext.fromExecutorService(new ThreadPoolExecutor(0, 32, 60L, TimeUnit.SECONDS, new SynchronousQueue[Runnable]))

  private val trustManager = new TrustAllTrustManager()
  // Yes, it is actually a bad idea to trust every server but for now it's okay as we only use it with exactly one server in a private network.
  private val sslUtil = new SSLUtil(trustManager)
  private val connectionOptions = new LDAPConnectionOptions()
  connectionOptions.setAutoReconnect(true)
  connectionOptions.setUseSynchronousMode(true)

  /**
    * Tries to authenticate a user with the the LDAP service.
 *
    * @param user the user
    * @param password the password for this user
    * @return either a boolean if the connection was successful or a String with the error message
    */
  def authenticate(user: String, password: String): Future[Boolean] = bind(bindHost, bindPort, dn, "", ssl = true) { connection ⇒
    val bindDN = s"uid=$user, $dn"
    val bindRequest = new SimpleBindRequest(bindDN, password)
    val bindResult = connection.bind(bindRequest)
    bindResult.getResultCode == ResultCode.SUCCESS
  }

  def filter[B](predicate: String)(f: Vector[SearchResultEntry] => B): Future[B] = bind(bindHost, bindPort, dn, "", ssl = true) { connection =>
    val a = connection.search(dn, SearchScope.SUB, Filter.create(predicate), "*")
    import scala.collection.JavaConverters._
    f(a.getSearchEntries.asScala.toVector)
  }
  /**
    * Grabs all groups from LDAP.
    *
    * @param user the user
    * @param bindHost the host
    * @param bindPort the port
    * @param dn the dn
    * @return Either an error message or a with the names of the groups
    */
  def groupMembership(user: String, bindHost: String, bindPort: Int, dn: String): Future[Set[String]] = bind(bindHost, bindPort, dn, "") { connection ⇒
    import scala.collection.JavaConverters._
    val results = connection.search(dn, SearchScope.SUB, "(cn=*)", "*")
    results.getSearchEntries.asScala.filter(_.getAttribute("memberUid").getValues.toList.contains(user)).map(_.getAttribute("cn").getValue).toSet
  }

  def isMemberOfGroup(user: String, group: String, bindHost: String, bindPort: Int, dn: String): Future[Boolean] = bind(bindHost, bindPort, dn, "") { connection ⇒
    val results = connection.search(s"cn=$group,$dn", SearchScope.SUB, s"(memberUid=$user)", "*")
    results.getEntryCount > 0
  }

  /**
    * Establishes a connection with the LDAP Server and runs an arbitrary function.
 *
    * @param host the host of the LDAP server
    * @param port the port of the LDAP Server
    * @param dn
    * @param password the password needed for the binding operation
    * @param ssl is it a secure connection?
    * @param f the function that is executed when the connection was established
    * @tparam A the return value when the function was successfully executed
    * @return the result of the function f
    */
  private def bind[A](host: String, port: Int, dn: String, password: String, ssl: Boolean = true)(f: LDAPConnection ⇒ A): Future[A] = Future {
    val connection = if (ssl) {
      val sslContext = sslUtil.createSSLContext("SSLv3")
      val connection = new LDAPConnection(sslContext.getSocketFactory)
      connection.setConnectionOptions(connectionOptions)
      connection.connect(host, port)
      connection
    } else {
      new LDAPConnection(host, port)
    }

    val result = f(connection)
    connection.close()
    result
  }

  def getName(user: String, bindHost: String, bindPort: Int, dn: String): Future[(String, String)] = bind(bindHost, bindPort, dn, "") { connection ⇒
    import scala.collection.JavaConverters._
    val results = connection.search(s"uid=$user,$dn", SearchScope.SUB, s"(uid=$user)", "sn", "givenName").getSearchEntries.asScala

    if (results.size == 1) {
      val sn = results.head.getAttribute("sn").getValue
      val givenName = results.head.getAttribute("givenName").getValue
      (givenName, sn)
    } else {
      throw new RuntimeException("No name")
    }
  }

  def degreeAbbrev(user: String): Future[String] = bind(bindHost, bindPort, dn, "") { connection ⇒
    import scala.collection.JavaConverters._
    val results = connection.search(s"uid=$user,$dn", SearchScope.SUB, "(cn=*)", "*").getSearchEntries.asScala.toList

    results match {
      case h :: Nil =>
        h.getAttribute("employeeType").getValue match {
          case User.studentType => h.getAttribute("studyPath").getValue
          case _ => throw new RuntimeException(s"User $user is not a student")
        }
      case _ => throw new RuntimeException(s"User $user not found")
    }
  }

  private def extractUser(attributes: List[SearchResultEntry], user: String)(degreeFor: String => Try[UUID]): Try[User] = attributes match {
    case h :: Nil => Try {
      val forename = h.getAttribute("givenName").getValue
      val surname = h.getAttribute("sn").getValue
      val systemId = h.getAttribute("uid").getValue
      val employeeType = h.getAttribute("employeeType").getValue
      val mail = h.getAttribute("mail").getValue

      employeeType match {
        case User.employeeType | User.lecturerType =>
          Success(Employee(systemId, surname, forename, mail, employeeType))
        case User.studentType =>
          val studyPath = h.getAttribute("studyPath").getValue
          degreeFor(studyPath).map(Student(systemId, surname, forename, mail, "", _))
        case _ =>
          Failure(new Throwable(s"$user is neither an employee n'or a student"))
      }
    }.flatten
    case _ :: t => Failure(new Throwable(s"More than one LDAP entry found under username $user"))

    case _ => Failure(new Throwable("No attributes found"))
  }

  override def users(users: Set[String])(degreeFor: (String) => Try[UUID]): Future[Set[User]] = bind(bindHost, bindPort, dn, "") { connection ⇒
    import scala.collection.JavaConverters._
    import utils.Ops._
    import utils.Ops.MonadInstances.tryM

    users.map { user =>
      Try(connection.search(s"uid=$user,$dn", SearchScope.SUB, "(cn=*)", "*")) flatMap { searchResult =>
        val results = searchResult.getSearchEntries.asScala.toList

        extractUser(results, user)(degreeFor)
      }
    }.filter(_.isSuccess).sequence match {
      case Success(s) => s
      case Failure(e) => throw new RuntimeException(e.getMessage)
    }
  }

  override def user(user: String)(degreeFor: String => Try[UUID]): Future[User] = bind(bindHost, bindPort, dn, "") { connection ⇒
    import scala.collection.JavaConverters._

    Try(connection.search(s"uid=$user,$dn", SearchScope.SUB, "(cn=*)", "*")) flatMap { searchResult =>
      val results = searchResult.getSearchEntries.asScala.toList
      extractUser(results, user)(degreeFor)
    } match {
      case Success(s) => s
      case Failure(e) => throw new RuntimeException(e.getMessage)
    }
  }
}
