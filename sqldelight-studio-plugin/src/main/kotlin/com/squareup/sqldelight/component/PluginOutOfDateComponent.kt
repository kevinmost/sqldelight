/*
 * Copyright (C) 2016 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.sqldelight.component

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiManager
import com.intellij.util.text.VersionComparatorUtil
import com.squareup.sqldelight.SqliteCompiler
import com.squareup.sqldelight.VERSION
import com.squareup.sqldelight.util.collectElements
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl
import javax.swing.event.HyperlinkEvent

/**
 * WARNING: This class is run from SqlDelightStartupActivity, not from plugin.xml
 */
class PluginOutOfDateComponent(myProject: Project) : AbstractProjectComponent(myProject) {
  override fun projectOpened() {
    var sqldelightPluginApplied = false
    var hasSqlDelightFiles = false

    ProjectRootManager.getInstance(myProject).fileIndex.iterateContent({ file ->
      if (file.extension == SqliteCompiler.FILE_EXTENSION) {
        hasSqlDelightFiles = true
      }
      if (file.name != "build.gradle") return@iterateContent true

      // Grab the 'com.squareup.sqldelight:gradle-plugin:xxx' element.
      val matches = PsiManager.getInstance(myProject).findFile(file)!!.collectElements({ element ->
        element is GrLiteralImpl && element.text.startsWith(GRADLE_PREFIX)
      }).filterIsInstance<GrLiteralImpl>()
      if (matches.size < 1) return@iterateContent true
      val gradleVersion = matches[0].text.trim('\'').substringAfterLast(':')
      sqldelightPluginApplied = true

      // Check for version mismatch and show a warning if the gradle version is outdated.
      if (gradleVersion == VERSION
          || VERSION.equals(PropertiesComponent.getInstance(myProject).getValue(SUPPRESSED))
          || VersionComparatorUtil.compare(VERSION, gradleVersion) <= 0) {
        return@iterateContent true
      }

      val message = "<p>Your version of SQLDelight gradle plugin is $gradleVersion, while IDE" +
          " version is $VERSION. Gradle plugin should be updated to avoid compatibility problems." +
          "</p><p><a href=\"update\">Update Gradle</a> <a href=\"ignore\">Ignore</a></p>";

      Notifications.Bus.notify(Notification("Outdated SQLDelight Gradle Plugin",
          "Outdated SQLDelight Gradle Plugin", message,
          NotificationType.WARNING, NotificationListener { notification, event ->
        if (event.eventType != HyperlinkEvent.EventType.ACTIVATED) return@NotificationListener
        when (event.description) {
          "update" -> updateGradlePlugin(matches[0])
          "ignore" -> PropertiesComponent.getInstance(myProject).setValue(SUPPRESSED, VERSION);
          else -> throw IllegalStateException("Unknown event description ${event.description}");
        }
        notification.expire();
      }), myProject);
      true
    })

    if (sqldelightPluginApplied || !hasSqlDelightFiles) return

    // There was no sqldelight plugin applied, throw an error.
    val message = "<p>The SQLDelight gradle plugin must be applied in order for the project to " +
        "compile</p>"
    Notifications.Bus.notify(Notification("SQLDelight Gradle Plugin Not Found",
        "SQLDelight Gradle Plugin Not Found", message, NotificationType.ERROR))
  }

  private fun updateGradlePlugin(element: GrLiteralImpl) {
    WriteCommandAction.runWriteCommandAction(myProject, {
      element.updateText(CURRENT_GRADLE)
    })
  }

  companion object {
    private val GRADLE_PREFIX = "'com.squareup.sqldelight:gradle-plugin:"
    private val CURRENT_GRADLE = "$GRADLE_PREFIX$VERSION'"
    private val SUPPRESSED = "sqldelight.outdate.runtime.suppressed.version";
  }
}