package com.nulabinc.r2b.cli

import com.nulabinc.backlog.importer.core.BacklogConfig
import com.nulabinc.backlog4j.BacklogAPIException
import com.nulabinc.r2b.actor.utils.R2BLogging
import com.nulabinc.r2b.conf.R2BConfig
import com.nulabinc.r2b.service.{BacklogService, RedmineService}
import com.osinka.i18n.Messages
import com.taskadapter.redmineapi.{RedmineAuthenticationException, RedmineTransportException}

/**
  * @author uchida
  */
class ParameterValidator(r2bConf: R2BConfig) extends R2BLogging {

  def validate(): Seq[String] = {
    val backlogErrors: Seq[String] = validateLoadBacklog()
    val redmineErrors: Seq[String] = validateLoadRedmine()
    val redmineProjectsErrors: Seq[String] = if (redmineErrors.isEmpty) validateLoadRedmineProjects() else Seq.empty[String]
    val backlogProjectsErrors: Seq[String] = if (redmineErrors.isEmpty) validateLoadBacklogProjects() else Seq.empty[String]
    backlogErrors union redmineErrors union backlogProjectsErrors union redmineProjectsErrors
  }

  private def validateLoadRedmineProjects(): Seq[String] =
    if (r2bConf.projects.isEmpty) Seq("- " + Messages("message.specify_projects"))
    else r2bConf.projects.flatMap(validateLoadRedmineProject)

  private def validateLoadRedmineProject(projectKey: ParamProjectKey): Seq[String] = {
    val redmineService: RedmineService = new RedmineService(r2bConf)
    val option = redmineService.getProject(projectKey)

    if(option.isEmpty) Seq("- " + Messages("message.can_not_load_project", projectKey.redmine))
    else Seq.empty[String]
  }

  private def validateLoadBacklogProjects(): Seq[String] =
    if (r2bConf.projects.isEmpty) Seq("- " + Messages("message.specify_projects"))
    else r2bConf.projects.flatMap(validateLoadBacklogProject)

  private def validateLoadBacklogProject(projectKey: ParamProjectKey): Seq[String] = {
    val backlogService: BacklogService = new BacklogService(BacklogConfig(r2bConf.backlogUrl, r2bConf.backlogKey))
    projectKey.backlog match {
      case Some(backlog) =>
        backlogService.getProject(backlog) match {
          case Right(_) => Seq.empty[String]
          case Left(_) => Seq("- " + Messages("message.project_not_exist", projectKey.backlog.get))
        }
      case None => Seq.empty[String]
    }
  }

  private def validateLoadBacklog(): Seq[String] =
    try {
      val backlogService: BacklogService = new BacklogService(BacklogConfig(r2bConf.backlogUrl, r2bConf.backlogKey))
      backlogService.getUsers
      Seq.empty[String]
    } catch {
      case unknown: BacklogAPIException if unknown.getStatusCode == 404 =>
        error(unknown)
        Seq("- " + Messages("message.transport_error_backlog", r2bConf.backlogUrl))
      case api: BacklogAPIException =>
        error(api)
        Seq("- " + Messages("message.disable_access_backlog"))
      case e: Throwable =>
        error(e)
        Seq("- " + Messages("message.disable_access_backlog"))
    }

  private def validateLoadRedmine(): Seq[String] =
    try {
      val redmineService: RedmineService = new RedmineService(r2bConf)
      redmineService.getUsers
      Seq.empty[String]
    } catch {
      case auth: RedmineAuthenticationException =>
        log.error(auth.getMessage, auth)
        Seq("- " + Messages("message.auth_error_redmine"))
      case transport: RedmineTransportException =>
        log.error(transport.getMessage, transport)
        Seq("- " + Messages("message.transport_error_redmine", r2bConf.redmineUrl))
      case e: Throwable =>
        log.error(e.getMessage, e)
        Seq("- " + Messages("message.disable_access_redmine"))
    }

}
