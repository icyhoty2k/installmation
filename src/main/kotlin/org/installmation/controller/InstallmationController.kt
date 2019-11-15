/*
 * Copyright 2019 Serge Merzliakov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.installmation.controller

import com.google.common.eventbus.Subscribe
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.control.*
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.Pane
import javafx.stage.Stage
import javafx.util.StringConverter
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.installmation.configuration.Configuration
import org.installmation.configuration.UserHistory
import org.installmation.io.ApplicationJsonWriter
import org.installmation.javafx.ComboUtils
import org.installmation.model.*
import org.installmation.model.binary.JDK
import org.installmation.model.binary.JDKFactory
import org.installmation.model.binary.OperatingSystem
import org.installmation.service.*
import org.installmation.ui.dialog.AboutDialog
import org.installmation.ui.dialog.BinaryArtefactDialog
import org.installmation.ui.dialog.SingleValueDialog
import java.io.File


class InstallmationController(private val configuration: Configuration,
                              private val userHistory: UserHistory,
                              private val workspace: Workspace,
                              private val projectService: ProjectService) {
   
   companion object {
      val log: Logger = LogManager.getLogger(InstallmationController::class.java)
   }

   @FXML private lateinit var applicationMenuBar: MenuBar
   @FXML private lateinit var dependenciesPane: AnchorPane
   @FXML private lateinit var locationPane: AnchorPane
   @FXML private lateinit var newProjectMenuItem: MenuItem
   @FXML private lateinit var openProjectMenuItem: MenuItem
   @FXML private lateinit var projectNameField: TextField
   @FXML private lateinit var applicationVersionField: TextField
   @FXML private lateinit var copyrightField: TextField
   @FXML private lateinit var jpackageComboBox: ComboBox<JDK>
   @FXML private lateinit var configureJPackageButton: Button
   @FXML private lateinit var javafxComboBox: ComboBox<NamedDirectory>
   @FXML private lateinit var configureJFXButton: Button
   @FXML private lateinit var mainJarField: TextField
   @FXML private lateinit var mainClassField: TextField
   @FXML private lateinit var classPathListView: ListView<File>
   @FXML private lateinit var modulePathListView: ListView<File>
   @FXML private lateinit var shutdownMenu: Menu

   private var dependenciesController = DependenciesController(configuration,
         userHistory,
         workspace,
         projectService)
   
   private var locationController = LocationController(configuration,
         userHistory,
         workspace,
         projectService)


   // model loaded from configuration
   private val jpackageLocations: ObservableList<JDK> = FXCollections.observableArrayList<JDK>()
   private val javafxLocations: ObservableList<NamedDirectory> = FXCollections.observableArrayList<NamedDirectory>()
   
   init {
      configuration.eventBus.register(this)
   }
   
   @FXML
   fun initialize() {
      if (OperatingSystem.os() == OperatingSystem.Type.OSX) {
         shutdownMenu.isDisable = true
         shutdownMenu.isVisible = false
         applicationMenuBar.useSystemMenuBarProperty().set(true)
      }
      initializeChildControllers()
      initializeConfiguredBinaries()
   }

   private fun initializeChildControllers() {
      // load file list UI and insert into it's pane in the application
      setupChildController("/dependenciesTab.fxml", dependenciesController, dependenciesPane)
      setupChildController("/locationTab.fxml", locationController, locationPane)
      
   }
   /**
    * JDKs and drop down lists
    */
   private fun initializeConfiguredBinaries() {
      jpackageLocations.addAll(configuration.jdkEntries.values)
      jpackageComboBox.items = jpackageLocations.sorted()

      javafxLocations.addAll(configuration.javafxModuleEntries.entries.map { NamedDirectory(it.key, it.value) })
      javafxComboBox.items = javafxLocations.sorted()
      javafxComboBox.converter = object : StringConverter<NamedDirectory>() {

         override fun toString(obj: NamedDirectory?): String? {
            return obj?.name
         }

         override fun fromString(name: String): NamedDirectory {
            return javafxComboBox.items.first { it.name == name }
         }
      }
   }
   
   @FXML
   fun shutdown() {
      // save configuration
      val reader = ApplicationJsonWriter<Configuration>(Configuration.configurationFile(), JsonParserFactory.configurationParser())
      reader.save(configuration)
      
      val stage = mainJarField.scene.window as Stage
      stage.close()
      log.info("Shutting down Installmation Application")
   }

   @FXML
   fun newProject() {
      // get a name first
      val sd = SingleValueDialog(applicationStage(), "Project Name", "Choose Project Name", "myProject")
      val result = sd.showAndWait()
      if (result.ok) {
         val project = projectService.newProject(result.data!!)
         log.debug("Created project ${project.name}")
         workspace.setCurrentProject(project)
         configuration.eventBus.post(ProjectCreatedEvent(project))
      }
   }

   @FXML
   fun openProject() {
      val p = projectService.loadProject("myProject")
      configuration.eventBus.post(ProjectLoadedEvent(p))
   }

   @FXML
   fun saveProject() {
      val current = workspace.currentProject
      if (current != null) {
         projectService.saveProject(current)
         configuration.eventBus.post(ProjectSavedEvent(current))
      }
   }

   @FXML
   fun configureJPackageBinaries() {
      val pairs = jpackageLocations.map { NamedDirectory(it.name, it.path) }
      val dialog = BinaryArtefactDialog(applicationStage(), "JPackager JDKs", pairs, userHistory)
      val result = dialog.showAndWait()
      if (result.ok) {
         ComboUtils.comboSelect(jpackageComboBox, result.data?.name)

         val updatedModel = dialog.updatedModel()
         if (updatedModel != null) {
            configuration.eventBus.post(JDKUpdatedEvent(updatedModel))
         }
      }
   }

   @FXML
   fun configureJavaFxModules() {
      val items = javafxLocations.map { NamedDirectory(it.name, it.path) }
      val dialog = BinaryArtefactDialog(applicationStage(), "JavaFX Module Directories", items, userHistory)
      val result = dialog.showAndWait()
      if (result.ok) {
         ComboUtils.comboSelect(javafxComboBox, result.data?.name)
         // update model
         val updatedModel = dialog.updatedModel()
         if (updatedModel != null) {
            configuration.eventBus.post(JFXModuleUpdatedEvent(updatedModel))
         }
      }
   }

   @FXML
   fun aboutDialog() {
      val dialog = AboutDialog(applicationStage())
      dialog.showAndWait()
   }

   /**
    * Generate in a single directory all the artefacts required for the install, but NOT
    * the final installer
    */
   @FXML
   fun generateImage() {

   }

   /*
     Will generate full installer file, creating an image as well
    */
   @FXML
   fun generateInstaller() {

   }

   /**
    * Generate scripts of creating images and installers for
    * Mac/Linux and Windows
    */
   @FXML
   fun generateScripts() {

   }

   @FXML
   fun showAllJDK() {
      val items = jpackageLocations.map { NamedDirectory(it.name, it.path) }
      val dialog = BinaryArtefactDialog(applicationStage(), "JPackager JDKs", items, userHistory)
      val result = dialog.showAndWait()
      if (result.ok) {
         // update jdk entries 
         val updatedModel = dialog.updatedModel()
         if (updatedModel != null) {
            configuration.eventBus.post(JDKUpdatedEvent(updatedModel))
         }
      }
   }

   @FXML
   fun showAllJavaFX() {

   }
   
   private fun applicationStage(): Stage {
      return mainJarField.scene.window as Stage
   }

   private fun setupChildController(fxmlPath: String, controller: Any, parent: Pane) {
      log.debug("Loading child controller from $fxmlPath")
      val loader = FXMLLoader(javaClass.getResource(fxmlPath))
      loader.setController(controller)
      val pane = loader.load<Pane>()
      AnchorPane.setTopAnchor(pane, 0.0)
      AnchorPane.setLeftAnchor(pane, 0.0)
      AnchorPane.setBottomAnchor(pane, 0.0)
      AnchorPane.setRightAnchor(pane, 0.0)
      parent.children.add(pane)
      log.debug("Child controller initialized successfully - $fxmlPath")
   }
   
   //-------------------------------------------------------
   //  Event Subscribers
   //-------------------------------------------------------

   @Subscribe
   fun handleProjectCreated(e: ProjectCreatedEvent) {
      projectNameField.text = e.project.name
   }

   @Subscribe
   fun handleProjectUpdated(e: ProjectUpdatedEvent) {
   }

   @Subscribe
   fun handleProjectDeleted(e: ProjectDeletedEvent) {
   }

   @Subscribe
   fun handleProjectSaved(e: ProjectSavedEvent) {
   }


   /**
    * Clear out and refresh list of JDKs from this controller's model
    * and configuration
    */
   @Subscribe
   fun handleJDKUpdated(e: JDKUpdatedEvent) {
      jpackageLocations.clear()
      configuration.jdkEntries.clear()
      e.updated.map {
         val jdk = JDKFactory.create(OperatingSystem.os(), it.name, it.path)
         jpackageLocations.add(jdk)
         configuration.jdkEntries[it.name] = jdk
      }
   }

   /**
    * Clear out and refresh list of FX modules from this controller's model
    * and configuration
    */
   @Subscribe
   fun handleJFXModuleUpdated(e: JFXModuleUpdatedEvent) {
      javafxLocations.clear()
      configuration.javafxModuleEntries.clear()
      e.updated.map {
         val nd = NamedDirectory(it.name, it.path)
         javafxLocations.add(nd)
         configuration.javafxModuleEntries[it.name] = it.path
      }
   }

}




