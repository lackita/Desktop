/**
 * author: Marcel Genzmehr
 * 18.08.2011
 */
package org.docear.plugin.core.workspace.node;

import java.io.File;
import java.net.URI;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.docear.plugin.core.CoreConfiguration;
import org.docear.plugin.core.workspace.node.config.NodeAttributeObserver;
import org.freeplane.core.util.LogUtils;
import org.freeplane.plugin.workspace.WorkspaceUtils;
import org.freeplane.plugin.workspace.config.node.PhysicalFolderNode;
import org.freeplane.plugin.workspace.model.node.AWorkspaceTreeNode;

/**
 * 
 */
public class FolderTypeLiteratureRepositoryNode extends PhysicalFolderNode implements ChangeListener /* FolderNode */{

	private static final long serialVersionUID = 1L;
	private boolean locked;

	/***********************************************************************************
	 * CONSTRUCTORS
	 **********************************************************************************/

	public FolderTypeLiteratureRepositoryNode(String type) {
		super(type);		
		CoreConfiguration.repositoryPathObserver.addChangeListener(this);
	}

	/***********************************************************************************
	 * METHODS
	 **********************************************************************************/

	public AWorkspaceTreeNode clone() {
		FolderTypeLiteratureRepositoryNode node = new FolderTypeLiteratureRepositoryNode(getType());
		return clone(node);
	}
	
	public void disassociateReferences()  {
		CoreConfiguration.repositoryPathObserver.removeChangeListener(this);
	}
	
	public void setPath(URI uri) {
		super.setPath(uri);
		locked = true;		
		CoreConfiguration.repositoryPathObserver.setUri(uri);
		if (uri != null) {
			createPathIfNeeded(uri);
		}
		locked = false;	
	}
	
	private void createPathIfNeeded(URI uri) {
		File file = WorkspaceUtils.resolveURI(uri);

		if (file != null) {
			if (!file.exists()) {
				if (file.mkdirs()) {
					LogUtils.info("New Literature Folder Created: " + file.getAbsolutePath());
				}
			}
			this.setName(file.getName());
		}
		else {
			this.setName("no folder selected!");
		}

		
	}

	/***********************************************************************************
	 * REQUIRED METHODS FOR INTERFACES
	 **********************************************************************************/
	//TODO: replace by new method
	public void propertyChanged(String propertyName, final String newValue, String oldValue) {
		
	}

	public void stateChanged(ChangeEvent e) {		
		if(!locked && e.getSource() instanceof NodeAttributeObserver) {			
			URI uri = ((NodeAttributeObserver) e.getSource()).getUri();
			try{		
				createPathIfNeeded(uri);				
			}
			catch (Exception ex) {
				return;
			}
			this.setPath(uri);
			this.refresh();
		}
	}
}