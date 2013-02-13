package org.freeplane.plugin.workspace;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.freeplane.core.extension.IExtension;
import org.freeplane.core.resources.IFreeplanePropertyListener;
import org.freeplane.core.resources.OptionPanelController;
import org.freeplane.core.resources.OptionPanelController.PropertyLoadListener;
import org.freeplane.core.resources.ResourceBundles;
import org.freeplane.core.resources.components.IPropertyControl;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.mindmapmode.MModeController;
import org.freeplane.plugin.workspace.io.FileSystemManager;
import org.freeplane.plugin.workspace.mindmapmode.MModeWorkspaceController;
import org.freeplane.plugin.workspace.model.WorkspaceModel;
import org.freeplane.plugin.workspace.model.project.AWorkspaceProject;

public final class WorkspaceController implements IExtension {
	
	public static final String WORKSPACE_RESOURCE_URL_PROTOCOL = "workspace";
	public static final String PROPERTY_RESOURCE_URL_PROTOCOL = "property";
	public static final String WORKSPACE_VERSION = "1.0";
		
	private static WorkspaceController self;
	private static Map<Class<? extends ModeController>, Class<? extends AWorkspaceModeExtension>> modeWorkspaceCtrlMap = new HashMap<Class<? extends ModeController>, Class<? extends AWorkspaceModeExtension>>();
	
	private WorkspaceController(Controller controller) {
		self = this;
	}

	public static void install(Controller controller) {
		if(self == null) {
			new WorkspaceController(controller);
			self.setupLanguage(controller);
		}
		controller.addExtension(WorkspaceController.class, self);
	}
	
	public static WorkspaceController getController() {
		return self;
	}
	
	public static void registerWorkspaceModeExtension(Class<? extends ModeController> modeController, Class<? extends AWorkspaceModeExtension> modeWorkspaceCtrl) throws ModeControlAlreadyRegisteredException {
		synchronized (modeWorkspaceCtrlMap) {
			if(modeWorkspaceCtrlMap.containsKey(modeController)) {
				throw new ModeControlAlreadyRegisteredException(modeController);
			}
			modeWorkspaceCtrlMap.put(modeController, modeWorkspaceCtrl);
		}
	}
	
	public static void removeWorkspaceModeExtension(Class<? extends ModeController> modeController) {
		synchronized (modeWorkspaceCtrlMap) {
			modeWorkspaceCtrlMap.remove(modeController);
		}
	}

	public boolean installMode(ModeController modeController) {
		AWorkspaceModeExtension modeCtrl = modeController.getExtension(AWorkspaceModeExtension.class);
		if(modeCtrl == null) {
			Class<? extends AWorkspaceModeExtension> clazz = modeWorkspaceCtrlMap.get(modeController.getClass());
			if(clazz == null) {
				return false;
			}
			try {
				modeCtrl = clazz.getConstructor(ModeController.class).newInstance(modeController);
				modeController.addExtension(AWorkspaceModeExtension.class, modeCtrl);
				return true;
			} catch (Exception e) {
				e.printStackTrace();
			}			
		}
		else {
			return true;
		}
		return false;
		
	}
	
	private void setupLanguage(Controller controller) {
		setLanguage();
		
		final OptionPanelController optionController = controller.getOptionPanelController();
		
		optionController.addPropertyLoadListener(new PropertyLoadListener() {			
			public void propertiesLoaded(Collection<IPropertyControl> properties) {
				setLanguage();
			}
		});
		
		controller.getResourceController().addPropertyChangeListener(new IFreeplanePropertyListener() {
			
			public void propertyChanged(String propertyName, String newValue, String oldValue) {
				if(propertyName.equalsIgnoreCase("language")){
					setLanguage();
				}
			}
		});
		try {
			WorkspaceController.registerWorkspaceModeExtension(MModeController.class, MModeWorkspaceController.class);
		} catch (ModeControlAlreadyRegisteredException e) {
			e.printStackTrace();
		}
		
	}
	
	private void setLanguage() {
		final String DEFAULT_LANGUAGE = "en";
		ResourceBundles resBundle = ((ResourceBundles)Controller.getCurrentController().getResourceController().getResources());
		String lang = resBundle.getLanguageCode();
		if (lang == null || lang.equals(ResourceBundles.LANGUAGE_AUTOMATIC)) {
			lang = DEFAULT_LANGUAGE;
		}
		
		URL res = this.getClass().getResource("/translations/Resources_"+lang+".properties");
		if (res == null) {
			lang = DEFAULT_LANGUAGE;
			res = this.getClass().getResource("/translations/Resources_"+lang+".properties");
		}
		
		if (res == null) {
			return;
		}
					
		resBundle.addResources(resBundle.getLanguageCode(), res);
	}
	
	public void startModeExtension(ModeController modeController) {
		AWorkspaceModeExtension modeCtrl = modeController.getExtension(AWorkspaceModeExtension.class);
		if(modeCtrl == null) {
			return;
		}
		modeCtrl.start(modeController);
		
	}
	
	public void shutdown() {
		for (String modeName : Controller.getCurrentController().getModes()) {
			ModeController modeController = Controller.getCurrentController().getModeController(modeName);
			AWorkspaceModeExtension modeExt = modeController.getExtension(AWorkspaceModeExtension.class);
			if(modeExt == null) {
				continue;
			}
			modeExt.shutdown();
		}
		
	}
	
	public static WorkspaceModel getCurrentModel() {
		return getCurrentModeExtension().getModel();
	}

	public static AWorkspaceModeExtension getCurrentModeExtension() {
		return Controller.getCurrentModeController().getExtension(AWorkspaceModeExtension.class);
	}
	
	public static FileSystemManager getFileSystemMgr() {
		return new FileSystemManager(getCurrentModeExtension().getFileTypeManager());
	}

	public static URI resolveURI(URI uri) {
		//TODO - get absolute uri
		return uri;
	}

	public static File resolveFile(URI path) {
		return new File(resolveURI(path));
	}

	public static void loadProject(AWorkspaceProject project) throws IOException {
		getCurrentModeExtension().getProjectLoader().loadProject(project);
	}

	public static URI getDefaultProjectHome() {
		return getCurrentModeExtension().getDefaultProjectHome();
	}

	public static URI getApplicationHome() {
		String appName = Controller.getCurrentController().getResourceController().getProperty("ApplicationName");
		String homePath = System.getProperty("user.home")+ File.separator + appName;
		return new File(homePath).toURI();
	}
	
	public static URI getApplicationSettingsHome() {
		String appName = Controller.getCurrentController().getResourceController().getProperty("ApplicationName");
		String homePath = System.getProperty("user.home")+ File.separator + "." + appName.toLowerCase(Locale.ENGLISH);
		return new File(homePath).toURI();
	}

	public static AWorkspaceProject getCurrentProject() {
		return getCurrentModeExtension().getCurrentProject();
	}
}
