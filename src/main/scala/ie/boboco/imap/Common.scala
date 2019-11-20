package ie.boboco.imap

import java.io.{PrintWriter, StringWriter}
import java.util.Date

import com.sun.mail.imap.IMAPFolder
import javax.mail.{Folder, Session, Store}

import scala.util.Try

/**
 * Poor man's logging infrastructure, that just writes to stdout and puts a timestamp in front of everything.
 *
 * Overloaded methods for both strings and exception (for which is dumps the stack trace).
 */
trait Logging {
  def logMsg(msg: String): String = s"${new Date}: $msg"

  def log(msg: String): Unit = {
    println(logMsg(msg))
  }

  def log(t: Throwable): Unit = {
    val w = new StringWriter()
    t.printStackTrace(new PrintWriter(w))
    log(s"Stack trace:\n${w.toString}")
  }
}

/*
For instances that implement AutoCloseable, this trait supplies a `doWith` implicit
that executes a code block against the AutoCloseable, making sure to close it.

If an exception is throw during the `doWith` processing, we log it and propagate it.

If an exception is thrown while closing the Closeable, we log it and swallow it.
 */
trait AutoClose extends Logging {

  implicit class Closeable[V <: AutoCloseable](c: V) {
    def doWith[U](f: V => U): U = {
      try {
        f(c)
      } catch {
        case e: Throwable =>
          log(s"Failed to execute doWith against $f")
          log(e)
          throw e
      }
      finally {
        Try(c.close()).toEither match {
          case Left(t) =>
            log(s"Failed to close $c: $t")
            log(t)
          case Right(_) => ()
        }
      }
    }
  }
}

/**
 * Trait that knows about the common set (or uncommon, such as for `threshold`) command line options.
 * Knows how to parse them, and defines typed variables for them. Quick & dirty.
 */
trait Options extends App {

  lazy val opts = parseOpts(args.toList)

  lazy val host = opts("host")
  lazy val user = opts("user")
  lazy val password = opts("password")

  lazy val dryRun = opts.contains("dry-run")
  lazy val threshold = opts.getOrElse("threshold", "2000").toInt
  lazy val folders = opts("folders").split(",").toSet
  lazy val topLevel = opts.contains("top-level")

  @scala.annotation.tailrec
  final def parseOpts(args: List[String], map: Map[String, String] = Map()): Map[String, String] = {
    args match {
      case ("--host" | "-h") :: h :: tail => parseOpts(tail, map + ("host" -> h))
      case ("--user" | "-u") :: u :: tail => parseOpts(tail, map + ("user" -> u))
      case ("--password" | "-p") :: p :: tail => parseOpts(tail, map + ("password" -> p))
      case ("--dry-run" | "-n") :: tail => parseOpts(tail, map + ("dry-run" -> "true"))
      case ("--threshold" | "-t") :: t :: tail => parseOpts(tail, map + ("threshold" -> t))
      case ("--folders" | "-f") :: f :: tail => parseOpts(tail, map + ("folders" -> f))
      case ("--top-level" | "-tl") :: tail => parseOpts(tail, map + ("top-level" -> "true"))
      case _ => map
    }
  }
}

/*
Super-trait for our different command line applications. Provides some basic
utility functions that have cropped up, and knows how to open an IMAP "Store" instance
that is ready to go.
 */
trait IMAPStore extends Options with AutoClose {
  lazy val store: Store = {
    val props = System.getProperties
    //noinspection SpellCheckingInspection
    props.setProperty("mail.store.protocol", "imaps")
    val session = Session.getDefaultInstance(props, null)
    val store = session.getStore
    store.connect(host, user, password)
    store
  }

  /**
   * Utility to open a Folder, which is aware of whether we are in dry-run mode or not.
   */
  def open(f: Folder): IMAPFolder = {
    if (dryRun) f.open(Folder.READ_ONLY) else f.open(Folder.READ_WRITE)
    f.asInstanceOf[IMAPFolder]
  }

  /**~
   * Depending on whether we are in top-level mode or not, there are different ways
   * to  get hold of the parent folder. Basically we first try to open the parent
   * directly, and if that fails for any reason, we use the default folder. This is because,
   * if top-level is disabled, then the parent folder of something like "2016.1" will be "2016".
   * However, if top-level is enabled, then 2016 and 2016.1 will be peers, and so the right
   * parent folder is the default folder. This might not be the smart way to do it: we could behave
   * differently depending on top-level, or not. But it's pragmatic and it seemed to work.
   */
  def safeOpenParent(folder: Folder): IMAPFolder =
    Try(open(folder.getParent)).getOrElse(store.getDefaultFolder).asInstanceOf[IMAPFolder]

  lazy val foldersToProcess: Array[IMAPFolder] = store.getDefaultFolder.list().filter(
    f => folders.contains(f.getFullName)).map(_.asInstanceOf[IMAPFolder])
}
