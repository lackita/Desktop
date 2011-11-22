package org.docear.plugin.bibtex;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map.Entry;

import javax.ws.rs.core.UriBuilder;

import net.sf.jabref.BibtexEntry;
import net.sf.jabref.Globals;
import net.sf.jabref.labelPattern.LabelPatternUtil;

import org.docear.plugin.core.CoreConfiguration;
import org.docear.plugin.pdfutilities.util.NodeUtils;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.attribute.Attribute;
import org.freeplane.features.attribute.AttributeController;
import org.freeplane.features.attribute.NodeAttributeTableModel;
import org.freeplane.features.link.LinkController;
import org.freeplane.features.link.NodeLinks;
import org.freeplane.features.link.mindmapmode.MLinkController;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.mode.Controller;
import org.freeplane.plugin.workspace.WorkspaceUtils;

public class JabRefAttributes {

	private HashMap<String, String> valueAttributes = new HashMap<String, String>();
	private String keyAttribute;

	public JabRefAttributes() {		
		registerAttributes();
	}
	
	public void registerAttributes() {
		this.keyAttribute = TextUtils.getText("bibtex_key");
		
		this.valueAttributes.put(TextUtils.getText("jabref_author"), "author");
		this.valueAttributes.put(TextUtils.getText("jabref_title"), "title");
		this.valueAttributes.put(TextUtils.getText("jabref_year"), "year");
		this.valueAttributes.put(TextUtils.getText("jabref_journal"), "journal");
	}
	
	public String getKeyAttribute() {
		return keyAttribute;
	}
	
	public HashMap<String, String> getValueAttributes() {
		return valueAttributes;
	}
	
	public String getBibtexKey(NodeModel node) {		
		return getAttributeValue(node, this.keyAttribute);
	}
	
	public String getAttributeValue(NodeModel node, String attributeName) {
		NodeAttributeTableModel attributeTable = (NodeAttributeTableModel) node.getExtension(NodeAttributeTableModel.class);
		if (attributeTable == null) {
			return null;
		}
		
		for (Attribute attribute : attributeTable.getAttributes()) {
			if (attribute.getName().equals(attributeName)) {
				return (String) attribute.getValue();
			}
		}
		
		return null;
	}
	
	public boolean isReferencing(BibtexEntry entry, NodeModel node) {
		String nodeKey = getBibtexKey(node);
		String entryKey = entry.getCiteKey();
		if (nodeKey != null && entryKey != null && nodeKey.equals(entryKey)) {
			return true;
		}				
		return false;
	}
	
	public void setReferenceToNode(BibtexEntry entry) {
		NodeModel target = Controller.getCurrentModeController().getMapController().getSelectedNode();
		setReferenceToNode(entry, target);
	}
	
	public void removeReferenceFromNode(BibtexEntry entry, NodeModel target) {
		NodeAttributeTableModel attributeTable = AttributeController.getController().createAttributeTableModel(target);
		
		if (attributeTable == null) {
			return;
		}
		
		for (String attributeKey : attributeTable.getAttributeKeyList()) {
			if (this.valueAttributes.containsKey(attributeKey) || this.keyAttribute.equals(attributeKey)) {				
				AttributeController.getController().performRemoveRow(attributeTable, attributeTable.getAttributePosition(attributeKey));
			}
		}
	}
	
	public boolean updateReferenceToNode(BibtexEntry entry, NodeModel node) {
		boolean changes = false;
		
		NodeAttributeTableModel attributeTable = (NodeAttributeTableModel) node.getExtension(NodeAttributeTableModel.class);
		if (attributeTable == null) {
			return false;
		}
		
		for (Entry<String, String> valueAttribute : this.valueAttributes.entrySet()) {
			String nodeAttributeName = valueAttribute.getKey();
			String jabrefAttributeName = valueAttribute.getValue();
			
			String nodeValue = getAttributeValue(node, nodeAttributeName);
			String jabrefValue = entry.getField(jabrefAttributeName);
			
			if (nodeValue == null) {
				if (jabrefValue != null) {
					AttributeController.getController().performInsertRow(attributeTable, 0, nodeAttributeName, jabrefValue);
					changes = true;
				}
			}
			else {
				if (jabrefValue == null) {
					AttributeController.getController().performRemoveRow(attributeTable, attributeTable.getAttributePosition(nodeAttributeName));
					changes = true;
				}
				else {
					if (!nodeValue.equals(jabrefValue)) {
						AttributeController.getController().performReplaceAttributeValue(nodeAttributeName, nodeValue, jabrefValue);						
						changes = true;
					}
				}
			}
		}
				
		return changes;
	}
	
	public void setReferenceToNode(BibtexEntry entry, NodeModel node) {
		if (entry.getCiteKey()==null) {
			LabelPatternUtil.makeLabel(Globals.prefs.getKeyPattern(), ReferencesController.getController().getJabrefWrapper().getDatabase(), entry);						
		}		
		
		removeReferenceFromNode(entry, node);
		
		for (Entry<String, String> e : this.valueAttributes.entrySet()) {
			NodeUtils.setAttributeValue(node, e.getKey(), entry.getField(e.getValue()), false);
		}

		NodeUtils.setAttributeValue(node, keyAttribute, entry.getCiteKey(), false);
		
		NodeLinks nodeLinks = NodeLinks.getLinkExtension(node);
		if (nodeLinks != null) {
			System.out.println("debug remove hyperlink");
			nodeLinks.setHyperLink(null);
		}

		String files = entry.getField("file");
		System.out.println("debug path: "+files);
		
		
		if (files != null && files.length() > 0) {			
			String[] paths = files.split("(?<!\\\\);"); // taken from splmm, could not test it
            for(String path : paths){
            	URI uri = parsePath(entry, path);
            	if(uri != null){
            		NodeUtils.setLinkFrom(uri, node);
            		break;
            	}
            }		
		}
		else {
			String url = entry.getField("url");			
			if (url != null && url.length() > 0) {
				URI link;			
				try {
					link = LinkController.createURI(url.trim());
					final MLinkController linkController = (MLinkController) MLinkController.getController();
					linkController.setLink(node, link, LinkController.LINK_ABSOLUTE);
				}
				catch (URISyntaxException e) {				
					e.printStackTrace();
				}
			}
		}

	}


	private URI parsePath(BibtexEntry entry, String path) {		
		path = extractPath(path);
		if(path == null){
			LogUtils.warn("Could not extract path from: "+ entry.getCiteKey());
			return null; 
		}		
		path = removeEscapingCharacter(path);
		if(isAbsolutePath(path)){
			if(new File(path).exists()){
				return new File(path).toURI();
			}
		}
		else{
			URI uri = CoreConfiguration.referencePathObserver.getUri();
			URI absUri = WorkspaceUtils.absoluteURI(uri);
			
			System.out.println("debug parsePath: "+UriBuilder.fromPath(path).build());
			URI pdfUri = absUri.resolve(UriBuilder.fromPath(path).build());
			if(new File(pdfUri.normalize()) != null && new File(pdfUri.normalize()).exists()){
				return pdfUri;
			}
		}		
		return null;
	}
	
	private static boolean isAbsolutePath(String path) {
		return path.matches("^/.*") || path.matches("^[a-zA-Z]:.*");		
	}

	private static String removeEscapingCharacter(String string) {
		return string.replaceAll("([^\\\\]{1,1})[\\\\]{1}", "$1");	
	}

	private static String extractPath(String path) {
		String[] array = path.split("(^:|(?<=[^\\\\]):)"); // splits the string at non escaped double points
		if(array.length >= 3){
			return array[1];
		}
		return null;
	}

}