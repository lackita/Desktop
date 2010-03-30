/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2008 Joerg Mueller, Daniel Polansky, Christian Foltin, Dimitry Polivaev
 *
 *  This file author is Christian Foltin
 *  It is modified by Dimitry Polivaev in 2008.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.freeplane.plugin.script;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map.Entry;

import javax.swing.JMenu;

import org.apache.commons.lang.StringUtils;
import org.freeplane.core.controller.Controller;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.resources.ui.OptionPanelBuilder;
import org.freeplane.core.ui.IndexedTree;
import org.freeplane.core.ui.MenuBuilder;
import org.freeplane.core.util.TextUtil;
import org.freeplane.features.common.map.ModeController;
import org.freeplane.features.common.map.NodeModel;
import org.freeplane.features.mindmapmode.MModeController;
import org.freeplane.plugin.script.ExecuteScriptAction.ExecutionMode;
import org.freeplane.plugin.script.ScriptEditorPanel.IScriptModel;
import org.freeplane.plugin.script.ScriptEditorPanel.ScriptHolder;
import org.freeplane.plugin.script.ScriptEditorProperty.IScriptEditorStarter;
import org.freeplane.plugin.script.ScriptingConfiguration.ScriptMetaData;
import org.freeplane.plugin.script.ScriptingEngine.IErrorHandler;

class ScriptingRegistration {
	/** create scripts submenu if there are more scripts than this number. */
	private static final int MINIMAL_SCRIPT_COUNT_FOR_SUBMENU = 1;
	private static final String MENU_BAR_SCRIPTING_PARENT_LOCATION = "/menu_bar/extras/first";
	static final String MENU_BAR_SCRIPTING_LOCATION = MENU_BAR_SCRIPTING_PARENT_LOCATION + "/scripting";

	final private class PatternScriptModel implements IScriptModel {
		final private String mOriginalScript;
		private String mScript;

		public PatternScriptModel(final String pScript) {
			mScript = pScript;
			mOriginalScript = pScript;
		}

		public int addNewScript() {
			return 0;
		}

		public ScriptEditorWindowConfigurationStorage decorateDialog(final ScriptEditorPanel pPanel,
		                                                             final String pWindow_preference_storage_property) {
			final String marshalled = ResourceController.getResourceController().getProperty(
			    pWindow_preference_storage_property);
			return ScriptEditorWindowConfigurationStorage.decorateDialog(marshalled, pPanel);
		}

		public void endDialog(final boolean pIsCanceled) {
			if (pIsCanceled) {
				mScript = mOriginalScript;
			}
		}

		public boolean executeScript(final int pIndex, final PrintStream pOutStream, final IErrorHandler pErrorHandler) {
			ScriptingEngine.setNoUserPermissionRequired(true);
			return ScriptingEngine.executeScript(modeController.getMapController().getSelectedNode(), mScript,
			    modeController, pErrorHandler, pOutStream, getScriptCookies());
		}

		public int getAmountOfScripts() {
			return 1;
		}

		public String getScript() {
			return mScript;
		}

		public ScriptHolder getScript(final int pIndex) {
			return new ScriptHolder("Script", mScript);
		}

		public boolean isDirty() {
			return !StringUtils.equals(mScript, mOriginalScript);
		}

		public void setScript(final int pIndex, final ScriptHolder pScript) {
			mScript = pScript.getScript();
		}

		public void storeDialogPositions(final ScriptEditorPanel pPanel,
		                                 final ScriptEditorWindowConfigurationStorage pStorage,
		                                 final String pWindow_preference_storage_property) {
			pStorage.storeDialogPositions(pPanel, pWindow_preference_storage_property);
		}
	}

	private static final String SEPARATOR = "OptionPanel.separator.plugins/scripting/separatorPropertyName";
	private static final String OPTION_PANEL_SCRIPTING_TAB = "OptionPanel.plugins/scripting/tab_name";
	final private MModeController modeController;
	final private HashMap mScriptCookies = new HashMap();
	private IScriptEditorStarter mScriptEditorStarter;

	public ScriptingRegistration(final ModeController controller) {
		modeController = (MModeController) controller;
		register();
	}

	private void addPropertiesToOptionPanel() {
		final OptionPanelBuilder controls = modeController.getOptionPanelBuilder();
		controls.addTab(OPTION_PANEL_SCRIPTING_TAB);
		controls.addSeparator(OPTION_PANEL_SCRIPTING_TAB, SEPARATOR, IndexedTree.AS_CHILD);
		final String GROUP = OPTION_PANEL_SCRIPTING_TAB + "/" + SEPARATOR;
		controls.addBooleanProperty(GROUP, ScriptingEngine.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_ASKING,
		    IndexedTree.AS_CHILD);
		controls.addBooleanProperty(GROUP, ScriptingEngine.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_FILE_RESTRICTION,
		    IndexedTree.AS_CHILD);
		controls.addBooleanProperty(GROUP, ScriptingEngine.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_NETWORK_RESTRICTION,
		    IndexedTree.AS_CHILD);
		controls.addBooleanProperty(GROUP, ScriptingEngine.RESOURCES_EXECUTE_SCRIPTS_WITHOUT_EXEC_RESTRICTION,
		    IndexedTree.AS_CHILD);
		controls.addBooleanProperty(GROUP, ScriptingEngine.RESOURCES_SIGNED_SCRIPT_ARE_TRUSTED, IndexedTree.AS_CHILD);
		controls.addStringProperty(GROUP, ScriptingEngine.RESOURCES_SCRIPT_USER_KEY_NAME_FOR_SIGNING,
		    IndexedTree.AS_CHILD);
		controls.addStringProperty(GROUP, ScriptingEngine.RESOURCES_SCRIPT_DIRECTORIES, IndexedTree.AS_CHILD);
	}

	public HashMap getScriptCookies() {
		return mScriptCookies;
	}

	private void register() {
		final Controller controller = modeController.getController();
		mScriptEditorStarter = new ScriptEditorProperty.IScriptEditorStarter() {
			public String startEditor(final String pScriptInput) {
				final PatternScriptModel patternScriptModel = new PatternScriptModel(pScriptInput);
				final ScriptEditorPanel scriptEditorPanel = new ScriptEditorPanel(controller, patternScriptModel, false);
				scriptEditorPanel.setVisible(true);
				return patternScriptModel.getScript();
			}
		};
		modeController.addExtension(ScriptEditorProperty.IScriptEditorStarter.class, mScriptEditorStarter);
		addPropertiesToOptionPanel();
		final MenuBuilder menuBuilder = modeController.getUserInputListenerFactory().getMenuBuilder();
		menuBuilder.addAnnotatedAction(new ScriptEditor(controller, this));
		final ScriptingEngine scriptingEngine = new ScriptingEngine(this);
		menuBuilder.addAnnotatedAction(new ExecuteScriptForAllNodes(controller, scriptingEngine));
		menuBuilder.addAnnotatedAction(new ExecuteScriptForSelectionAction(controller, scriptingEngine));
		registerScripts(controller, menuBuilder, scriptingEngine);
	}

	private void registerScripts(final Controller controller, final MenuBuilder menuBuilder,
	                             final ScriptingEngine scriptingEngine) {
		ScriptingConfiguration configuration = new ScriptingConfiguration();
		String scriptsParentLocation = MENU_BAR_SCRIPTING_LOCATION;
		String scriptsLocation = scriptsParentLocation + "/scripts";
		addSubMenu(menuBuilder, scriptsParentLocation, scriptsLocation, TextUtil.getText("ExecuteScripts.text"));
		for (Entry<String, String> entry : configuration.getNameScriptMap().entrySet()) {
			String scriptName = entry.getKey();
			String location = scriptsLocation + "/" + scriptName;
			addSubMenu(menuBuilder, scriptsLocation, location, scriptName);
			final ScriptMetaData scriptMetaData = configuration.getNameScriptMetaDataMap().get(scriptName);
			// in the worst case three actions will cache a script - should not matter that much since it's unlikely
			// that one script is used in multiple modes by the same user
			for (ExecutionMode executionMode : scriptMetaData.getExecutionModes()) {
				addMenuItem(controller, menuBuilder, scriptingEngine, location, entry, executionMode, scriptMetaData
				    .cacheContent());
			}
		}
	}

	private void addSubMenu(final MenuBuilder menuBuilder, final String scriptsParentLocation,
	                        final String scriptsLocation, final String name) {
		final JMenu menuItem = new JMenu();
		MenuBuilder.setLabelAndMnemonic(menuItem, name);
		menuBuilder.addMenuItem(scriptsParentLocation, menuItem, scriptsLocation, MenuBuilder.AS_CHILD);
	}

	private void addMenuItem(final Controller controller, final MenuBuilder menuBuilder,
	                         final ScriptingEngine scriptingEngine, final String location,
	                         final Entry<String, String> entry, final ExecutionMode executionMode, boolean cacheContent) {
		final String scriptName = entry.getKey();
		final String key = ExecuteScriptAction.getExecutionModeKey(executionMode);
		final String menuName = TextUtil.format(key, new Object[] { scriptName });
		menuBuilder.addAction(location, new ExecuteScriptAction(controller, scriptingEngine, scriptName,
		    menuName, entry.getValue(), executionMode, cacheContent), MenuBuilder.AS_CHILD);
	}
}
