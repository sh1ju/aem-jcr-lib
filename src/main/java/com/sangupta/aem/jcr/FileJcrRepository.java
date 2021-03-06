package com.sangupta.aem.jcr;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sangupta.aem.Utils;
import com.sangupta.aem.vault.VaultHelper;
import com.sangupta.aem.xml.XmlHelper;
import com.sangupta.jerry.util.AssertUtils;

public class FileJcrRepository implements JcrRepository {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(FileJcrRepository.class);

	/**
	 * The root folder where the JCR repository will be created
	 */
	private final File rootFolder;
	
	/**
	 * The jcr_root folder inside the repo folder
	 */
	private final File jcrRoot;
	
	/**
	 * The META-INF folder inside the repo folder
	 */
	private final File metaRoot;

	/**
	 * The name of the repository to initialize with
	 */
	private final String repoName;
	
	/**
	 * The username who is creating the repository
	 */
	private final String userName;
	
	/**
	 * The group name of the package
	 */
	private final String groupName;
	
	/**
	 * Construct the repository at the given path
	 * 
	 * @param repoName
	 * @param userName
	 * @param groupName
	 * @param rootFolder
	 */
	public FileJcrRepository(String repoName, String userName, String groupName, String rootFolder) {
		this(repoName, userName, groupName, new File(rootFolder));
	}
	
	/**
	 * Construct the repository at the given folder
	 * 
	 * @param repoName
	 * @param userName
	 * @param groupName
	 * @param rootFolder
	 */
	public FileJcrRepository(String repoName, String userName, String groupName, File rootFolder) {
		this.repoName = repoName;
		this.userName = userName;
		this.groupName = groupName;
		
		this.rootFolder = rootFolder;
		
		this.jcrRoot = new File(this.rootFolder, "jcr_root");
		this.metaRoot = new File(this.rootFolder, "META-INF");
		
		if(!this.jcrRoot.exists()) {
			this.jcrRoot.mkdirs();
		}
		
		if(!this.metaRoot.exists()) {
			this.metaRoot.mkdirs();
		}
	}
	
	/**
	 * Create the basic structure of a JCR repo and the required folders
	 * and files needed.
	 * 
	 * @return
	 * @throws IOException 
	 */
	public boolean initialize() throws IOException {
		// initialize the root node
		JcrNode node = this.getNode(null);
		node.initialize();
		
		// initialize the vault folder
		this.initializeVault();
		return true;
	}

	/**
	 * Check if a repository already exists at this point or not.
	 * 
	 * @return
	 */
	public boolean exists() {
		return this.rootFolder.exists();
	}
	
	/* (non-Javadoc)
	 * @see com.sangupta.aem.jcr.JcrRepository#getNode(java.lang.String)
	 */
	@Override
	public JcrNode getNode(String path) {
		if(path == null || path.isEmpty()) {
			path = "/";
		}
		
		path = path.trim();
		
		if("/".equals(path)) {
			// this is a root node request
			return new JcrNode(this.jcrRoot, "/", true, this);
		}
		
		File nodePath = new File(this.jcrRoot, path);
		nodePath.mkdirs();
		return new JcrNode(nodePath, path, "/".equals(path), this);
	}
	
	/* (non-Javadoc)
	 * @see com.sangupta.aem.jcr.JcrRepository#createComponent(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public JcrNode createComponent(String path, String title, String parentComponent, String componentGroup) {
		JcrNode node = getNode(path);
		
		Map<String, String> map = new HashMap<>();
		map.put("jcr:primaryType", "cq:Component");
		map.put("jcr:title", title);
		map.put("sling:resourceSuperType", parentComponent);
		map.put("componentGroup", componentGroup);
		
		try {
			node.initialize(map);
		} catch(IOException e) {
			LOGGER.error("Unable to initialize the node", e);
			return null;
		}
		
		return node;
	}
	
	@Override
	public JcrNode createPage(String path, String title, String template, String slingResourceType, Map<String, String> properties) {
		JcrNode node = getNode(path);
		
		properties.put("cq:lastModified", "{Date}2016-08-26T07:33:03.067Z");
		properties.put("cq:lastModifiedBy", this.userName);
		properties.put("cq:template", template);
		properties.put("jcr:primaryType", "cq:PageContent");
		properties.put("jcr:title", title);
		properties.put("sling:resourceType", slingResourceType);

		String contents = XmlHelper.createTag("jcr:content", properties);
		
		try {
			node.addProperty("jcr:primaryType", "cq:Page");
			node.initialize(null, contents);
		} catch(IOException e) {
			LOGGER.error("Unable to initialize the node", e);
			return null;
		}
		
		return node;
	}
	
	/* (non-Javadoc)
	 * @see com.sangupta.aem.jcr.JcrRepository#createTemplate(java.lang.String, java.lang.String, long, java.lang.String)
	 */
	@Override
	public JcrNode createTemplate(String path, String title, long ranking, String baseComponent) {
		Map<String, Object> map = new HashMap<>();
		map.put("title", title);
		map.put("ranking", ranking);
		map.put("baseComponent", baseComponent);
		String xml = Utils.mergeTemplate("create-template.xml", map);
		
		JcrNode node = getNode(path);
		
		try {
			node.initializeWithContents(xml);
		} catch(IOException e) {
			LOGGER.error("Unable to initialize the node", e);
			return null;
		}
		
		return node;
	}
	
	/* (non-Javadoc)
	 * @see com.sangupta.aem.jcr.JcrRepository#getRootFolder()
	 */
	@Override
	public File getRootFolder() {
		return this.rootFolder;
	}
	
	/**
	 * Initialize the meta-inf/vault folder
	 * @throws IOException 
	 */
	private void initializeVault() throws IOException {
		File vault = new File(this.metaRoot, "vault");
		if(vault.exists()) {
			return;
		}
		
		vault.mkdirs();
		
		// copy files into the vault directory
		FileUtils.writeStringToFile(new File(vault, "config.xml"), Utils.getDiskResource("vault/config.xml"));
		FileUtils.writeStringToFile(new File(vault, "nodetypes.cnd"), Utils.getDiskResource("vault/nodetypes.cnd"));
		
		Map<String, Object> properties = new HashMap<>();
		properties.put("name", this.repoName);
		properties.put("user", this.userName);
		properties.put("group", this.groupName);
		FileUtils.writeStringToFile(new File(vault, "properties.xml"), Utils.mergeTemplate("vault/properties.xml", properties));
		
		
		// write filter.xml file
		writeFilterXML(vault, VaultHelper.createVaultFilterXml(new String[] { "/apps/sangupta" }));
		
		// definition xml
		File definition = new File(vault, "definition");
		definition.mkdirs();
		FileUtils.writeStringToFile(new File(definition, ".content.xml"), Utils.getDiskResource("vault/definition/.content.xml"));
	}

	/* (non-Javadoc)
	 * @see com.sangupta.aem.jcr.JcrRepository#updateFilters()
	 */
	@Override
	public void updateFilters() throws IOException {
		// read a list of all files within the repository.
		Collection<File> files = FileUtils.listFiles(this.jcrRoot, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
		if(AssertUtils.isEmpty(files)) {
			writeFilterXML(null, VaultHelper.createVaultFilterXml(null));
			return;
		}
		
		final String rootPath = this.jcrRoot.getAbsoluteFile().getAbsolutePath();
		
		Set<String> list = new HashSet<>();
		for(File file : files) {
			if(file.isDirectory()) {
				continue;
			}
			
			String path = file.getAbsoluteFile().getAbsolutePath();
			path = path.substring(rootPath.length());
			path = StringUtils.replaceChars(path, '\\', '/');
			
			if(AssertUtils.isEmpty(path)) {
				continue;
			}
			
			// check if file is inside META-INF
			if(path.equals("/.content.xml")) {
				// ignore
				continue;
			}
			
			// these files are the ones that have been added/modified
			// now create the path structures from them
			int index = path.lastIndexOf('/');
			path = path.substring(0, index);
			
			list.add(path);
		}
		
		// no file has been written in repository
		if(AssertUtils.isNotEmpty(list)) {
			writeFilterXML(null, VaultHelper.createVaultFilterXml(list.toArray(new String[] { })));
			return;
		}
	}
	
	/**
	 * Write the filter.xml file within the vault meta directory.
	 * 
	 * @param contents
	 * @throws IOException 
	 */
	private void writeFilterXML(File vault, String contents) throws IOException {
		if(vault == null) {
			vault = new File(this.metaRoot, "vault");
			vault.mkdirs();
		}
		
		File filterXml = new File(vault, "filter.xml");
		FileUtils.writeStringToFile(filterXml, contents);
	}
	
	/* (non-Javadoc)
	 * @see com.sangupta.aem.jcr.JcrRepository#createDAMAsset(java.lang.String, java.lang.String, byte[])
	 */
	@Override
	public void createDAMAsset(String path, String fileName, byte[] bytes) throws IOException {
		if(!path.startsWith("/")) {
			path = "/" + path;
		}
		
		JcrNode node = this.getNode("/content/dam" + path + "/" + fileName);
		JcrNode renditions = node.getChildNode("_jcr_content/renditions");
		JcrNode originalDir = renditions.getChildNode("original.dir");
		
		// save the original file
		renditions.saveFile("original", bytes);
		
		// create the renditions
		createRenditionsForDAMAsset(renditions, fileName, bytes);
		
		// original.dir .content.xml
		Map<String, Object> context = new HashMap<>();
		context.put("user", this.userName);
		context.put("imageType", getImageMimeType(fileName));
		String xml = Utils.mergeTemplate("dam/original.dir.content.xml", context);
		originalDir.saveFile(".content.xml", xml);
		
		// create content.xml in node itself
		context = new HashMap<>();
		xml = Utils.mergeTemplate("dam/asset.content.xml", context);
		node.saveFile(".content.xml", xml);
	}

	private String getImageMimeType(String fileName) {
		// TODO: fix this
		return "image/jpeg";
	}

	/**
	 * Generate the required renditions for the image.
	 * 
	 * @param renditions
	 * @param fileName
	 * @param bytes
	 */
	private void createRenditionsForDAMAsset(JcrNode renditions, String fileName, byte[] bytes) {
		// TODO Auto-generated method stub
		
	}
	
}
