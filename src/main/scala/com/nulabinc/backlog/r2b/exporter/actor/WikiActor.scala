package com.nulabinc.backlog.r2b.exporter.actor

import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels
import java.util.concurrent.CountDownLatch

import akka.actor.Actor
import com.nulabinc.backlog.migration.common.convert.Convert
import com.nulabinc.backlog.migration.common.utils.{IOUtil, Logging}
import com.nulabinc.backlog.r2b.exporter.core.ExportContext
import com.taskadapter.redmineapi.bean.WikiPage
import spray.json._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * @author uchida
  */
private[exporter] class WikiActor(exportContext: ExportContext) extends Actor with Logging {

  import com.nulabinc.backlog.migration.common.formatters.BacklogJsonProtocol._

  implicit val wikiWrites = exportContext.wikiWrites

  override def preRestart(reason: Throwable, message: Option[Any]) = {
    logger.debug(s"preRestart: reason: ${reason}, message: ${message}")
    for { value <- message } yield {
      context.system.scheduler.scheduleOnce(10.seconds, self, value)
    }
  }

  def receive: Receive = {
    case WikiActor.Do(wiki: WikiPage, completion: CountDownLatch, allCount: Int, console: ((Int, Int) => Unit)) =>
      exportContext.wikiService.optWikiDetail(wiki.getTitle).foreach { wikiDetail =>

        val backlogWiki = Convert.toBacklog(wikiDetail)
        IOUtil.output(exportContext.backlogPaths.wikiJson(backlogWiki.name), backlogWiki.toJson.prettyPrint)

        wikiDetail.getAttachments.asScala.foreach { attachment =>

          val dir = exportContext.backlogPaths.wikiAttachmentDirectoryPath(backlogWiki.name)
          val path = exportContext.backlogPaths.wikiAttachmentPath(backlogWiki.name, attachment.getFileName)

          IOUtil.createDirectory(dir)

          val url: URL = new URL(s"${attachment.getContentURL}?key=${exportContext.apiConfig.key}")

          try {
            val rbc = Channels.newChannel(url.openStream())
            val fos = new FileOutputStream(path.path.toFile)
            fos.getChannel.transferFrom(rbc, 0, java.lang.Long.MAX_VALUE)

            rbc.close()
            fos.close()
          } catch {
            case e: Throwable => logger.warn("Download wiki attachment failed: " + e.getMessage)
          }
        }
      }

      completion.countDown()
      console((allCount - completion.getCount).toInt, allCount)
  }

}

private[exporter] object WikiActor {

  case class Do(wiki: WikiPage, completion: CountDownLatch, allCount: Int, console: ((Int, Int) => Unit))

}
