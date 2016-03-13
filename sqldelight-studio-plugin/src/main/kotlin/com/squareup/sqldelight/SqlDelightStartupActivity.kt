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
package com.squareup.sqldelight

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.squareup.sqldelight.component.PluginOutOfDateComponent
import com.squareup.sqldelight.lang.SqlDelightFileViewProvider
import com.squareup.sqldelight.lang.SqliteContentIterator
import com.squareup.sqldelight.lang.SqliteFile
import com.squareup.sqldelight.types.SymbolTable

class SqlDelightStartupActivity : StartupActivity {
  override fun runActivity(project: Project) {
    var files = arrayListOf<SqliteFile>()
    VirtualFileManager.getInstance().addVirtualFileListener(SqlDelightVirtualFileListener())
    ProjectRootManager.getInstance(project).fileIndex
        .iterateContent(SqliteContentIterator(PsiManager.getInstance(project)) { file ->
          files.add(file)
          true
        })
    files.forEach { file ->
      file.parseThen({ parsed ->
        SqlDelightFileViewProvider.symbolTable += SymbolTable(parsed, file.virtualFile)
      })
    }
    files.forEach { file ->
      ApplicationManager.getApplication().executeOnPooledThread {
        WriteCommandAction.runWriteCommandAction(project, {
          (file.viewProvider as SqlDelightFileViewProvider).generateJavaInterface()
        })
      }
    }

    // Technically this should be defined in plugin.xml as a plugin component, but since
    // it performs PSI logic, it needs to be ran after startup. Running the main body
    // here eliminates race conditions with this class.
    PluginOutOfDateComponent(project).projectOpened()
  }
}